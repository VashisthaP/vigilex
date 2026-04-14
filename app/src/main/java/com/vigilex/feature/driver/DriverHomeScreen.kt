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
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilex.R
import com.vigilex.feature.driver.service.MonitoringForegroundService
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
            onPermissionsGranted = {
                viewModel.onPermissionsGranted()
                setupStep = SetupStep.BLUETOOTH
            }
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
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
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
// Step 3 — Active Monitoring UI with live camera preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonitoringScreen(
    viewModel: DriverViewModel,
    onSignOut: () -> Unit
) {
    val uiState          by viewModel.uiState.collectAsState()
    val monitoringStatus by MonitoringForegroundService.monitoringStatusFlow.collectAsState()
    val view             = LocalView.current
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showPinDialog      by remember { mutableStateOf(false) }
    var pinInput           by remember { mutableStateOf("") }
    var pinError           by remember { mutableStateOf<String?>(null) }

    // Keep screen on at low brightness so camera continues even when user "turns off" screen
    DisposableEffect(Unit) {
        val window = (view.context as? androidx.activity.ComponentActivity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Hardware back → ask for sign-out confirmation
    BackHandler(enabled = true) { showSignOutConfirm = true }

    val showAlert = monitoringStatus is MonitoringStatus.Alert

    // Determine camera border color based on monitoring status
    val borderColor = when (monitoringStatus) {
        is MonitoringStatus.Active         -> Color(0xFF4CAF50)  // Green — all good
        is MonitoringStatus.Calibrating    -> Amber               // Amber — calibrating
        is MonitoringStatus.Paused         -> Color(0xFFFF9800)  // Orange — paused
        is MonitoringStatus.FaceNotDetected -> Color(0xFFFF9800) // Orange — no face
        is MonitoringStatus.Alert          -> Color.Red           // Red — impairment
    }

    val statusLabel = when (val s = monitoringStatus) {
        is MonitoringStatus.Active         -> "Monitoring Active"
        is MonitoringStatus.Calibrating    -> "Calibrating (${(s.progress * 100).toInt()}%)"
        is MonitoringStatus.Paused         -> "Starting..."
        is MonitoringStatus.FaceNotDetected -> "Face Not Detected — Adjust Camera"
        is MonitoringStatus.Alert          -> "⚠ Impairment Detected"
    }

    Box(modifier = Modifier.fillMaxSize().background(NavyDark)) {

        // Full-screen red alert overlay
        AnimatedVisibility(visible = showAlert, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.35f)))
        }

        Column(
            modifier            = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top section: destination or title ──────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(32.dp))
                if (uiState.trip?.destination?.name?.isNotBlank() == true) {
                    Text(
                        "Destination",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text      = uiState.trip!!.destination.name,
                        color     = Amber,
                        fontSize  = 22.sp,
                        style     = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        "VigileX Monitoring",
                        color    = Amber,
                        fontSize = 22.sp,
                        style    = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            // ── Center: Live camera preview with colored border ───────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CameraPreviewBox(
                    borderColor = borderColor,
                    modifier    = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text      = statusLabel,
                    color     = borderColor,
                    style     = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            // ── Bottom: speed + sign-out ──────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val speedKmh = ((uiState.trip?.lastLocation?.speed ?: 0f) * 3.6f).toInt()
                if (speedKmh > 0) {
                    Text(
                        "$speedKmh km/h",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Sign-out confirmation — ALWAYS requires PIN ──────────────
        if (showSignOutConfirm) {
            AlertDialog(
                onDismissRequest = { showSignOutConfirm = false },
                containerColor   = Color(0xFF1A2A3A),
                title   = { Text("Monitoring Active", color = Amber) },
                text    = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Monitoring is running. To sign out, enter your 6-digit exit PIN.",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.75f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Contact your owner if you don't know your PIN.",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSignOutConfirm = false
                            showPinDialog = true
                            pinInput = ""
                            pinError = null
                        },
                        colors  = ButtonDefaults.buttonColors(containerColor = Amber)
                    ) { Text("Enter PIN", color = NavyDark) }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutConfirm = false }) {
                        Text("Cancel", color = Amber)
                    }
                }
            )
        }

        // ── PIN entry dialog ─────────────────────────────────────────
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                containerColor   = Color(0xFF1A2A3A),
                properties       = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = { Text("Enter Exit PIN", color = Amber) },
                text  = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value         = pinInput,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                            label         = { Text("6-digit PIN") },
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor     = Color.White,
                                unfocusedTextColor   = Color.White,
                                focusedBorderColor   = Amber,
                                unfocusedBorderColor = Color.White.copy(0.3f),
                                focusedLabelColor    = Amber,
                                unfocusedLabelColor  = Color.White.copy(0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (pinError != null) {
                            Text(pinError!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.validateExitPin(pinInput) { valid ->
                                if (valid) {
                                    showPinDialog = false
                                    onSignOut()
                                } else {
                                    pinError = "Invalid PIN. Contact your owner."
                                }
                            }
                        },
                        enabled = pinInput.length == 6,
                        colors  = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.8f))
                    ) { Text("Force Sign Out", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
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
// Camera Preview composable — shows live front camera with colored border
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPreviewBox(
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    // Collect the Preview use case from the foreground service
    val preview by MonitoringForegroundService.previewFlow.collectAsState()

    // Animate border color transitions
    val animatedBorderColor by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(500),
        label = "camera_border"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(4.dp, animatedBorderColor, RoundedCornerShape(16.dp))
            .background(NavyMid),
        contentAlignment = Alignment.Center
    ) {
        if (preview != null) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                update = { previewView ->
                    preview?.setSurfaceProvider(previewView.surfaceProvider)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Waiting for camera to initialize
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Amber, strokeWidth = 3.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Starting camera...",
                    color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
