package com.vigilex.feature.superadmin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.core.model.User
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid

@Composable
fun SuperAdminDashboardScreen(
    onCompanyClick: (String) -> Unit,
    onAddCompany: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SuperAdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var ownerToDelete by remember { mutableStateOf<User?>(null) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title            = { Text("Sign Out", color = Amber) },
            text             = { Text("Sign out of Super Admin?", color = Color.White.copy(0.8f)) },
            confirmButton    = {
                TextButton(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Text("Sign Out", color = Color.Red)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = Amber)
                }
            },
            containerColor = Color(0xFF1A2A3A)
        )
    }

    // Delete owner confirmation dialog
    if (ownerToDelete != null) {
        AlertDialog(
            onDismissRequest = { ownerToDelete = null },
            title            = { Text("Remove Owner?", color = Color.Red) },
            text             = {
                Text(
                    "Remove ${ownerToDelete!!.name} (${ownerToDelete!!.phone})? " +
                    "They will no longer be able to log in.",
                    color = Color.White.copy(0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOwner(ownerToDelete!!.uid)
                    ownerToDelete = null
                }) { Text("Remove", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { ownerToDelete = null }) {
                    Text("Cancel", color = Amber)
                }
            },
            containerColor = Color(0xFF1A2A3A)
        )
    }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title   = { Text("VigileX — Super Admin", color = Amber) },
                actions = {
                    IconButton(onClick = { showSignOutDialog = true }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = Color.Red.copy(0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = onAddCompany,
                containerColor = Amber,
                contentColor   = NavyDark
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Owner")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlobalStatCard("Owners",       uiState.owners.size.toString(),     Modifier.weight(1f))
                    GlobalStatCard("Active Trips", uiState.activeTripsCount.toString(), Modifier.weight(1f))
                    GlobalStatCard("Total Alerts", uiState.totalAlerts.toString(),      Modifier.weight(1f))
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Authorized Owners",
                    color      = Amber,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.owners.isEmpty()) {
                item {
                    Text(
                        "No owners yet. Tap 'Add Owner' to authorize someone.",
                        color = Color.White.copy(0.4f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(uiState.owners) { owner ->
                OwnerCard(
                    owner    = owner,
                    onClick  = { onCompanyClick(owner.companyId) },
                    onDelete = { ownerToDelete = owner }
                )
            }
        }
    }
}

@Composable
private fun GlobalStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Amber,                  style = MaterialTheme.typography.headlineSmall)
            Text(label, color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OwnerCard(owner: User, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = NavyMid),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier            = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(owner.name.ifBlank { "Unnamed Owner" }, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                if (owner.email.isNotBlank()) {
                    Text(owner.email, color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                }
                Text(owner.phone, color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red.copy(0.7f))
            }
        }
    }
}
