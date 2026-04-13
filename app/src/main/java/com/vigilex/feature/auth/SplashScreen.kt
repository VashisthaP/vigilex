package com.vigilex.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.core.model.Role
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark

/**
 * Entry point shown every time the app launches.
 * Waits for AuthViewModel to resolve the current session, then routes:
 *  - Existing session → role-based dashboard (skips Login entirely)
 *  - No session / error → Login screen
 */
@Composable
fun SplashScreen(
    onNavigateTo: (Role) -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LoginUiState.Success  -> onNavigateTo(state.role)
            is LoginUiState.NoSession,
            is LoginUiState.OtpSent,
            is LoginUiState.Error    -> onNavigateToLogin()
            is LoginUiState.Loading  -> { /* wait for resolution */ }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "VigileX",
                color = Amber,
                fontSize = 40.sp,
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                text = "Driver Safety Monitor",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }
    }
}
