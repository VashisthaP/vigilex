package com.vigilex.feature.driver

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.firebase.auth.FirebaseAuth
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.ImpairmentEvent
import com.vigilex.core.model.EventType
import com.vigilex.core.model.ImpairmentSubtype
import com.vigilex.core.model.Severity
import com.vigilex.core.model.Trip
import com.vigilex.core.model.TripStatus
import com.vigilex.core.util.OtpGenerator
import com.vigilex.feature.driver.service.GeofenceReceiver
import com.vigilex.feature.driver.service.MonitoringForegroundService
import com.vigilex.feature.driver.service.MonitoringStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.PendingIntent

data class DriverUiState(
    val trip: Trip? = null,
    val monitoringStatus: MonitoringStatus = MonitoringStatus.Calibrating(0f),
    val showOtpDialog: Boolean = false,
    val otpError: String? = null,
    val isTripComplete: Boolean = false,
    val showImpairmentDisclaimer: Boolean = false,
    val isMonitoringRunning: Boolean = false
)

@HiltViewModel
class DriverViewModel @Inject constructor(
    application: Application,
    private val auth: FirebaseAuth,
    private val firestore: FirestoreDataSource,
    private val geofencingClient: GeofencingClient,
    private val otpGenerator: OtpGenerator
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DriverUiState())
    val uiState: StateFlow<DriverUiState> = _uiState.asStateFlow()

    // Tracks whether a trip was seen as ACTIVE in this session.
    private var hadActiveTrip = false

    // Cached exit PIN from Firestore
    private var driverExitPin: String = ""

    private var permissionsReady = false
    private var observingTrip = false

    init {
        observeActiveTrip()
        checkDisclaimerShown()
        loadDriverExitPin()
    }

    /**
     * Called by DriverHomeScreen once all runtime permissions (camera, location) are granted.
     * Immediately starts the monitoring service — no need to wait for a trip.
     */
    fun onPermissionsGranted() {
        permissionsReady = true
        // Start monitoring immediately — camera + drowsiness detection begin right away
        startMonitoringService(
            tripId = _uiState.value.trip?.id ?: "",
            companyId = _uiState.value.trip?.companyId ?: ""
        )
    }

    private fun loadDriverExitPin() {
        val uid = auth.currentUser?.uid ?: return
        val phone = auth.currentUser?.phoneNumber ?: ""
        viewModelScope.launch {
            var pin = runCatching { firestore.getUser(uid)?.exitPin }.getOrNull() ?: ""
            if (pin.isBlank() && phone.isNotBlank()) {
                pin = runCatching { firestore.getUserByPhone(phone)?.exitPin }.getOrNull() ?: ""
            }
            driverExitPin = pin
        }
    }

    private fun checkDisclaimerShown() {
        val prefs = getApplication<Application>().getSharedPreferences("vigilex_prefs", 0)
        if (!prefs.getBoolean("disclaimer_shown", false)) {
            _uiState.value = _uiState.value.copy(showImpairmentDisclaimer = true)
        }
    }

    fun dismissDisclaimer() {
        getApplication<Application>().getSharedPreferences("vigilex_prefs", 0)
            .edit().putBoolean("disclaimer_shown", true).apply()
        _uiState.value = _uiState.value.copy(showImpairmentDisclaimer = false)
    }

    private fun observeActiveTrip() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            firestore.observeActiveTrip(uid).collect { trip ->
                _uiState.value = _uiState.value.copy(trip = trip)
                if (trip != null && trip.status == TripStatus.ACTIVE) {
                    hadActiveTrip = true
                    // Update service with trip details if monitoring is already running
                    if (permissionsReady) {
                        startMonitoringService(trip.id, trip.companyId)
                    }
                    registerGeofence(trip)
                } else if (hadActiveTrip && (trip == null || trip.status == TripStatus.COMPLETE)) {
                    _uiState.value = _uiState.value.copy(isTripComplete = true)
                    // Don't stop monitoring — it runs regardless of trip
                }
            }
        }
    }

    private fun startMonitoringService(tripId: String, companyId: String) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MonitoringForegroundService::class.java).apply {
            putExtra(MonitoringForegroundService.EXTRA_TRIP_ID, tripId)
            putExtra(MonitoringForegroundService.EXTRA_COMPANY_ID, companyId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
        _uiState.value = _uiState.value.copy(isMonitoringRunning = true)
    }

    fun stopMonitoringService() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, MonitoringForegroundService::class.java))
        _uiState.value = _uiState.value.copy(isMonitoringRunning = false)
    }

    private fun registerGeofence(trip: Trip) {
        val ctx = getApplication<Application>()
        val geofence = Geofence.Builder()
            .setRequestId(trip.id)
            .setCircularRegion(trip.destination.lat, trip.destination.lng, ARRIVAL_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val geofenceIntent = PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        geofencingClient.addGeofences(request, geofenceIntent)
    }

    // ── OTP Flow ──────────────────────────────────────────────────────────

    fun requestOtpExit() {
        _uiState.value = _uiState.value.copy(showOtpDialog = true, otpError = null)
        val tripId = _uiState.value.trip?.id ?: return

        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            runCatching {
                firestore.writeEvent(
                    ImpairmentEvent(
                        type = EventType.CLOSE_ATTEMPT,
                        subtype = ImpairmentSubtype.COMBINED,
                        driverId = uid,
                        tripId = tripId,
                        companyId = _uiState.value.trip?.companyId ?: "",
                        timestamp = System.currentTimeMillis(),
                        lat = 0.0, lng = 0.0,
                        severity = Severity.LOW
                    )
                )
                firestore.incrementTripCounter(tripId, "closeAttemptCount")

                val code = otpGenerator.generate()
                val expiry = otpGenerator.expiryTimestamp()
                firestore.writeOtp(tripId, code, expiry)
            }
        }
    }

    fun submitOtp(code: String) {
        val tripId = _uiState.value.trip?.id ?: return
        viewModelScope.launch {
            val staticPinValid = driverExitPin.isNotBlank() && code == driverExitPin
            val tripOtpValid   = runCatching { firestore.validateOtp(tripId, code) }.getOrDefault(false)

            if (staticPinValid || tripOtpValid) {
                stopMonitoringService()
                _uiState.value = _uiState.value.copy(
                    showOtpDialog  = false,
                    isTripComplete = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    otpError = "Invalid PIN. Use your 6-digit exit PIN or request OTP from owner."
                )
            }
        }
    }

    fun dismissOtpDialog() {
        _uiState.value = _uiState.value.copy(showOtpDialog = false, otpError = null)
    }

    /** Validates the exit PIN and calls back with the result on the main thread. */
    fun validateExitPin(pin: String, onResult: (Boolean) -> Unit) {
        val valid = driverExitPin.isNotBlank() && pin == driverExitPin
        if (valid) stopMonitoringService()
        onResult(valid)
    }

    fun updateMonitoringStatus(status: MonitoringStatus) {
        _uiState.value = _uiState.value.copy(monitoringStatus = status)
    }

    companion object {
        private const val ARRIVAL_RADIUS_METERS = 200f
    }
}
