package com.vigilex.feature.owner.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark

@Composable
fun OwnerSettingsScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title            = { Text("Sign Out", color = Amber) },
            text             = { Text("Are you sure you want to sign out?", color = Color.White.copy(0.8f)) },
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

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title          = { Text("Settings", color = Amber) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Amber) } },
                colors         = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        }
    ) { padding ->
        Column(
            modifier          = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Profile", color = Amber, style = MaterialTheme.typography.titleSmall)
            Text("Name and contact info updates are handled by Super Admin.", color = Color.White.copy(0.6f))

            HorizontalDivider(color = Color.White.copy(0.1f))

            Text("Notifications", color = Amber, style = MaterialTheme.typography.titleSmall)
            Text("FCM push notifications are enabled by default.", color = Color.White.copy(0.6f))

            HorizontalDivider(color = Color.White.copy(0.1f))

            // ── Sign out ────────────────────────────────────────────────────
            Spacer(Modifier.weight(1f))
            Button(
                onClick  = { showSignOutDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text("Sign Out", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
