package com.vigilex.feature.owner.drivers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.core.model.Trip
import com.vigilex.core.model.User
import com.vigilex.ui.components.PlacesAutocompleteField
import com.vigilex.ui.components.PlaceResult
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid

@Composable
fun DriversManagementScreen(
    onBack: () -> Unit,
    viewModel: DriversViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog    by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) viewModel.clearMessages()
    }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Drivers", color = Amber) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Amber) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Amber) {
                Icon(Icons.Default.Add, null, tint = NavyDark)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.drivers) { driver ->
                DriverRow(
                    driver      = driver,
                    activeTrip  = uiState.activeTrips[driver.uid],
                    onAssignTrip = { showAssignDialog = driver }
                )
            }
        }
    }

    if (showAddDialog) {
        AddDriverDialog(
            isLoading = uiState.isLoading,
            error     = uiState.errorMessage,
            onConfirm = { name, phone, pin ->
                viewModel.addDriver(name, phone, pin)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false; viewModel.clearMessages() }
        )
    }

    showAssignDialog?.let { driver ->
        AssignTripDialog(
            driverName = driver.name,
            onConfirm  = { name, lat, lng ->
                viewModel.assignTrip(driver.uid, name, lat, lng)
                showAssignDialog = null
            },
            onDismiss  = { showAssignDialog = null }
        )
    }
}

// ── Driver row ────────────────────────────────────────────────────────────────

@Composable
private fun DriverRow(driver: User, activeTrip: Trip?, onAssignTrip: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(driver.name,  color = Color.White,            style = MaterialTheme.typography.bodyMedium)
                Text(driver.phone, color = Color.White.copy(0.45f), style = MaterialTheme.typography.bodySmall)
                if (activeTrip != null) {
                    Text(
                        "On trip → ${activeTrip.destination.name}",
                        color = Amber.copy(0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (activeTrip == null) {
                TextButton(onClick = onAssignTrip) {
                    Text("Assign Trip", color = Amber, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                // Driver is already on a trip — show status chip instead
                Surface(
                    color = Amber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Active",
                        color = Amber,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ── Add driver dialog — name + phone + owner-set exit PIN ────────────────────

@Composable
private fun AddDriverDialog(
    isLoading: Boolean,
    error:     String?,
    onConfirm: (name: String, phone: String, pin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name  by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin   by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A2A3A),
        title = { Text("Add Driver", color = Amber) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Full Name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value           = phone,
                    onValueChange   = { if (it.length <= 13) phone = it },
                    label           = { Text("Mobile Number") },
                    placeholder     = { Text("+91XXXXXXXXXX") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier        = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value           = pin,
                    onValueChange   = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label           = { Text("6-Digit Exit PIN") },
                    placeholder     = { Text("e.g. 123456") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier        = Modifier.fillMaxWidth()
                )
                Text(
                    "Share this PIN with the driver. They use it to unlock the monitoring screen.",
                    color = Color.White.copy(0.45f),
                    style = MaterialTheme.typography.bodySmall
                )
                if (error != null) Text(error, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(name, phone, pin) },
                enabled  = !isLoading && name.isNotBlank() && phone.isNotBlank() && pin.length == 6,
                colors   = ButtonDefaults.buttonColors(containerColor = Amber)
            ) { Text(if (isLoading) "Adding..." else "Add Driver", color = NavyDark) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Assign trip dialog — Google Places autocomplete ──────────────────────────

@Composable
private fun AssignTripDialog(
    driverName: String,
    onConfirm:  (name: String, lat: Double, lng: Double) -> Unit,
    onDismiss:  () -> Unit
) {
    var selectedPlace by remember { mutableStateOf<PlaceResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A2A3A),
        title = { Text("Assign Trip — $driverName", color = Amber) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Search for the driver's destination using Google Maps.",
                    color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
                PlacesAutocompleteField(
                    label           = "Search destination",
                    modifier        = Modifier.fillMaxWidth(),
                    onPlaceSelected = { selectedPlace = it }
                )
                if (selectedPlace != null) {
                    Text(
                        "📍 ${selectedPlace!!.address}",
                        color = Amber.copy(0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    selectedPlace?.let { onConfirm(it.name, it.latLng.latitude, it.latLng.longitude) }
                },
                enabled  = selectedPlace != null,
                colors   = ButtonDefaults.buttonColors(containerColor = Amber)
            ) { Text("Assign", color = NavyDark) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
