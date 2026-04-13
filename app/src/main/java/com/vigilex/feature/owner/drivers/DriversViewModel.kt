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
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DriversUiState(
    val drivers: List<User> = emptyList(),
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

    init {
        loadDrivers()
    }

    private fun loadDrivers() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val owner = firestore.getUser(uid) ?: return@launch
            ownerCompanyId = owner.companyId
            firestore.observeDriversForOwner(uid, owner.companyId).collect {
                _uiState.value = _uiState.value.copy(drivers = it)
            }
        }
    }

    /**
     * Registers a new driver in Firestore only — no Firebase Auth account needed.
     * With phone OTP auth, Firebase Auth is created automatically on first OTP login.
     * The driver's phone number is the only required identifier.
     */
    fun addDriver(name: String, phone: String) {
        if (name.isBlank() || phone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Name and phone are required")
            return
        }
        val normalizedPhone = normalizePhone(phone)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                // Generate a placeholder UID — will be replaced with real Firebase Auth UID on first login
                val placeholderUid = "pending_${normalizedPhone.replace("+", "")}"
                firestore.createUser(
                    User(
                        uid       = placeholderUid,
                        name      = name.trim(),
                        email     = "",
                        phone     = normalizedPhone,
                        role      = Role.DRIVER,
                        companyId = ownerCompanyId
                    )
                )
                _uiState.value = _uiState.value.copy(
                    isLoading      = false,
                    successMessage = "Driver '$name' added. They can now log in using their phone number."
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading     = false,
                    errorMessage  = e.message ?: "Failed to add driver"
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
        val ownerId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                firestore.createTrip(
                    Trip(
                        driverId = driverId,
                        ownerId = ownerId,
                        companyId = ownerCompanyId,
                        destination = Destination(destinationName, destLat, destLng),
                        startTime = System.currentTimeMillis(),
                        status = TripStatus.ACTIVE
                    )
                )
                _uiState.value = _uiState.value.copy(successMessage = "Trip assigned successfully!")
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.message)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(successMessage = null, errorMessage = null)
    }

}
