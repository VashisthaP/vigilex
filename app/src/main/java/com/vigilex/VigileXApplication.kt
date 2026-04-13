package com.vigilex

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.vigilex.BuildConfig
import com.vigilex.core.worker.SyncEventsWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class VigileXApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
        createNotificationChannels()
        scheduleSyncWorker()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING,
                getString(R.string.notification_channel_monitoring),
                NotificationManager.IMPORTANCE_LOW // Low so it doesn't make sound on every GPS tick
            ).apply {
                description = "Shows while VigileX is actively monitoring the driver"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT,
                "VigileX Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts for drowsiness or impairment events"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(monitoringChannel, alertChannel))
        }
    }

    /** Sync offline queue every 15 minutes when connected to internet */
    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncEventsWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val CHANNEL_MONITORING = "vigilex_monitoring"
        const val CHANNEL_ALERT = "vigilex_alert"
        const val SYNC_WORK_NAME = "sync_events_worker"
    }
}
