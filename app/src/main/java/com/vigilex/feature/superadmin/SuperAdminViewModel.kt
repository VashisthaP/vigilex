package com.vigilex.feature.superadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.Company
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

data class SuperAdminUiState(
    val owners: List<User> = emptyList(),
    val activeTripsCount: Int = 0,
    val totalAlerts: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SuperAdminViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirestoreDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuperAdminUiState())
    val uiState: StateFlow<SuperAdminUiState> = _uiState.asStateFlow()

    /** Phones already mirrored this session — keeps the backfill to one write each. */
    private val syncedPhones = mutableSetOf<String>()

    init {
        // Observe all owners (users with role OWNER)
        viewModelScope.launch {
            firestore.observeAllUsers().collect { users ->
                _uiState.value = _uiState.value.copy(
                    owners = users.filter { it.role == Role.OWNER }
                )
                // Backfill the pre-auth phone gate for users created before it
                // existed. Super Admin sees every user, so this covers them all.
                val missing = users.map { it.phone }.filter { it.isNotBlank() && it !in syncedPhones }
                if (missing.isNotEmpty()) {
                    syncedPhones += missing
                    firestore.ensureAuthorizedPhones(missing)
                }
            }
        }
        viewModelScope.launch {
            firestore.observeAllTrips().collect { trips ->
                _uiState.value = _uiState.value.copy(
                    activeTripsCount = trips.count { it.status == TripStatus.ACTIVE || it.status == TripStatus.HIGH_RISK },
                    totalAlerts = trips.sumOf { it.drowsyEventCount }
                )
            }
        }
    }

    /**
     * Registers a new Owner in Firestore so they can log in via phone OTP.
     * A placeholder company is auto-created for the owner.
     * No Firebase Auth account is created — the owner logs in via phone OTP later.
     */
    fun addOwner(
        ownerName:  String,
        ownerEmail: String,
        ownerPhone: String
    ) {
        if (ownerName.isBlank() || ownerPhone.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Name and phone are required")
            return
        }
        val normalizedPhone = normalizePhone(ownerPhone)
        if (normalizedPhone.length < 13) {
            _uiState.value = _uiState.value.copy(error = "Enter a valid 10-digit phone number")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                // Check if owner with this phone already exists
                val existing = firestore.getUserByPhone(normalizedPhone)
                if (existing != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "An owner with this phone number already exists."
                    )
                    return@launch
                }

                val companyId      = UUID.randomUUID().toString()
                val placeholderUid = "pending_${normalizedPhone.replace("+", "")}"

                // Auto-create a company for this owner (named after them)
                firestore.createCompany(
                    Company(
                        id          = companyId,
                        companyName = "${ownerName.trim()}'s Fleet",
                        ownerUid    = placeholderUid,
                        createdAt   = System.currentTimeMillis()
                    )
                )
                firestore.createUser(
                    User(
                        uid       = placeholderUid,
                        name      = ownerName.trim(),
                        email     = ownerEmail.trim(),
                        phone     = normalizedPhone,
                        role      = Role.OWNER,
                        companyId = companyId
                    )
                )
                // Mirror the phone so the owner passes the pre-auth OTP gate
                firestore.addAuthorizedPhone(normalizedPhone)

                _uiState.value = _uiState.value.copy(
                    isLoading      = false,
                    successMessage = "Owner '${ownerName.trim()}' authorized. They can now log in via OTP."
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun deleteOwner(uid: String) {
        viewModelScope.launch {
            runCatching {
                // Revoke the phone gate first, so a half-failed delete can't
                // leave a number able to request OTPs.
                firestore.getUser(uid)?.phone?.let { phone ->
                    firestore.removeAuthorizedPhone(phone)
                    syncedPhones -= phone
                }
                firestore.deleteUser(uid)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
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
}
