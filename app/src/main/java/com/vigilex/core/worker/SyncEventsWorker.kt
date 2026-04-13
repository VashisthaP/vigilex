package com.vigilex.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vigilex.core.data.local.PendingEventDao
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.EventType
import com.vigilex.core.model.ImpairmentEvent
import com.vigilex.core.model.ImpairmentSubtype
import com.vigilex.core.model.Severity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager job that drains the local offline queue to Firestore.
 * Runs only when internet is available (constraint set in VigileXApplication).
 * Retries automatically on failure (WorkManager default exponential backoff).
 */
@HiltWorker
class SyncEventsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: PendingEventDao,
    private val remote: FirestoreDataSource
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            dao.purgeStale()
            val pending = dao.getPending()
            pending.forEach { entity ->
                runCatching {
                    val event = ImpairmentEvent(
                        type = EventType.values().firstOrNull { it.toFirestoreValue() == entity.type }
                            ?: EventType.IMPAIRMENT,
                        subtype = ImpairmentSubtype.values().firstOrNull { it.toFirestoreValue() == entity.subtype }
                            ?: ImpairmentSubtype.COMBINED,
                        driverId = entity.driverId,
                        tripId = entity.tripId,
                        companyId = entity.companyId,
                        timestamp = entity.timestamp,
                        lat = entity.lat,
                        lng = entity.lng,
                        severity = Severity.from(entity.severity)
                    )
                    remote.writeEvent(event)
                    dao.delete(entity.localId)
                }.onFailure {
                    dao.incrementRetry(entity.localId)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
