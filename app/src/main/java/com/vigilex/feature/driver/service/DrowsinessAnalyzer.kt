package com.vigilex.feature.driver.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.vigilex.core.model.ImpairmentSubtype
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs

/**
 * Combines ML Kit face detection + accelerometer readings to detect both
 * drowsiness AND drunk-driving impairment signals.
 *
 * SIGNAL 1 — EYE_CLOSURE (drowsy / intoxicated eye droop):
 *   Both eyes < threshold (default 0.25) for ≥ 2 consecutive seconds.
 *
 * SIGNAL 2 — HEAD_DROP (nodding asleep / intoxicated head loll):
 *   abs(eulerZ) > 20° or abs(eulerY) > 25° for ≥ 1.5 consecutive seconds.
 *
 * SIGNAL 3 — ERRATIC_MOTION (drunk swerving / sudden lateral G-force):
 *   abs(lateral acceleration X-axis) > 4 m/s² sustained for ≥ 2 seconds.
 *   This is the primary drunk-driving differentiator — drowsy drivers don't
 *   typically show sustained lateral spikes whereas intoxicated drivers do.
 *
 * COMBINED: Two or more signals active simultaneously → highest severity.
 *
 * Baseline calibration (first 60 s):
 *   Records rolling eye openness average and adjusts EYE_CLOSURE threshold
 *   to avg * 0.5 so drivers with naturally smaller eyes aren't penalised.
 *
 * Speed gate: DISABLED — monitoring runs at all speeds including stationary.
 */
