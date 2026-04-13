package com.vigilex.feature.owner.drivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.Destination
import com.vigilex.core.model.Role
import com.vigilex.core.model.Trip
import com.vigilex.core.model.TripStatus
import com.vigilex.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DriversUiState(
    val drivers: List<User> = emptyList(),
    /** Maps driverId → active Trip (if any). Used to hide the Assign Trip button. */
    val activeTrips: Map<String, Trip> = emptyMap(),
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class DriversViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirestoreDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriversUiState())
    val uiState: StateFlow<DriversUiState> = _uiState.asStateFlow()

    private var ownerCompanyId: String = ""
    private var ownerId: String = ""

    init {
        loadDriversAndTrips()
    }

    private fun loadDriversAndTrips() {
        val uid = auth.currentUser?.uid ?: return
        ownerId = uid
        viewModelScope.launch {
            val owner = firestore.getUser(uid) ?: return@launch
            ownerCompanyId = owner.companyId

            // Combine driver list + all owner trips so we can mark which drivers are busy
            combine(
                firestore.observeDriversForOwner(uid, owner.companyId),
                firestore.observeTripsForOwner(uid)
            ) { drivers, trips ->
                val activeMap = trips
                    .filter { it.status == TripStatus.ACTIVE }
                    .associateBy { it.driverId }
                drivers to activeMap
            }.collect { (drivers, activeMap) ->
                _uiState.value = _uiState.value.copy(
                    drivers     = drivers,
                    activeTrips = activeMap
                )
            }
        }
    }

    /**
     * Adds a driver in Firestore only.
     * [exitPin] is a 6-digit PIN set by the owner — the driver uses it to unlock
     * the monitoring lock screen without waiting for an owner-sent OTP.
     */
    fun addDriver(name: String, phone: String, exitPin: String) {
        if (name.isBlank() || phone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Name and phone are required")
            return
        }
        if (exitPin.length != 6 || !exitPin.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(errorMessage = "Exit PIN must be exactly 6 digits")
            return
        }
        val normalizedPhone = normalizePhone(phone)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val placeholderUid = "pending_${normalizedPhone.replace("+", "")}"
                firestore.createUser(
                    User(
                        uid       = placeholderUid,
                        name      = name.trim(),
                        email     = "",
                        phone     = normalizedPhone,
                        role      = Role.DRIVER,
                        companyId = ownerCompanyId,
                        exitPin   = exitPin
                    )
                )
                _uiState.value = _uiState.value.copy(
                    isLoading      = false,
                    successMessage = "Driver '$name' added."
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    errorMessage = e.message ?: "Failed to add driver"
                )
            }
        }
    }

    private fun normalizePhone(raw: String): String {
        val cleaned = raw.trim().replace(" ", "").replace("-", "")
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("0") -> "+91${cleaned.drop(1)}"
            cleaned.length == 10    -> "+91$cleaned"
            else                    -> cleaned
        }
    }

    fun assignTrip(driverId: String, destinationName: String, destLat: Double, destLng: Double) {
        viewModelScope.launch {
            runCatching {
                firestore.createTrip(
                    Trip(
                        driverId    = driverId,
                        ownerId     = ownerId,
                        companyId   = ownerCompanyId,
                        destination = Destination(destinationName, destLat, destLng),
                        startTime   = System.currentTimeMillis(),
                        status      = TripStatus.ACTIVE
                    )
                )
                _uiState.value = _uiState.value.copy(successMessage = "Trip assigned!")
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.message)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(successMessage = null, errorMessage = null)
    }
}
