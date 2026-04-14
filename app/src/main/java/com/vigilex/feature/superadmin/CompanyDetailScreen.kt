package com.vigilex.feature.superadmin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.vigilex.ui.theme.NavyMid

@Composable
fun CompanyDetailScreen(
    companyId: String,
    onBack: () -> Unit,
    viewModel: SuperAdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val owner = uiState.owners.find { it.companyId == companyId }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = { Text(owner?.name ?: "Owner Details", color = Amber) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Amber) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = NavyMid), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Name: ${owner?.name ?: "—"}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        if (owner?.email?.isNotBlank() == true) {
                            Text("Email: ${owner.email}", color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Phone: ${owner?.phone ?: "—"}", color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                        Text("Company ID: $companyId", color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Text("Active Trips", color = Amber, style = MaterialTheme.typography.titleSmall)
            }

            item {
                Text("Trip data visible in Owner Dashboard and Trip History.", color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