class DrowsinessAnalyzer(
    private val sensorManager: SensorManager,
    private val onImpairmentDetected: (subtype: ImpairmentSubtype) -> Unit,
    private val onCalibrationComplete: (threshold: Float) -> Unit
) : ImageAnalysis.Analyzer, SensorEventListener {

    // ── ML Kit face detector ────────────────────────────────────────────────
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    // ── Detection state ─────────────────────────────────────────────────────
    private var eyeClosedStartMs: Long = -1L
    private var headDropStartMs: Long = -1L
    private var lastEventMs: Long = 0L        // debounce between logged events

    // For COMBINED detection — tracks the most recent distinct signal type and when it fired
    private var prevSignalType: ImpairmentSubtype? = null
    private var prevSignalMs:   Long = 0L

    // ── Recovery state: eyes open for 2s = clear alert ─────────────────────
    private var eyesOpenSinceMs: Long = -1L
    @Volatile var isInAlertState: Boolean = false
        private set

    // ── Accelerometer state ─────────────────────────────────────────────────
    private var lateralSpikeStartMs: Long = -1L

    // ── Calibration ─────────────────────────────────────────────────────────
    private var calibrationStartMs = -1L   // set on first frame, not at construction
    private val eyeOpennessSamples = mutableListOf<Float>()
    private var calibrated = false
    private var eyeThreshold = DEFAULT_EYE_THRESHOLD    // adjusted after calibration

    // ── Speed gate ──────────────────────────────────────────────────────────
    @Volatile private var currentSpeedKmh: Float = 0f

    // ── Shared flow for monitoring status updates (for UI) ──────────────────
    private val _statusFlow = MutableSharedFlow<MonitoringStatus>(extraBufferCapacity = 8)
    val statusFlow: SharedFlow<MonitoringStatus> = _statusFlow.asSharedFlow()

    init {
        // Register accelerometer for drunk-driving lateral motion detection
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun setCurrentSpeed(speedMs: Float) {
        currentSpeedKmh = speedMs * 3.6f  // convert m/s → km/h
    }

    // ── SensorEventListener — accelerometer ────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        if (!calibrated) return  // don't process accelerometer until face calibration is done

        // X-axis = lateral (left/right) — drunk-driving swerve signal
        // Y-axis = forward/backward — hard braking
        val lateralAccel = abs(event.values[0])
        val now = System.currentTimeMillis()

        // Only fire on VERY high sustained lateral force — normal turns (parking, roundabouts)
        // produce brief 2-5 m/s² spikes. Drunk swerving is 8+ m/s² sustained for 3+ seconds.
        if (lateralAccel > LATERAL_ACCEL_THRESHOLD_MS2) {
            if (lateralSpikeStartMs < 0) lateralSpikeStartMs = now
            val duration = now - lateralSpikeStartMs
            if (duration >= LATERAL_SPIKE_DURATION_MS && canFireEvent()) {
                lateralSpikeStartMs = -1L
                fireImpairment(ImpairmentSubtype.ERRATIC_MOTION)
            }
        } else {
            lateralSpikeStartMs = -1L  // spike ended before threshold
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    // ── ImageAnalysis.Analyzer — ML Kit face detection ─────────────────────

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                if (face == null) {
                    // No face detected — reset timers, don't false-positive
                    eyeClosedStartMs = -1L
                    headDropStartMs = -1L
                    _statusFlow.tryEmit(MonitoringStatus.FaceNotDetected)
                } else {
                    processFace(
                        leftEye = face.leftEyeOpenProbability ?: 1f,
                        rightEye = face.rightEyeOpenProbability ?: 1f,
                        eulerY = face.headEulerAngleY,
                        eulerZ = face.headEulerAngleZ
                    )
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun processFace(leftEye: Float, rightEye: Float, eulerY: Float, eulerZ: Float) {
        val now = System.currentTimeMillis()
        if (calibrationStartMs < 0) calibrationStartMs = now  // start timer on first real frame
        val sinceStart = now - calibrationStartMs
        // ── Calibration window (first 60 s) ──────────────────────────────
        if (!calibrated && sinceStart < CALIBRATION_WINDOW_MS) {
            eyeOpennessSamples += (leftEye + rightEye) / 2f
            _statusFlow.tryEmit(MonitoringStatus.Calibrating(progress = (sinceStart / CALIBRATION_WINDOW_MS.toFloat()).coerceIn(0f, 1f)))
            return  // don't fire alerts during calibration
        }
        if (!calibrated) {
            val avg = if (eyeOpennessSamples.isNotEmpty()) eyeOpennessSamples.average().toFloat() else DEFAULT_EYE_THRESHOLD
            // Threshold = 60% of average — eyes significantly more closed than normal
            // Floor 0.2 (very narrow eyes) / ceiling 0.45 (wide open eyes)
            eyeThreshold = (avg * 0.6f).coerceIn(0.20f, 0.45f)
            calibrated = true
            onCalibrationComplete(eyeThreshold)
        }

        // ── Signal 1: Eye closure ─────────────────────────────────────────
        val eyesClosed = leftEye < eyeThreshold && rightEye < eyeThreshold
        if (eyesClosed) {
            eyesOpenSinceMs = -1L  // reset recovery timer
            if (eyeClosedStartMs < 0) eyeClosedStartMs = now
            val closedMs = now - eyeClosedStartMs
            if (closedMs >= EYE_CLOSED_DURATION_MS && canFireEvent()) {
                eyeClosedStartMs = -1L
                fireImpairment(ImpairmentSubtype.EYE_CLOSURE)
                return
            }
        } else {
            eyeClosedStartMs = -1L
            // Eyes are open — track how long they've been open for recovery
            if (eyesOpenSinceMs < 0) eyesOpenSinceMs = now
        }

        // ── Signal 2: Head drop ───────────────────────────────────────────
        val headDropped = abs(eulerZ) > HEAD_EULER_Z_DEG || abs(eulerY) > HEAD_EULER_Y_DEG
        if (headDropped) {
            if (headDropStartMs < 0) headDropStartMs = now
            val droppedMs = now - headDropStartMs
            if (droppedMs >= HEAD_DROP_DURATION_MS && canFireEvent()) {
                headDropStartMs = -1L
                fireImpairment(ImpairmentSubtype.HEAD_DROP)
                return
            }
        } else {
            headDropStartMs = -1L
        }

        // ── Recovery: eyes open for 2s → clear alert state ───────────────
        if (isInAlertState && eyesOpenSinceMs > 0 && (now - eyesOpenSinceMs) >= RECOVERY_OPEN_MS) {
            isInAlertState = false
            eyesOpenSinceMs = -1L
            _statusFlow.tryEmit(MonitoringStatus.Recovered)
            return
        }

        if (!isInAlertState) {
            _statusFlow.tryEmit(MonitoringStatus.Active)
        }
    }

    private fun fireImpairment(subtype: ImpairmentSubtype) {
        val now = System.currentTimeMillis()

        // Upgrade to COMBINED if a DIFFERENT signal type fired within the combined window
        val resolvedSubtype = if (
            prevSignalType != null &&
            prevSignalType != subtype &&
            (now - prevSignalMs) <= COMBINED_WINDOW_MS
        ) {
            ImpairmentSubtype.COMBINED
        } else {
            subtype
        }

        // Record this signal for future COMBINED checks
        prevSignalType = subtype
        prevSignalMs   = now
        lastEventMs    = now

        isInAlertState = true
        eyesOpenSinceMs = -1L  // reset recovery timer
        _statusFlow.tryEmit(MonitoringStatus.Alert(resolvedSubtype))
        onImpairmentDetected(resolvedSubtype)
    }

    /** Returns true if enough time has passed since the last event (30s debounce). */
    private fun canFireEvent(): Boolean =
        System.currentTimeMillis() - lastEventMs > EVENT_DEBOUNCE_MS

    /** Called by UI "Stop Alarm" button or auto-recovery. */
    fun clearAlertState() {
        isInAlertState = false
        eyesOpenSinceMs = -1L
        _statusFlow.tryEmit(MonitoringStatus.Active)
    }

    fun release() {
        sensorManager.unregisterListener(this)
        faceDetector.close()
    }

    companion object {
        private const val DEFAULT_EYE_THRESHOLD = 0.35f           // more sensitive default
        private const val EYE_CLOSED_DURATION_MS = 2_000L         // 2 seconds eyes shut
        private const val HEAD_DROP_DURATION_MS = 1_500L          // 1.5 seconds head tilted
        private const val HEAD_EULER_Z_DEG = 25f                  // relaxed from 20° to reduce false positives on turns
        private const val HEAD_EULER_Y_DEG = 30f                  // relaxed from 25° to reduce false positives when looking sideways
        private const val LATERAL_ACCEL_THRESHOLD_MS2 = 8f        // raised from 4 — normal turns hit 3-5, drunk swerving 8+
        private const val LATERAL_SPIKE_DURATION_MS = 3_000L      // raised from 2s to 3s — must be sustained, not brief
        private const val CALIBRATION_WINDOW_MS = 15_000L         // 15 seconds
        private const val EVENT_DEBOUNCE_MS = 5_000L              // 5 seconds — re-alerts if eyes still closed
        private const val COMBINED_WINDOW_MS = 10_000L            // 10s window for COMBINED
        private const val RECOVERY_OPEN_MS = 2_000L              // eyes open 2s = alarm auto-stop
    }
}

/** Emitted to the UI to drive the camera border color + status label. */
sealed class MonitoringStatus {
    object Active : MonitoringStatus()
    object Paused : MonitoringStatus()
    object FaceNotDetected : MonitoringStatus()
    object Recovered : MonitoringStatus()   // eyes opened for 2s after alert — alarm stopped
    data class Calibrating(val progress: Float) : MonitoringStatus()
    data class Alert(val subtype: ImpairmentSubtype) : MonitoringStatus()
}
