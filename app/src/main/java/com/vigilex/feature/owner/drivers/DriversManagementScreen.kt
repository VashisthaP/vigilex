package com.vigilex.feature.owner.drivers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.core.model.User
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid

@Composable
fun DriversManagementScreen(
    onBack: () -> Unit,
    viewModel: DriversViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) viewModel.clearMessages()
    }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Drivers", color = Amber) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Amber) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Amber
            ) { Icon(Icons.Default.Add, null, tint = NavyDark) }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.drivers) { driver ->
                DriverRow(
                    driver = driver,
                    onAssignTrip = { showAssignDialog = driver }
                )
            }
        }
    }

    if (showAddDialog) {
        AddDriverDialog(
            isLoading = uiState.isLoading,
            error = uiState.errorMessage,
            onConfirm = { name, phone ->
                viewModel.addDriver(name, phone)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    showAssignDialog?.let { driver ->
        AssignTripDialog(
            driverName = driver.name,
            onConfirm = { name, lat, lng ->
                viewModel.assignTrip(driver.uid, name, lat, lng)
                showAssignDialog = null
            },
            onDismiss = { showAssignDialog = null }
        )
    }
}

@Composable
private fun DriverRow(driver: User, onAssignTrip: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyMid),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(driver.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text(driver.email, color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                Text(driver.phone, color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAssignTrip) {
                Text("Assign Trip", color = Amber, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AddDriverDialog(
    isLoading: Boolean,
    error: String?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Driver", color = Amber) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                if (error != null) Text(error, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, phone) },
                enabled = !isLoading && name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Amber)
            ) { Text(if (isLoading) "Adding..." else "Add", color = NavyDark) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Color(0xFF1A2A3A)
    )
}

@Composable
private fun AssignTripDialog(
    driverName: String,
    onConfirm: (String, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    // Simple text input for destination — in production wire up Places Autocomplete SDK here
    var destination by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Trip — $driverName", color = Amber) },
        text = {
            Column {
                Text(
                    "Enter destination. In the production build, wire the Places Autocomplete SDK to this field for lat/lng resolution.",
                    color = Color.White.copy(0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Lat/lng would come from Places SDK selection — using 0.0 as placeholder
                    onConfirm(destination, 0.0, 0.0)
                },
                enabled = destination.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Amber)
            ) { Text("Assign", color = NavyDark) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Color(0xFF1A2A3A)
    )
}
