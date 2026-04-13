package com.vigilex.feature.owner.driverdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.ImpairmentEvent
import com.vigilex.core.model.Trip
import com.vigilex.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverDetailUiState(
    val driver: User? = null,
    val trip: Trip? = null,
    val events: List<ImpairmentEvent> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DriverDetailViewModel @Inject constructor(
    private val firestore: FirestoreDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverDetailUiState())
    val uiState: StateFlow<DriverDetailUiState> = _uiState.asStateFlow()

    fun load(driverId: String, tripId: String) {
        viewModelScope.launch {
            val driver = firestore.getUser(driverId)
            _uiState.value = _uiState.value.copy(driver = driver)

            // Observe events for this trip
            firestore.observeEventsForTrip(tripId).collect { events ->
                _uiState.value = _uiState.value.copy(
                    events = events,
                    isLoading = false
                )
            }
        }

        // Observe trips to get live location + status
        viewModelScope.launch {
            firestore.observeActiveTrip(driverId).collect { trip ->
                _uiState.value = _uiState.value.copy(trip = trip)
            }
        }
    }
}
