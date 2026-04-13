package com.vigilex.feature.owner.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.Trip
import com.vigilex.core.model.TripStatus
import com.vigilex.core.model.User
import com.vigilex.core.util.OtpGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class DriverCardData(
    val driver: User,
    val activeTrip: Trip?
)

data class OwnerDashboardUiState(
    val driverCards: List<DriverCardData> = emptyList(),
    val isLoading: Boolean = true,
    val ownerUser: User? = null
)

@HiltViewModel
class OwnerDashboardViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirestoreDataSource,
    private val messaging: FirebaseMessaging,
    private val otpGenerator: OtpGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(OwnerDashboardUiState())
    val uiState: StateFlow<OwnerDashboardUiState> = _uiState.asStateFlow()

    init {
        loadOwnerData()
        refreshFcmToken()
    }

    private fun loadOwnerData() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val owner = firestore.getUser(uid)
            _uiState.value = _uiState.value.copy(ownerUser = owner)

            if (owner != null) {
                // Observe drivers + their trips in real-time
                firestore.observeDriversForOwner(uid, owner.companyId).collect { drivers ->
                    val cards = drivers.map { driver ->
                        val trip = runCatching {
                            // Get latest active trip for each driver from trips observed via owner
                            null  // populated below via trip observation
                        }.getOrNull()
                        DriverCardData(driver = driver, activeTrip = trip)
                    }
                    _uiState.value = _uiState.value.copy(driverCards = cards, isLoading = false)
                }
            }
        }

        // Also observe all trips for owner and cross-reference with drivers
        viewModelScope.launch {
            val uid2 = auth.currentUser?.uid ?: return@launch
            firestore.observeTripsForOwner(uid2).collect { trips ->
                val activeTripsByDriver = trips
                    .filter { it.status == TripStatus.ACTIVE || it.status == TripStatus.HIGH_RISK }
                    .associateBy { it.driverId }

                _uiState.value = _uiState.value.copy(
                    driverCards = _uiState.value.driverCards.map { card ->
                        card.copy(activeTrip = activeTripsByDriver[card.driver.uid])
                    }
                )
            }
        }
    }

    private fun refreshFcmToken() {
        viewModelScope.launch {
            runCatching {
                val token = messaging.token.await()
                val uid = auth.currentUser?.uid ?: return@runCatching
                firestore.updateFcmToken(uid, token)
            }
        }
    }

    fun sendOtpToDriver(tripId: String) {
        viewModelScope.launch {
            runCatching {
                val code = otpGenerator.generate()
                val expiry = otpGenerator.expiryTimestamp()
                firestore.writeOtp(tripId, code, expiry)
                // Firestore Cloud Function picks up this write and FCMs the driver
            }
        }
    }
}
