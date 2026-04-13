package com.vigilex.feature.driver.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vigilex.core.model.TripStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Restarts the monitoring foreground service after device reboot
 * if a driver had an active trip at the time of the reboot.
 *
 * Only fires if a user is logged in (FirebaseAuth session persists across reboots).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Check Firestore for an active trip on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db = FirebaseFirestore.getInstance()
                val snap = db.collection("trips")
                    .whereEqualTo("driverId", uid)
                    .whereEqualTo("status", TripStatus.ACTIVE.toFirestoreValue())
                    .limit(1)
                    .get()
                    .await()

                val trip = snap.documents.firstOrNull() ?: return@runCatching
                val tripId = trip.id
                val companyId = trip.getString("companyId") ?: ""

                val serviceIntent = Intent(context, MonitoringForegroundService::class.java).apply {
                    putExtra(MonitoringForegroundService.EXTRA_TRIP_ID, tripId)
                    putExtra(MonitoringForegroundService.EXTRA_COMPANY_ID, companyId)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
