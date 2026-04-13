package com.vigilex.feature.superadmin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.TripStatus
import com.vigilex.core.model.User
import com.vigilex.ui.components.StatusBadge
import com.vigilex.ui.components.StatusType
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid
import javax.inject.Inject

@Composable
fun CompanyDetailScreen(
    companyId: String,
    onBack: () -> Unit,
    viewModel: SuperAdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val company = uiState.companies.find { it.id == companyId }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = { Text(company?.companyName ?: "Company", color = Amber) },
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
                        Text("Owner UID: ${company?.ownerUid ?: "—"}", color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                        Text("Drivers: ${company?.driverCount ?: 0}", color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Text("Active Trips", color = Amber, style = MaterialTheme.typography.titleSmall)
            }

            // Note: In production, observe trips filtered by companyId
            item {
                Text("Trip data visible in Owner Dashboard and Trip History.", color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
