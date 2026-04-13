package com.vigilex.feature.driver

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.R
import com.vigilex.feature.driver.service.MonitoringStatus
import com.vigilex.ui.theme.Amber
import com.vigilex.ui.theme.NavyDark
import com.vigilex.ui.theme.NavyMid

// ── Setup step machine ────────────────────────────────────────────────────────
private enum class SetupStep { PERMISSIONS, BLUETOOTH, MONITORING }

@Composable
fun DriverHomeScreen(
    onSignOut: () -> Unit,
    viewModel: DriverViewModel = hiltViewModel()
) {
    var setupStep by remember { mutableStateOf(SetupStep.PERMISSIONS) }

    when (setupStep) {
        SetupStep.PERMISSIONS -> PermissionsStep(
            onPermissionsGranted = { setupStep = SetupStep.BLUETOOTH }
        )
        SetupStep.BLUETOOTH   -> BluetoothStep(
            onContinue = { setupStep = SetupStep.MONITORING }
        )
        SetupStep.MONITORING  -> MonitoringScreen(
            viewModel = viewModel,
            onSignOut = onSignOut
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — Permission Requests
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionsStep(onPermissionsGranted: () -> Unit) {
    val context = LocalContext.current

    val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ runtime BT permissions
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        // Pre-Android 12 BLUETOOTH is a normal permission — no runtime request needed
    }

    fun allGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var permissionsGranted by remember { mutableStateOf(allGranted()) }
    var deniedPermissions  by remember { mutableStateOf<List<String>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys.toList()
        deniedPermissions  = denied
        permissionsGranted = denied.isEmpty()
    }

    // Auto-advance if already granted (re-install / re-launch)
    LaunchedEffect(Unit) {
        if (allGranted()) onPermissionsGranted()
        else launcher.launch(requiredPermissions.toTypedArray())
    }

    if (permissionsGranted) {
        LaunchedEffect(Unit) { onPermissionsGranted() }
        return
    }

    Box(
        modifier            = Modifier.fillMaxSize().background(NavyDark),
        contentAlignment    = Alignment.Center
    ) {
        Column(
            modifier                = Modifier.padding(32.dp),
            horizontalAlignment     = Alignment.CenterHorizontally,
            verticalArrangement     = Arrangement.spacedBy(20.dp)
        ) {
            Text("VigileX", color = Amber, fontSize = 32.sp, style = MaterialTheme.typography.displaySmall)
            Text(
                "VigileX needs the following permissions to monitor driver safety.",
                color     = Color.White.copy(0.7f),
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            PermissionRow("📷  Camera",          "Detects eye closure and head position")
            PermissionRow("📍  Location",         "Tracks trip route and triggers geofence arrival")
            PermissionRow("🔔  Notifications",    "Delivers impairment alerts")
            PermissionRow("🎵  Bluetooth",        "Routes safety alarms through vehicle speakers")

            if (deniedPermissions.isNotEmpty()) {
                Text(
                    "Some permissions were denied. Please grant them in Settings → App → VigileX.",
                    color     = Color(0xFFFF7043),
                    style     = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick  = { launcher.launch(requiredPermissions.toTypedArray()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber)
            ) {
                Text(
                    if (deniedPermissions.isEmpty()) "Grant Permissions" else "Retry",
                    color = NavyDark,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title,    color = Color.White,            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(subtitle, color = Color.White.copy(0.45f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — Bluetooth device picker
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BluetoothStep(onContinue: () -> Unit) {
    val context = LocalContext.current

    val bluetoothAdapter: BluetoothAdapter? = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    val bondedAudioDevices: List<BluetoothDevice> = remember(bluetoothAdapter) {
        if (bluetoothAdapter == null) return@remember emptyList()
        val canRead = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        if (!canRead) return@remember emptyList()
        @Suppress("MissingPermission")
        bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
    }

    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user enabled/rejected BT */ }

    Box(
        modifier         = Modifier.fillMaxSize().background(NavyDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier              = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint     = Amber,
                modifier = Modifier.size(56.dp)
            )

            Text(
                "Connect Vehicle Audio",
                color = Amber,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Select your vehicle's Bluetooth audio system so safety alarms play through the car speakers.",
                color     = Color.White.copy(0.65f),
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = Color.White.copy(0.1f))

            if (bluetoothAdapter == null) {
                Text("Bluetooth not available on this device.", color = Color(0xFFFF7043), textAlign = TextAlign.Center)
            } else if (bluetoothAdapter.isEnabled == false) {
                Button(
                    onClick  = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber)
                ) {
                    Text("Enable Bluetooth", color = NavyDark)
                }
            } else if (bondedAudioDevices.isEmpty()) {
                Text(
                    "No paired Bluetooth devices found.\nPair your vehicle audio first in phone Settings.",
                    color     = Color.White.copy(0.55f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bondedAudioDevices) { device ->
                        BluetoothDeviceRow(
                            device     = device,
                            isSelected = device == selectedDevice,
                            onSelect   = { selectedDevice = if (selectedDevice == device) null else device }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (selectedDevice != null) Amber else Color.White.copy(0.15f)
                )
            ) {
                Icon(
                    imageVector        = if (selectedDevice != null) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint               = if (selectedDevice != null) NavyDark else Color.White.copy(0.5f),
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (selectedDevice != null) "Start Monitoring" else "Skip — Use Phone Speaker",
                    color = if (selectedDevice != null) NavyDark else Color.White.copy(0.7f),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
@Suppress("MissingPermission")
private fun BluetoothDeviceRow(
    device: BluetoothDevice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val canRead = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            LocalContext.current, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    val deviceName = if (canRead) device.name ?: "Unknown Device" else "Unknown Device"
    val deviceAddress = device.address ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Amber.copy(alpha = 0.15f) else NavyMid
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = if (isSelected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint               = if (isSelected) Amber else Color.White.copy(0.5f),
                modifier           = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(deviceName,    color = Color.White,            style = MaterialTheme.typography.bodyMedium)
                Text(deviceAddress, color = Color.White.copy(0.35f), style = MaterialTheme.typography.bodySmall)
            }
            if (isSelected) {
                Icon(Icons.Default.Check, null, tint = Amber, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — Active Monitoring UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonitoringScreen(
    viewModel: DriverViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val view    = LocalView.current
    var showSignOutConfirm by remember { mutableStateOf(false) }

    // Keep screen on — brightness unchanged (use device setting)
    DisposableEffect(Unit) {
        val window = (view.context as? androidx.activity.ComponentActivity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Hardware back → ask for sign-out confirmation instead of OTP
    BackHandler(enabled = true) { showSignOutConfirm = true }

    val showAlert = uiState.monitoringStatus is MonitoringStatus.Alert

    Box(modifier = Modifier.fillMaxSize().background(NavyDark)) {

        // Full-screen red alert overlay
        AnimatedVisibility(visible = showAlert, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.35f)))
        }

        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Active trip destination (if assigned) — informational only
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                if (uiState.trip?.destination?.name?.isNotBlank() == true) {
                    Text(
                        "Destination",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = uiState.trip!!.destination.name,
                        color     = Amber,
                        fontSize  = 28.sp,
                        style     = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // No trip assigned yet — monitoring runs regardless
                    Text(
                        "Monitoring Active",
                        color = Amber,
                        fontSize  = 22.sp,
                        style     = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Status dot
            MonitoringStatusDot(status = uiState.monitoringStatus)

            // Speed + sign-out
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val speedKmh = ((uiState.trip?.lastLocation?.speed ?: 0f) * 3.6f).toInt()
                if (speedKmh > 0) {
                    Text(
                        "$speedKmh km/h",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick  = { showSignOutConfirm = true },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(
                        "Sign Out",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        // Sign-out confirmation dialog
        if (showSignOutConfirm) {
            AlertDialog(
                onDismissRequest = { showSignOutConfirm = false },
                containerColor   = Color(0xFF1A2A3A),
                title   = { Text("Sign Out?", color = Amber) },
                text    = {
                    Text(
                        "Are you sure you want to sign out? Monitoring will stop.",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.75f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onSignOut,
                        colors  = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.8f))
                    ) { Text("Sign Out", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutConfirm = false }) {
                        Text("Cancel", color = Amber)
                    }
                }
            )
        }

        // First-launch disclaimer
        if (uiState.showImpairmentDisclaimer) {
            AlertDialog(
                onDismissRequest = {},
                title    = { Text("Important Notice", color = Amber) },
                text     = {
                    Text(
                        text  = stringResource(R.string.disclaimer_impairment),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissDisclaimer() }) {
                        Text("Understood", color = Amber)
                    }
                },
                containerColor = Color(0xFF1A2A3A),
                properties     = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonitoringStatusDot(status: MonitoringStatus) {
    val (dotColor, label) = when (status) {
        is MonitoringStatus.Active         -> Color(0xFF4CAF50) to "Monitoring Active"
        is MonitoringStatus.Calibrating    -> Amber              to "Calibrating (${(status.progress * 100).toInt()}%)"
        is MonitoringStatus.Paused         -> Color(0xFFFF9800) to "Monitoring Paused (Stationary)"
        is MonitoringStatus.FaceNotDetected -> Color(0xFFFF9800) to "Face Not Detected"
        is MonitoringStatus.Alert          -> Color.Red          to "⚠ Impairment Detected"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(64.dp).scale(scale).background(dotColor, CircleShape))
        Spacer(Modifier.height(16.dp))
        Text(text = label, color = dotColor, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

