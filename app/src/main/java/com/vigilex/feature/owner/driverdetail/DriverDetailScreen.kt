package com.vigilex.feature.owner.driverdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.vigilex.core.model.ImpairmentEvent
import com.vigilex.core.model.ImpairmentSubtype
import com.vigilex.core.model.Severity
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriverDetailScreen(
    driverId: String,
    tripId: String,
    onBack: () -> Unit,
    viewModel: DriverDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(driverId, tripId) { viewModel.load(driverId, tripId) }

    val location = uiState.trip?.lastLocation
    val cameraState = rememberCameraPositionState {
        if (location != null) {
            position = CameraPosition.fromLatLngZoom(LatLng(location.lat, location.lng), 14f)
        }
    }

    // Keep map camera centred on driver's live location
    LaunchedEffect(location) {
        if (location != null) {
            cameraState.position = CameraPosition.fromLatLngZoom(
                LatLng(location.lat, location.lng), 14f
            )
        }
    }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = { Text(uiState.driver?.name ?: "Driver", color = Amber) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Amber)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live map (auto-refreshes via state)
            item {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    cameraPositionState = cameraState,
                    properties = MapProperties(isMyLocationEnabled = false)
                ) {
                    if (location != null) {
                        Marker(
                            state = MarkerState(LatLng(location.lat, location.lng)),
                            title = uiState.driver?.name ?: "Driver"
                        )
                    }
                    // Draw route polyline from events
                    val points = uiState.events
                        .filter { it.lat != 0.0 && it.lng != 0.0 }
                        .map { LatLng(it.lat, it.lng) }
                    if (points.size >= 2) {
                        Polyline(points = points, color = Amber)
                    }
                }
            }

            // Trip stats
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatChip("Impairment Alerts", uiState.trip?.drowsyEventCount.toString(), Modifier.weight(1f))
                    StatChip("Exit Attempts", uiState.trip?.closeAttemptCount.toString(), Modifier.weight(1f))
                }
            }

            // Event timeline header
            item {
                Text(
                    text = "Event Timeline",
                    color = Amber,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (uiState.events.isEmpty()) {
                item {
                    Text(
                        "No events yet.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(uiState.events.sortedByDescending { it.timestamp }) { event ->
                    EventTimelineRow(event = event, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NavyMid),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Amber, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EventTimelineRow(event: ImpairmentEvent, modifier: Modifier = Modifier) {
    val severityColor = when (event.severity) {
        Severity.HIGH -> Color.Red
        Severity.MEDIUM -> Color(0xFFFF9800)
        Severity.LOW -> Color(0xFF4CAF50)
    }
    val subtypeLabel = when (event.subtype) {
        ImpairmentSubtype.EYE_CLOSURE -> "Eye Closure"
        ImpairmentSubtype.HEAD_DROP -> "Head Drop"
        ImpairmentSubtype.ERRATIC_MOTION -> "Erratic Motion (possible intoxication)"
        ImpairmentSubtype.COMBINED -> "Combined Signal"
    }
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NavyMid, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = severityColor,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(subtypeLabel, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text(
                sdf.format(Date(event.timestamp)),
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Surface(color = severityColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
            Text(
                event.severity.name,
                color = severityColor,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
