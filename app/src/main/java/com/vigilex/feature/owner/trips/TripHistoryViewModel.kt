package com.vigilex.feature.owner.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.ImpairmentEvent
import com.vigilex.core.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirestoreDataSource
) : ViewModel() {

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _selectedTripEvents = MutableStateFlow<List<ImpairmentEvent>>(emptyList())
    val selectedTripEvents: StateFlow<List<ImpairmentEvent>> = _selectedTripEvents.asStateFlow()

    private val _isLoadingEvents = MutableStateFlow(false)
    val isLoadingEvents: StateFlow<Boolean> = _isLoadingEvents.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            firestore.observeTripsForOwner(uid).collect { _trips.value = it }
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch { runCatching { firestore.deleteTrip(tripId) } }
    }

    fun loadEventsForTrip(tripId: String) {
        _isLoadingEvents.value = true
        _selectedTripEvents.value = emptyList()
        viewModelScope.launch {
            firestore.observeEventsForTrip(tripId).collect {
                _selectedTripEvents.value = it
                _isLoadingEvents.value = false
            }
        }
    }
}
