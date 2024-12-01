package com.pqsolutions.hdd_monitor.presentation.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.BleViewModel
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import android.content.pm.PackageManager
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleConfigScreen(
    viewModel: BleViewModel = hiltViewModel(),
    onConfigurationComplete: () -> Unit,
    onBackClick: () -> Unit
) {
    var showPermissionDialog by remember { mutableStateOf(false) }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var panelName by remember { mutableStateOf("") }
    var panelLocation by remember { mutableStateOf("") }

    val state by viewModel.state.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.startScan()
        } else {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.hasRequiredPermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN
                    )
                )
            }
        }
    }

    HDD1_2Theme {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.configure_esp32),
                    onBackClick = onBackClick
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it },
                    label = { Text(stringResource(R.string.wifi_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = wifiPassword,
                    onValueChange = { wifiPassword = it },
                    label = { Text(stringResource(R.string.wifi_password)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = panelName,
                    onValueChange = { panelName = it },
                    label = { Text(stringResource(R.string.panel_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = panelLocation,
                    onValueChange = { panelLocation = it },
                    label = { Text(stringResource(R.string.panel_location)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (state) {
                    is BleState.Initial -> {
                        Button(
                            onClick = {
                                if (viewModel.hasRequiredPermissions()) {
                                    viewModel.startScan()
                                } else {
                                    showPermissionDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.search_devices))
                        }
                    }
                    is BleState.Scanning -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.scanning_devices))
                        Spacer(modifier = Modifier.height(16.dp))
                        devices.forEach { device ->
                            DeviceButton(device = device, onClick = { viewModel.connectToDevice(it) })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    is BleState.Connected -> {
                        Button(
                            onClick = {
                                viewModel.sendConfiguration(
                                    wifiSsid = wifiSsid,
                                    wifiPassword = wifiPassword,
                                    panelName = panelName,
                                    panelLocation = panelLocation
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = wifiSsid.isNotBlank() &&
                                    wifiPassword.isNotBlank() &&
                                    panelName.isNotBlank() &&
                                    panelLocation.isNotBlank()
                        ) {
                            Text(stringResource(R.string.send_configuration))
                        }
                    }
                    is BleState.DataSent -> {
                        Text(
                            stringResource(R.string.configuration_successful),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LaunchedEffect(Unit) {
                            onConfigurationComplete()
                        }
                    }
                    is BleState.Error -> {
                        Text(
                            (state as BleState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.startScan() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    else -> { /* Estados no manejados */ }
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.permissions_required)) },
            text = { Text(stringResource(R.string.bluetooth_permission_explanation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DeviceButton(
    device: BluetoothDevice,
    onClick: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current

    val deviceName = remember(device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED) {
                device.name ?: "Dispositivo desconocido"
            } else {
                "Dispositivo desconocido"
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED) {
                device.name ?: "Dispositivo desconocido"
            } else {
                "Dispositivo desconocido"
            }
        }
    }

    Button(
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED) {
                    onClick(device)
                }
            } else {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED) {
                    onClick(device)
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(deviceName)
    }
}