package com.vigilex.feature.owner.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.vigilex.core.model.TripStatus
import com.vigilex.ui.components.StatusBadge
import com.vigilex.ui.components.StatusType
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid

private enum class DashboardTab { MAP, CARDS }

@Composable
fun OwnerDashboardScreen(
    onDriverClick:        (driverId: String, tripId: String) -> Unit,
    onNavigateToHistory:  () -> Unit,
    onNavigateToDrivers:  () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: OwnerDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var activeTab by remember { mutableStateOf(DashboardTab.MAP) }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = uiState.ownerUser?.let { "${it.name} — Dashboard" } ?: "Dashboard",
                        color = Amber
                    )
                },
                actions = {
                    // Toggle between Map and Cards
                    IconButton(onClick = {
                        activeTab = if (activeTab == DashboardTab.MAP) DashboardTab.CARDS else DashboardTab.MAP
                    }) {
                        Icon(
                            imageVector        = if (activeTab == DashboardTab.MAP) Icons.Default.List else Icons.Default.Map,
                            contentDescription = "Toggle view",
                            tint               = Amber
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = Amber)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = NavyMid) {
                NavigationBarItem(
                    selected = true,
                    onClick  = {},
                    icon     = { Icon(Icons.Default.Person, null) },
                    label    = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onNavigateToHistory,
                    icon     = { Icon(Icons.Default.List, null) },
                    label    = { Text("Trip History") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onNavigateToDrivers,
                    icon     = { Icon(Icons.Default.Person, null) },
                    label    = { Text("Drivers") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onNavigateToSettings,
                    icon     = { Icon(Icons.Default.Settings, null) },
                    label    = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else {
            when (activeTab) {
                DashboardTab.MAP   -> DriverMapView(
                    driverCards   = uiState.driverCards,
                    onDriverClick = onDriverClick,
                    modifier      = Modifier.fillMaxSize().padding(padding)
                )
                DashboardTab.CARDS -> DriverCardsView(
                    driverCards   = uiState.driverCards,
                    onDriverClick = onDriverClick,
                    modifier      = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}

// ── Live map showing all active driver locations ──────────────────────────────

@Composable
private fun DriverMapView(
    driverCards:   List<DriverCardData>,
    onDriverClick: (driverId: String, tripId: String) -> Unit,
    modifier:      Modifier = Modifier
) {
    // Find a sensible initial camera position (first active driver or India centre)
    val firstLocation = driverCards
        .mapNotNull { it.activeTrip?.lastLocation }
        .firstOrNull()

    val initialPosition = firstLocation
        ?.let { LatLng(it.lat, it.lng) }
        ?: LatLng(20.5937, 78.9629)   // geographic centre of India

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition, if (firstLocation != null) 14f else 5f)
    }

    // Auto-pan to first active driver when data loads
    LaunchedEffect(driverCards) {
        val loc = driverCards.mapNotNull { it.activeTrip?.lastLocation }.firstOrNull()
        if (loc != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lng), 13f)
            )
        }
    }

    GoogleMap(
        modifier           = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings         = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false),
        properties         = MapProperties(mapType = MapType.NORMAL)
    ) {
        driverCards.forEach { card ->
            val location = card.activeTrip?.lastLocation ?: return@forEach
            val tripId   = card.activeTrip.id
            val isAlert  = card.activeTrip.status == TripStatus.HIGH_RISK

            // Driver position marker
            Marker(
                state   = rememberMarkerState(
                    key      = "${card.driver.uid}_loc",
                    position = LatLng(location.lat, location.lng)
                ),
                title   = card.driver.name,
                snippet = buildString {
                    val kmh = (location.speed * 3.6f).toInt()
                    append("$kmh km/h")
                    if (isAlert) append(" ⚠ HIGH RISK")
                },
                icon    = BitmapDescriptorFactory.defaultMarker(
                    if (isAlert) BitmapDescriptorFactory.HUE_RED
                    else BitmapDescriptorFactory.HUE_GREEN
                ),
                onClick = {
                    onDriverClick(card.driver.uid, tripId)
                    true   // consume click — prevent default info window auto-close
                }
            )

            // Destination marker (flag)
            val dest = card.activeTrip.destination
            if (dest.lat != 0.0 || dest.lng != 0.0) {
                Marker(
                    state   = rememberMarkerState(
                        key      = "${card.driver.uid}_dest",
                        position = LatLng(dest.lat, dest.lng)
                    ),
                    title   = "Destination: ${dest.name}",
                    icon    = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
        }
    }
}

// ── Card grid (original view) ─────────────────────────────────────────────────

@Composable
private fun DriverCardsView(
    driverCards:   List<DriverCardData>,
    onDriverClick: (driverId: String, tripId: String) -> Unit,
    modifier:      Modifier = Modifier
) {
    if (driverCards.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No drivers assigned yet.", color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyVerticalGrid(
        columns             = GridCells.Fixed(2),
        modifier            = modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(driverCards) { card ->
            DriverCard(card = card, onClick = {
                val tripId = card.activeTrip?.id ?: return@DriverCard
                onDriverClick(card.driver.uid, tripId)
            })
        }
    }
}

@Composable
private fun DriverCard(card: DriverCardData, onClick: () -> Unit) {
    val statusType = when (card.activeTrip?.status) {
        TripStatus.ACTIVE    -> StatusType.ACTIVE
        TripStatus.HIGH_RISK -> StatusType.ALERT
        TripStatus.COMPLETE  -> StatusType.COMPLETE
        null                 -> StatusType.INACTIVE
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NavyMid)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = card.driver.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
            StatusBadge(type = statusType)

            if (card.activeTrip != null) {
                val elapsed = System.currentTimeMillis() - card.activeTrip.startTime
                val hours   = elapsed / 3_600_000
                val minutes = (elapsed % 3_600_000) / 60_000
                Text(
                    text  = "%dh %02dm".format(hours, minutes),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
                val speedKmh = ((card.activeTrip.lastLocation?.speed ?: 0f) * 3.6f).toInt()
                Text(
                    text  = "$speedKmh km/h",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("No active trip", color = MaterialTheme.colorScheme.onSurface.copy(0.3f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
