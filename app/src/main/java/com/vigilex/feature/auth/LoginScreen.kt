package com.vigilex.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.core.model.Role
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onLoginSuccess: (Role) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as ComponentActivity

    // Navigate as soon as login succeeds
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess((uiState as LoginUiState.Success).role)
        }
    }

    Box(
        modifier         = Modifier.fillMaxSize().background(NavyDark),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = uiState is LoginUiState.OtpSent,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            },
            label = "login_step"
        ) { isOtpStep ->
            if (isOtpStep) {
                OtpPanel(
                    phone     = (uiState as? LoginUiState.OtpSent)?.formattedPhone ?: "",
                    isLoading = uiState is LoginUiState.Loading,
                    error     = (uiState as? LoginUiState.Error)?.message,
                    onVerify  = { viewModel.verifyOtp(it) },
                    onResend  = { viewModel.resendOtp(activity) },
                    onBack    = { viewModel.signOut() }
                )
            } else {
                PhonePanel(
                    isLoading = uiState is LoginUiState.Loading,
                    error     = (uiState as? LoginUiState.Error)?.message,
                    onSendOtp = { viewModel.sendOtp(it, activity) },
                    onClearError = { viewModel.clearError() }
                )
            }
        }
    }
}

// ── Step 1: Phone number input ────────────────────────────────────────────────

@Composable
private fun PhonePanel(
    isLoading:    Boolean,
    error:        String?,
    onSendOtp:    (String) -> Unit,
    onClearError: () -> Unit
) {
    var phone by remember { mutableStateOf("") }

    Column(
        modifier                = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment     = Alignment.CenterHorizontally,
        verticalArrangement     = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text("VigileX", color = Amber, fontSize = 40.sp, style = MaterialTheme.typography.displayMedium)
        Text(
            "Driver Safety Monitor",
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))

        // Phone field with +91 prefix card
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyMid),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("+91 ", color = Amber, style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value         = phone,
                    onValueChange = {
                        if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                            phone = it
                            if (error != null) onClearError()
                        }
                    },
                    placeholder   = { Text("10-digit mobile number", color = Color.White.copy(0.4f)) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = Amber
                    ),
                    modifier      = Modifier.weight(1f)
                )
            }
        }

        AnimatedVisibility(visible = error != null) {
            Text(
                text      = error ?: "",
                color     = Color(0xFFFF7043),
                style     = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick  = { onSendOtp(phone) },
            enabled  = !isLoading && phone.length == 10,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Amber)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
            } else {
                Text("Send OTP", color = NavyDark, style = MaterialTheme.typography.labelLarge)
            }
        }

        Text(
            "OTP will be sent via SMS to your registered mobile number.",
            color     = Color.White.copy(0.3f),
            style     = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

// ── Step 2: OTP code input ────────────────────────────────────────────────────

@Composable
private fun OtpPanel(
    phone:     String,
    isLoading: Boolean,
    error:     String?,
    onVerify:  (String) -> Unit,
    onResend:  () -> Unit,
    onBack:    () -> Unit
) {
    var otp         by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableIntStateOf(60) }

    // Countdown for resend button
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) { delay(1_000L); secondsLeft-- }
    }

    Column(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text("VigileX", color = Amber, fontSize = 40.sp, style = MaterialTheme.typography.displayMedium)

        Text(
            "OTP sent to\n$phone",
            color     = Color.White.copy(0.65f),
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        // 6-box OTP display
        OtpBoxRow(
            value     = otp,
            onChange  = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otp = it }
        )

        AnimatedVisibility(visible = error != null) {
            Text(
                text      = error ?: "",
                color     = Color(0xFFFF7043),
                style     = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick  = { onVerify(otp) },
            enabled  = !isLoading && otp.length == 6,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Amber)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
            } else {
                Text("Verify OTP", color = NavyDark, style = MaterialTheme.typography.labelLarge)
            }
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("← Change Number", color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick  = { if (secondsLeft == 0) { secondsLeft = 60; onResend() } },
                enabled  = secondsLeft == 0
            ) {
                Text(
                    if (secondsLeft > 0) "Resend in ${secondsLeft}s" else "Resend OTP",
                    color = if (secondsLeft == 0) Amber else Color.White.copy(0.35f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ── 6-box OTP input widget ────────────────────────────────────────────────────
// Uses BasicTextField (not OutlinedTextField) so Samsung's IME treats it as a
// proper editable field — fixes "ssi() view is not EditText" keyboard dismissal.

@Composable
private fun OtpBoxRow(value: String, onChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    // Request focus shortly after composition so the keyboard opens automatically
    LaunchedEffect(Unit) {
        delay(120L)
        runCatching { focusRequester.requestFocus() }
    }

    BasicTextField(
        value           = value,
        onValueChange   = { raw ->
            // Accept only digits, max 6 chars
            val filtered = raw.filter { it.isDigit() }.take(6)
            onChange(filtered)
        },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush     = SolidColor(Color.Transparent),   // cursor hidden — boxes show position
        modifier        = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox   = { _ ->
            // Draw the 6 digit boxes; tap any box to (re)open keyboard
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { runCatching { focusRequester.requestFocus() } },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(6) { idx ->
                    val char     = value.getOrNull(idx)?.toString() ?: ""
                    val isFilled = char.isNotEmpty()
                    val isActive = idx == value.length   // cursor position
                    Card(
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = when {
                                isFilled -> Amber.copy(alpha = 0.15f)
                                isActive -> NavyMid
                                else     -> NavyMid
                            }
                        ),
                        border   = androidx.compose.foundation.BorderStroke(
                            width = if (isActive && !isFilled) 2.dp else 1.dp,
                            color = when {
                                isFilled -> Amber
                                isActive -> Amber.copy(alpha = 0.6f)
                                else     -> Color.White.copy(alpha = 0.2f)
                            }
                        )
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text  = char,
                                color = Amber,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }
        }
    )
}
