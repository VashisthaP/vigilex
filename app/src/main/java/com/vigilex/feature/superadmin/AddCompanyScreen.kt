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

@Composable
fun AddCompanyScreen(
    onBack: () -> Unit,
    viewModel: SuperAdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var companyName by remember { mutableStateOf("") }
    var ownerName   by remember { mutableStateOf("") }
    var ownerPhone  by remember { mutableStateOf("") }
    var ownerPin    by remember { mutableStateOf("") }

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
                title = { Text("Add Company", color = Amber) },
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
            // ── Company section ───────────────────────────────────────────
            Text("Company Details", color = Amber, style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value         = companyName,
                onValueChange = { companyName = it },
                label         = { Text("Company Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // ── Owner section ─────────────────────────────────────────────
            Text("Owner Details", color = Amber, style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value         = ownerName,
                onValueChange = { ownerName = it },
                label         = { Text("Owner Full Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
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

            OutlinedTextField(
                value           = ownerPin,
                onValueChange   = { if (it.length <= 6 && it.all { c -> c.isDigit() }) ownerPin = it },
                label           = { Text("6-Digit Access PIN") },
                placeholder     = { Text("e.g. 456789") },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier        = Modifier.fillMaxWidth()
            )

            Text(
                "Share this PIN with the owner. They use it along with their phone OTP to access their account.",
                color = Color.White.copy(0.4f),
                style = MaterialTheme.typography.bodySmall
            )

            if (uiState.error != null) {
                Text(uiState.error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }

            val allFilled = companyName.isNotBlank() && ownerName.isNotBlank() &&
                            ownerPhone.isNotBlank() && ownerPin.length == 6

            Button(
                onClick  = { viewModel.addCompany(companyName, ownerName, ownerPhone, ownerPin) },
                enabled  = !uiState.isLoading && allFilled,
                colors   = ButtonDefaults.buttonColors(containerColor = Amber),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (uiState.isLoading) "Creating..." else "Create Company & Owner",
                    color = NavyDark
                )
            }
        }
    }
}
