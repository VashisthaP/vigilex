package com.vigilex.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local offline queue for impairment events that couldn't reach Firestore.
 * SyncEventsWorker drains this table when network is restored.
 */
@Entity(tableName = "pending_events")
data class PendingEventEntity(
    @PrimaryKey val localId: String,          // UUID assigned at event creation
    val type: String,                          // EventType.toFirestoreValue()
    val subtype: String,                       // ImpairmentSubtype.toFirestoreValue()
    val driverId: String,
    val tripId: String,
    val companyId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val severity: String,
    val retryCount: Int = 0                    // incremented on each failed sync attempt
)
