package com.vigilex.feature.owner.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.core.model.TripStatus
import com.vigilex.ui.components.StatusBadge
import com.vigilex.ui.components.StatusType
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripHistoryScreen(
    onTripClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TripHistoryViewModel = hiltViewModel()
) {
    val trips by viewModel.trips.collectAsState()
    var filter by remember { mutableStateOf<TripStatus?>(null) }

    val filtered = if (filter == null) trips else trips.filter { it.status == filter }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Trip History", color = Amber) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Amber)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("All", filter == null) { filter = null }
                FilterChip("Active", filter == TripStatus.ACTIVE) { filter = TripStatus.ACTIVE }
                FilterChip("Complete", filter == TripStatus.COMPLETE) { filter = TripStatus.COMPLETE }
                FilterChip("High Risk", filter == TripStatus.HIGH_RISK) { filter = TripStatus.HIGH_RISK }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered.sortedByDescending { it.startTime }) { trip ->
                    TripRow(trip = trip, onClick = { onTripClick(trip.id) })
                }
            }
        }
    }
}

@Composable
private fun TripRow(trip: com.vigilex.core.model.Trip, onClick: () -> Unit) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val statusType = when (trip.status) {
        TripStatus.ACTIVE -> StatusType.ACTIVE
        TripStatus.HIGH_RISK -> StatusType.ALERT
        TripStatus.COMPLETE -> StatusType.COMPLETE
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NavyMid),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(trip.destination.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text(
                    sdf.format(Date(trip.startTime)),
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Alerts: ${trip.drowsyEventCount}  ·  Exits: ${trip.closeAttemptCount}",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            StatusBadge(type = statusType)
        }
    }
}

@Composable
fun TripDetailScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: TripHistoryViewModel = hiltViewModel()
) {
    val trips  by viewModel.trips.collectAsState()
    val events by viewModel.selectedTripEvents.collectAsState()
    val isLoading by viewModel.isLoadingEvents.collectAsState()

    LaunchedEffect(tripId) { viewModel.loadEventsForTrip(tripId) }

    val trip = trips.firstOrNull { it.id == tripId }
    val sdf  = SimpleDateFormat("HH:mm:ss dd MMM", Locale.getDefault())

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = { Text(trip?.destination?.name?.let { "Trip — $it" } ?: "Trip Detail", color = Amber) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Amber) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            // Trip summary card
            if (trip != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyMid),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Destination: ${trip.destination.name}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Text("Started: ${sdf.format(Date(trip.startTime))}", color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                        Text("Impairment alerts: ${trip.drowsyEventCount}  ·  Exit attempts: ${trip.closeAttemptCount}",
                            color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                        StatusBadge(type = when (trip.status) {
                            com.vigilex.core.model.TripStatus.ACTIVE    -> StatusType.ACTIVE
                            com.vigilex.core.model.TripStatus.HIGH_RISK -> StatusType.ALERT
                            com.vigilex.core.model.TripStatus.COMPLETE  -> StatusType.COMPLETE
                        })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Event Timeline", color = Amber, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp))
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
                events.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No events recorded for this trip.", color = Color.White.copy(0.4f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Events appear once the driver's monitoring session is active.",
                            color = Color.White.copy(0.25f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events.sortedByDescending { it.timestamp }) { event ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyMid, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(event.type.name, color = Amber, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            Text(event.subtype.name.replace("_", " "), color = Color.White.copy(0.6f),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
                            Text(sdf.format(Date(event.timestamp)), color = Color.White.copy(0.4f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Amber else NavyMid,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) NavyDark else Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
