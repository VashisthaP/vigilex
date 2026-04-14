package com.vigilex.feature.superadmin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark

/**
 * Super Admin screen to authorize a new Owner.
 * Only owners added here can log in via OTP.
 * A company is auto-created for the owner.
 */
@Composable
fun AddCompanyScreen(
    onBack: () -> Unit,
    viewModel: SuperAdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var ownerName  by remember { mutableStateOf("") }
    var ownerEmail by remember { mutableStateOf("") }
    var ownerPhone by remember { mutableStateOf("") }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            viewModel.clearMessages()
            onBack()
        }
    }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = { Text("Add Owner", color = Amber) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Amber) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Owner Details", color = Amber, style = MaterialTheme.typography.titleSmall)

            Text(
                "Only owners authorized here can log in to VigileX via OTP. " +
                "Unauthorized phone numbers will be blocked.",
                color = Color.White.copy(0.5f),
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value         = ownerName,
                onValueChange = { ownerName = it },
                label         = { Text("Owner Full Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value           = ownerEmail,
                onValueChange   = { ownerEmail = it },
                label           = { Text("Owner Email (optional)") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier        = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value           = ownerPhone,
                onValueChange   = { if (it.length <= 13) ownerPhone = it },
                label           = { Text("Owner Mobile Number") },
                placeholder     = { Text("+91XXXXXXXXXX") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier        = Modifier.fillMaxWidth()
            )

            Text(
                "The owner will use this phone number to receive OTP and log in. " +
                "They can then add drivers from their dashboard.",
                color = Color.White.copy(0.4f),
                style = MaterialTheme.typography.bodySmall
            )

            if (uiState.error != null) {
                Text(uiState.error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }

            val allFilled = ownerName.isNotBlank() && ownerPhone.isNotBlank()

            Button(
                onClick  = { viewModel.addOwner(ownerName, ownerEmail, ownerPhone) },
                enabled  = !uiState.isLoading && allFilled,
                colors   = ButtonDefaults.buttonColors(containerColor = Amber),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (uiState.isLoading) "Authorizing..." else "Authorize Owner",
                    color = NavyDark
                )
            }
        }
    }
}
