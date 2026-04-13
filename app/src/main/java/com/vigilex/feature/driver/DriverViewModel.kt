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
    val showImpairmentDisclaimer: Boolean = false
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
    // Without this guard, the first Firestore snapshot (null — no trips yet)
    // would immediately set isTripComplete = true and sign the driver out.
    private var hadActiveTrip = false

    // Cached exit PIN from Firestore — allows driver to unlock without owner OTP.
    private var driverExitPin: String = ""

    init {
        observeActiveTrip()
        checkDisclaimerShown()
        loadDriverExitPin()
    }

    private fun loadDriverExitPin() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            driverExitPin = runCatching { firestore.getUser(uid)?.exitPin ?: "" }.getOrDefault("")
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
                    startMonitoringService(trip.id, trip.companyId)
                    registerGeofence(trip)
                } else if (hadActiveTrip && (trip == null || trip.status == TripStatus.COMPLETE)) {
                    // Only complete the session if a trip was previously active —
                    // prevents signing out when no trips exist on first login.
                    _uiState.value = _uiState.value.copy(isTripComplete = true)
                    stopMonitoringService()
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
    }

    private fun stopMonitoringService() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, MonitoringForegroundService::class.java))
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

        // Log the close attempt
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

                // Generate and store OTP for owner to see
                val code = otpGenerator.generate()
                val expiry = otpGenerator.expiryTimestamp()
                firestore.writeOtp(tripId, code, expiry)
                // Note: FCM push to owner is triggered by a Firestore Cloud Function
                // watching the otps/{tripId} document for changes.
            }
        }
    }

    fun submitOtp(code: String) {
        val tripId = _uiState.value.trip?.id ?: return
        viewModelScope.launch {
            // Accept either the trip-specific OTP (generated per exit attempt)
            // or the driver's static exit PIN (set by owner at driver creation).
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

    fun updateMonitoringStatus(status: MonitoringStatus) {
        _uiState.value = _uiState.value.copy(monitoringStatus = status)
    }

    companion object {
        private const val ARRIVAL_RADIUS_METERS = 200f
    }
}
