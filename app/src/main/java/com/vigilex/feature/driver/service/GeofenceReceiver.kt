package com.vigilex.feature.driver.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.auth.FirebaseAuth
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.EventType
import com.vigilex.core.model.ImpairmentEvent
import com.vigilex.core.model.ImpairmentSubtype
import com.vigilex.core.model.Severity
import com.vigilex.core.model.TripStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Triggered by GeofencingClient when the driver enters the 200m arrival radius.
 * Marks trip complete, logs a TRIP_COMPLETE event, and stops the monitoring service.
 * No OTP required — geofence arrival is authoritative.
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var firestore: FirestoreDataSource
    @Inject lateinit var auth: FirebaseAuth

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val tripId = event.triggeringGeofences?.firstOrNull()?.requestId ?: return
        val driverId = auth.currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                firestore.updateTripStatus(tripId, TripStatus.COMPLETE)
                firestore.writeEvent(
                    ImpairmentEvent(
                        type = EventType.TRIP_COMPLETE,
                        subtype = ImpairmentSubtype.COMBINED,
                        driverId = driverId,
                        tripId = tripId,
                        timestamp = System.currentTimeMillis(),
                        severity = Severity.LOW
                    )
                )
            }
            // Stop the foreground service
            context.stopService(Intent(context, MonitoringForegroundService::class.java))
        }
    }
}
