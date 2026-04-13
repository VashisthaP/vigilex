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
 * Speed gate:
 *   Caller must call setCurrentSpeed(). Detection is suppressed below 20 km/h.
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
    private var lastEventMs: Long = 0L        // debounce: min 30s between logged events

    // For COMBINED detection — tracks the most recent distinct signal type and when it fired
    private var prevSignalType: ImpairmentSubtype? = null
    private var prevSignalMs:   Long = 0L

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
        if (currentSpeedKmh < SPEED_GATE_KMH) return  // parked — ignore jerks

        // X-axis = lateral (left/right) — primary drunk-driving swerve signal
        val lateralAccel = abs(event.values[0])
        val now = System.currentTimeMillis()

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
        val belowSpeedGate = currentSpeedKmh < SPEED_GATE_KMH

        // ── Calibration window (first 60 s) ──────────────────────────────
        if (!calibrated && sinceStart < CALIBRATION_WINDOW_MS) {
            eyeOpennessSamples += (leftEye + rightEye) / 2f
            _statusFlow.tryEmit(MonitoringStatus.Calibrating(progress = (sinceStart / CALIBRATION_WINDOW_MS.toFloat()).coerceIn(0f, 1f)))
            return  // don't fire alerts during calibration
        }
        if (!calibrated) {
            val avg = if (eyeOpennessSamples.isNotEmpty()) eyeOpennessSamples.average().toFloat() else DEFAULT_EYE_THRESHOLD
            eyeThreshold = (avg * 0.5f).coerceIn(0.15f, 0.35f)  // floor/ceiling safety
            calibrated = true
            onCalibrationComplete(eyeThreshold)
        }

        if (belowSpeedGate) {
            eyeClosedStartMs = -1L
            headDropStartMs = -1L
            _statusFlow.tryEmit(MonitoringStatus.Paused)
            return
        }

        // ── Signal 1: Eye closure ─────────────────────────────────────────
        val eyesClosed = leftEye < eyeThreshold && rightEye < eyeThreshold
        if (eyesClosed) {
            if (eyeClosedStartMs < 0) eyeClosedStartMs = now
            val closedMs = now - eyeClosedStartMs
            if (closedMs >= EYE_CLOSED_DURATION_MS && canFireEvent()) {
                eyeClosedStartMs = -1L
                fireImpairment(ImpairmentSubtype.EYE_CLOSURE)
                return
            }
        } else {
            eyeClosedStartMs = -1L
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

        _statusFlow.tryEmit(MonitoringStatus.Active)
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

        _statusFlow.tryEmit(MonitoringStatus.Alert(resolvedSubtype))
        onImpairmentDetected(resolvedSubtype)
    }

    /** Returns true if enough time has passed since the last event (30s debounce). */
    private fun canFireEvent(): Boolean =
        System.currentTimeMillis() - lastEventMs > EVENT_DEBOUNCE_MS

    fun release() {
        sensorManager.unregisterListener(this)
        faceDetector.close()
    }

    companion object {
        private const val DEFAULT_EYE_THRESHOLD = 0.25f
        private const val EYE_CLOSED_DURATION_MS = 2_000L        // 2 seconds
        private const val HEAD_DROP_DURATION_MS = 1_500L          // 1.5 seconds
        private const val HEAD_EULER_Z_DEG = 20f
        private const val HEAD_EULER_Y_DEG = 25f
        private const val LATERAL_ACCEL_THRESHOLD_MS2 = 4f        // m/s² — sustained swerve
        private const val LATERAL_SPIKE_DURATION_MS = 2_000L      // 2 seconds sustained
        private const val SPEED_GATE_KMH = 0f   // TODO: set back to 20f for production
        private const val CALIBRATION_WINDOW_MS = 60_000L         // 60 seconds
        private const val EVENT_DEBOUNCE_MS = 30_000L              // 30 seconds between events
        private const val COMBINED_WINDOW_MS = 10_000L             // 10s window for COMBINED
    }
}

/** Emitted to the UI to drive the status dot state. */
sealed class MonitoringStatus {
    object Active : MonitoringStatus()
    object Paused : MonitoringStatus()
    object FaceNotDetected : MonitoringStatus()
    data class Calibrating(val progress: Float) : MonitoringStatus()
    data class Alert(val subtype: ImpairmentSubtype) : MonitoringStatus()
}
