package com.vigilex.feature.superadmin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var ownerName by remember { mutableStateOf("") }
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
                title = { Text("Add Company", color = Amber) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Amber) } },
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
            Text("Company Details", color = Amber, style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = companyName, onValueChange = { companyName = it }, label = { Text("Company Name") }, modifier = Modifier.fillMaxWidth())

            Text("Owner Details", color = Amber, style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, label = { Text("Owner Full Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = ownerPhone, onValueChange = { ownerPhone = it }, label = { Text("Owner Phone") }, modifier = Modifier.fillMaxWidth())

            if (uiState.error != null) {
                Text(uiState.error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { viewModel.addCompany(companyName, ownerName, ownerPhone) },
                enabled = !uiState.isLoading && companyName.isNotBlank() && ownerName.isNotBlank() && ownerPhone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Amber),
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
