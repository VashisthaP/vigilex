package com.vigilex.feature.driver.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.IBinder
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.vigilex.MainActivity
import com.vigilex.R
import com.vigilex.VigileXApplication
import com.vigilex.core.data.local.PendingEventDao
import com.vigilex.core.data.local.PendingEventEntity
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.EventType
import com.vigilex.core.model.ImpairmentEvent
import com.vigilex.core.model.ImpairmentSubtype
import com.vigilex.core.model.LocationPoint
import com.vigilex.core.model.Severity
import com.vigilex.core.model.TripStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Foreground service that runs the entire monitoring pipeline.
 *
 * Lifecycle: started by DriverViewModel on trip start, stopped on trip end or OTP exit.
 * Survives screen-off via FOREGROUND_SERVICE_CAMERA + FOREGROUND_SERVICE_LOCATION manifest types.
 *
 * Screen-off workaround (OEM camera restrictions on Android 14+):
 * DriverHomeScreen sets WindowManager.LayoutParams.screenBrightness = 0.01f and
 * FLAG_KEEP_SCREEN_ON so the screen is technically "on" at near-zero brightness,
 * allowing CameraX to keep running.
 */
@AndroidEntryPoint
class MonitoringForegroundService : LifecycleService() {

    @Inject lateinit var fusedLocation: FusedLocationProviderClient
    @Inject lateinit var firestore: FirestoreDataSource
    @Inject lateinit var pendingEventDao: PendingEventDao
    @Inject lateinit var auth: FirebaseAuth

    private lateinit var alertOrchestrator: AlertOrchestrator
    private lateinit var drowsinessAnalyzer: DrowsinessAnalyzer

    private var currentTripId: String = ""
    private var currentCompanyId: String = ""
    private var lastLat: Double = 0.0
    private var lastLng: Double = 0.0
    private var consecutiveAlertCount: Int = 0
    private var firstAlertWindowStartMs: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            lastLat = loc.latitude
            lastLng = loc.longitude
            drowsinessAnalyzer.setCurrentSpeed(loc.speed)

            // Update Firestore with latest position (30s interval defined in location request)
            if (currentTripId.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        firestore.updateTripLocation(
                            currentTripId,
                            LocationPoint(lat = loc.latitude, lng = loc.longitude, speed = loc.speed, updatedAt = System.currentTimeMillis())
                        )
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        alertOrchestrator = AlertOrchestrator(this)

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        drowsinessAnalyzer = DrowsinessAnalyzer(
            sensorManager = sensorManager,
            onImpairmentDetected = { subtype -> handleImpairment(subtype) },
            onCalibrationComplete = { /* threshold logged; no action needed */ }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        currentTripId = intent?.getStringExtra(EXTRA_TRIP_ID) ?: ""
        currentCompanyId = intent?.getStringExtra(EXTRA_COMPANY_ID) ?: ""

        startForegroundWithNotification()
        startLocationUpdates()
        startCameraAnalysis()

        return START_STICKY  // restart service if killed by OS
    }

    private fun startForegroundWithNotification() {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, VigileXApplication.CHANNEL_MONITORING)
            .setContentTitle(getString(R.string.notification_monitoring_title))
            .setContentText(getString(R.string.notification_monitoring_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .build()

        fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun startCameraAnalysis() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return

        // Use the CameraX suspend API (awaitInstance) instead of the ListenableFuture-based
        // callback to keep the code clean.
        lifecycleScope.launch(Dispatchers.Main) {
            runCatching {
                val cameraProvider = ProcessCameraProvider.awaitInstance(this@MonitoringForegroundService)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), drowsinessAnalyzer) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this@MonitoringForegroundService,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis  // No Preview — headless, no UI surface needed
                )
            }
        }
    }

    private fun handleImpairment(subtype: ImpairmentSubtype) {
        val driverId = auth.currentUser?.uid ?: return

        // ── Alert ──────────────────────────────────────────────────────────
        alertOrchestrator.triggerAlert()

        // ── Escalation counter (3+ events in 30 min → High Risk) ──────────
        val now = System.currentTimeMillis()
        if (consecutiveAlertCount == 0) firstAlertWindowStartMs = now
        if (now - firstAlertWindowStartMs > ESCALATION_WINDOW_MS) {
            // Window expired — reset
            consecutiveAlertCount = 1
            firstAlertWindowStartMs = now
        } else {
            consecutiveAlertCount++
        }
        val severity = when {
            consecutiveAlertCount >= 3 -> Severity.HIGH
            consecutiveAlertCount == 2 -> Severity.MEDIUM
            else -> Severity.LOW
        }

        // ── Write event to Firestore (or offline queue) ────────────────────
        val event = ImpairmentEvent(
            type = EventType.IMPAIRMENT,
            subtype = subtype,
            driverId = driverId,
            tripId = currentTripId,
            companyId = currentCompanyId,
            timestamp = now,
            lat = lastLat,
            lng = lastLng,
            severity = severity
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val wrote = runCatching { firestore.writeEvent(event) }.isSuccess
            if (!wrote) {
                // Queue locally for later sync
                pendingEventDao.insert(
                    PendingEventEntity(
                        localId = UUID.randomUUID().toString(),
                        type = event.type.toFirestoreValue(),
                        subtype = event.subtype.toFirestoreValue(),
                        driverId = event.driverId,
                        tripId = event.tripId,
                        companyId = event.companyId,
                        timestamp = event.timestamp,
                        lat = event.lat,
                        lng = event.lng,
                        severity = event.severity.toFirestoreValue()
                    )
                )
            }
            // Increment counter on the trip document
            runCatching { firestore.incrementTripCounter(currentTripId, "drowsyEventCount") }

            // Mark trip HIGH_RISK if escalation threshold reached
            if (severity == Severity.HIGH) {
                runCatching { firestore.updateTripStatus(currentTripId, TripStatus.HIGH_RISK) }
            }
        }
    }

    fun stopMonitoring(tripId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { firestore.updateTripStatus(tripId, TripStatus.COMPLETE) }
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        alertOrchestrator.release()
        drowsinessAnalyzer.release()
        fusedLocation.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_COMPANY_ID = "extra_company_id"
        private const val NOTIFICATION_ID = 1001
        private const val ESCALATION_WINDOW_MS = 30 * 60 * 1000L  // 30 minutes
    }
}
