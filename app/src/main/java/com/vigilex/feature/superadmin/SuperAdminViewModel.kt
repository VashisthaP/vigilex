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
    val companies: List<Company> = emptyList(),
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

    private val _allTrips = MutableStateFlow<List<Trip>>(emptyList())

    init {
        viewModelScope.launch {
            firestore.observeAllCompanies().collect { companies ->
                _uiState.value = _uiState.value.copy(companies = companies)
            }
        }
        viewModelScope.launch {
            firestore.observeAllTrips().collect { trips ->
                _allTrips.value = trips
                _uiState.value = _uiState.value.copy(
                    activeTripsCount = trips.count { it.status == TripStatus.ACTIVE || it.status == TripStatus.HIGH_RISK },
                    totalAlerts = trips.sumOf { it.drowsyEventCount }
                )
            }
        }
    }

    /**
     * Creates a new company and registers the owner in Firestore.
     * No Firebase Auth account is created here — the owner logs in via phone OTP.
     * Firebase Auth UID is assigned automatically on their first OTP login.
     */
    fun addCompany(
        companyName: String,
        ownerName:   String,
        ownerPhone:  String
    ) {
        if (companyName.isBlank() || ownerName.isBlank() || ownerPhone.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "All fields are required")
            return
        }
        val normalizedPhone = normalizePhone(ownerPhone)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val companyId       = UUID.randomUUID().toString()
                val placeholderUid  = "pending_${normalizedPhone.replace("+", "")}"

                firestore.createCompany(
                    Company(
                        id          = companyId,
                        companyName = companyName.trim(),
                        ownerUid    = placeholderUid,
                        createdAt   = System.currentTimeMillis()
                    )
                )
                firestore.createUser(
                    User(
                        uid       = placeholderUid,
                        name      = ownerName.trim(),
                        email     = "",
                        phone     = normalizedPhone,
                        role      = Role.OWNER,
                        companyId = companyId
                    )
                )
                _uiState.value = _uiState.value.copy(
                    isLoading      = false,
                    successMessage = "Company '$companyName' created. Owner can now log in via their phone number."
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
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
