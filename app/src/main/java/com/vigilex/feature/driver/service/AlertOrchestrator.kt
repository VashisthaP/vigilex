package com.vigilex.feature.driver.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vigilex.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages the multi-step alert sequence when impairment is detected:
 *
 * 1. Force audio volume to MAX
 * 2. Try Bluetooth SCO (BT headset / speaker) first — drivers often have BT earpieces
 * 3. If BT unavailable / SCO fails, fall back to phone speaker (STREAM_ALARM)
 * 4. Play 3 escalating audio bursts (short → medium → long)
 * 5. Vibrate in urgent repeating pattern
 *
 * The visual alert (red flash overlay) is handled in DriverHomeScreen
 * by observing MonitoringStatus.Alert from the service.
 */
class AlertOrchestrator(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun triggerAlert() {
        scope.launch {
            forceMaxVolume()
            val btConnected = tryBluetoothSco()
            playEscalatingAlarm(useBluetooth = btConnected)
            vibrate()
        }
    }

    private fun forceMaxVolume() {
        val stream = AudioManager.STREAM_ALARM
        val maxVol = audioManager.getStreamMaxVolume(stream)
        audioManager.setStreamVolume(stream, maxVol, 0)
    }

    private fun tryBluetoothSco(): Boolean {
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter: BluetoothAdapter? = btManager?.adapter
            if (btAdapter == null || !btAdapter.isEnabled) return false

            // Check if any BT audio device is connected
            val hasConnectedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                btAdapter.bondedDevices.isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                btAdapter.bondedDevices?.isNotEmpty() == true
            }
            if (!hasConnectedDevice) return false

            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            true
        } catch (e: Exception) {
            // BT SCO failed — phone speaker will be used
            false
        }
    }

    private suspend fun playEscalatingAlarm(useBluetooth: Boolean) {
        if (!useBluetooth) {
            // Route to speaker (alarm stream bypasses silent mode)
            audioManager.mode = AudioManager.MODE_NORMAL
        }

        // 3 escalating bursts: 0.5s → 1s → 2s
        listOf(500L, 1_000L, 2_000L).forEach { duration ->
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.alert_alarm)?.apply {
                isLooping = false
                start()
            }
            // If MediaPlayer creation failed (missing resource), still delay for pacing
            delay(duration + 300L)
        }
    }

    private fun vibrate() {
        // Urgent repeating pattern: wait 0 → buzz 200ms → wait 100ms → buzz 400ms → wait 100ms → repeat
        val pattern = longArrayOf(0L, 200L, 100L, 400L, 100L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(pattern, /*repeat=*/0)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, /*repeat=*/0)
        }
    }

    fun stopAlert() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        audioManager.isBluetoothScoOn = false
        audioManager.stopBluetoothSco()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        }
    }

    fun release() {
        stopAlert()
    }
}
