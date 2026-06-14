package com.vigilex.feature.driver.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.vigilex.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages the alert sequence when impairment is detected:
 *
 * 1. Force audio volume to MAX
 * 2. Try Bluetooth SCO first — drivers often have BT earpieces
 * 3. If BT unavailable, fall back to phone speaker (STREAM_ALARM)
 * 4. Play alarm sound (looping until stopped)
 *
 * NO vibration — user feedback says it's distracting.
 *
 * The alarm stops when:
 * - Driver opens eyes for 2+ seconds (auto-stop via service)
 * - Driver taps "Stop Alarm" button in UI (manual stop via service)
 */
class AlertOrchestrator(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var alertJob: Job? = null

    @Volatile var isAlertActive: Boolean = false
        private set

    fun triggerAlert() {
        if (isAlertActive) return  // don't stack multiple alerts
        isAlertActive = true
        alertJob = scope.launch {
            forceMaxVolume()
            val btConnected = tryBluetoothSco()
            playAlarm(useBluetooth = btConnected)
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
            false
        }
    }

    private fun playAlarm(useBluetooth: Boolean) {
        if (!useBluetooth) {
            audioManager.mode = AudioManager.MODE_NORMAL
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, R.raw.alert_alarm)?.apply {
            isLooping = true   // loop until explicitly stopped
            start()
        }
    }

    fun stopAlert() {
        isAlertActive = false
        alertJob?.cancel()
        alertJob = null

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        audioManager.isBluetoothScoOn = false
        audioManager.stopBluetoothSco()
    }

    fun release() {
        stopAlert()
    }
}
