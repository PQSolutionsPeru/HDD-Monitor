package com.pqsolutions.hdd_monitor.presentation.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.BleViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleConfigScreen(
    viewModel: BleViewModel = hiltViewModel(),
    onConfigurationComplete: () -> Unit,
    onBackClick: () -> Unit
) {
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var panelName by remember { mutableStateOf("") }
    var panelLocation by remember { mutableStateOf("") }
    var selectedClientName by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(state) {
        Log.d("BleConfigScreen", "Estado actual: $state")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d("BleConfigScreen", "Permisos concedidos: $allGranted")
        if (allGranted) {
            viewModel.onPermissionsGranted()
        } else {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.hasRequiredPermissions()) {
            Log.d("BleConfigScreen", "Solicitando permisos iniciales")
            permissionLauncher.launch(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                } else {
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN
                    )
                }
            )
        } else if (!viewModel.isBluetoothEnabled()) {
            Log.d("BleConfigScreen", "Bluetooth desactivado")
            showBluetoothDialog = true
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
                when (state) {
                    is BleState.Initial,
                    is BleState.Scanning -> {
                        ScanningSection(
                            state = state,
                            devices = devices,
                            onScanClick = {
                                Log.d("BleConfigScreen", "Iniciando escaneo")
                                if (viewModel.hasRequiredPermissions()) {
                                    viewModel.startScan()
                                } else {
                                    showPermissionDialog = true
                                }
                            },
                            onDeviceClick = { device ->
                                Log.d("BleConfigScreen", "Conectando a dispositivo: ${device.address}")
                                viewModel.connectToDevice(device)
                            }
                        )
                    }

                    is BleState.Connecting -> {
                        LoadingSection(message = "Conectando al dispositivo...")
                    }

                    is BleState.Connected -> {
                        WifiConfigSection(
                            wifiSsid = wifiSsid,
                            wifiPassword = wifiPassword,
                            onSsidChange = { wifiSsid = it },
                            onPasswordChange = { wifiPassword = it },
                            onSendClick = {
                                Log.d("BleConfigScreen", "Enviando configuración WiFi: SSID=$wifiSsid")
                                viewModel.sendWifiConfiguration(wifiSsid, wifiPassword)
                            }
                        )
                    }

                    is BleState.WifiConfigReceived -> {
                        LoadingSection(message = "Configuración WiFi enviada, esperando conexión...")
                    }

                    is BleState.WifiConnected,
                    is BleState.WaitingPanelConfig -> {
                        PanelConfigSection(
                            selectedClientName = selectedClientName,
                            showMenu = showMenu,
                            clients = clients,
                            panelName = panelName,
                            panelLocation = panelLocation,
                            onClientSelect = { client ->
                                selectedClientName = client.name
                                selectedClientId = client.documentName
                                showMenu = false
                            },
                            onShowMenuChange = { showMenu = it },
                            onPanelNameChange = { panelName = it },
                            onPanelLocationChange = { panelLocation = it },
                            onSendClick = {
                                viewModel.sendPanelConfiguration(
                                    panelName = panelName,
                                    panelLocation = panelLocation,
                                    clientName = selectedClientName,
                                    clientId = selectedClientId
                                )
                            }
                        )
                    }

                    is BleState.ConfigurationSuccess -> {
                        SuccessSection(message = "¡Configuración exitosa!\nEl dispositivo se está reiniciando...")
                    }

                    is BleState.ConfigurationError -> {
                        ErrorSection(
                            message = (state as BleState.ConfigurationError).message,
                            onRetryClick = { viewModel.startScan() }
                        )
                    }

                    is BleState.Error -> {
                        ErrorSection(
                            message = (state as BleState.Error).message,
                            onRetryClick = { viewModel.startScan() }
                        )
                    }

                    is BleState.RequiresPermission -> {
                        LaunchedEffect(Unit) {
                            permissionLauncher.launch((state as BleState.RequiresPermission).permissions.toTypedArray())
                        }
                        LoadingSection(message = "Solicitando permisos necesarios...")
                    }

                    is BleState.Disconnected -> {
                        Button(
                            onClick = { viewModel.startScan() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reintentar conexión")
                        }
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onConfirm = {
                showPermissionDialog = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }

    if (showBluetoothDialog) {
        BluetoothDialog(
            context = context,
            onDismiss = { showBluetoothDialog = false }
        )
    }
}

@Composable
private fun ScanningSection(
    state: BleState,
    devices: List<BluetoothDevice>,
    onScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state is BleState.Scanning) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Buscando dispositivos...")
            Spacer(modifier = Modifier.height(16.dp))
            devices.forEach { device ->
                DeviceButton(device = device, onClick = onDeviceClick)
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buscar dispositivos")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiConfigSection(
    wifiSsid: String,
    wifiPassword: String,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Conectado al dispositivo",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = wifiSsid,
            onValueChange = onSsidChange,
            label = { Text("Nombre de red WiFi") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = wifiPassword,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña WiFi") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSendClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = wifiSsid.isNotBlank() && wifiPassword.isNotBlank()
        ) {
            Text("Configurar WiFi")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelConfigSection(
    selectedClientName: String,
    showMenu: Boolean,
    clients: List<Client>,
    panelName: String,
    panelLocation: String,
    onClientSelect: (Client) -> Unit,
    onShowMenuChange: (Boolean) -> Unit,
    onPanelNameChange: (String) -> Unit,
    onPanelLocationChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "WiFi conectado. Configurar panel",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = showMenu,
            onExpandedChange = onShowMenuChange
        ) {
            OutlinedTextField(
                value = selectedClientName,
                onValueChange = { },
                readOnly = true,
                label = { Text("Cliente") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onShowMenuChange(false) }
            ) {
                clients.forEach { client ->
                    DropdownMenuItem(
                        text = { Text(client.name) },
                        onClick = { onClientSelect(client) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = panelName,
            onValueChange = onPanelNameChange,
            label = { Text("Nombre del panel") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = panelLocation,
            onValueChange = onPanelLocationChange,
            label = { Text("Ubicación del panel") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSendClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedClientName.isNotBlank() &&
                    panelName.isNotBlank() &&
                    panelLocation.isNotBlank()
        ) {
            Text("Configurar Panel")
        }
    }
}

@Composable
private fun LoadingSection(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SuccessSection(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ErrorSection(
    message: String,
    onRetryClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetryClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reintentar")
        }
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
                PackageManager.PERMISSION_GRANTED
            ) {
                device.name?.let { name ->
                    val id = name.substringAfter("ESP32-", "")
                    if (id.isNotEmpty()) {
                        "ESP32 #$id"
                    } else {
                        "ESP32 (Sin ID)"
                    }
                } ?: "Dispositivo desconocido"
            } else {
                "Dispositivo desconocido"
            }
        } else {
            device.name?.let { name ->
                val id = name.substringAfter("ESP32-", "")
                if (id.isNotEmpty()) {
                    "ESP32 #$id"
                } else {
                    "ESP32 (Sin ID)"
                }
            } ?: "Dispositivo desconocido"
        }
    }

    Button(
        onClick = { onClick(device) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(deviceName)
    }
}

@Composable
private fun PermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Se requieren permisos") },
        text = { Text("Se necesitan permisos de Bluetooth para configurar el dispositivo") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Ir a Ajustes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun BluetoothDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth Desactivado") },
        text = { Text("Por favor activa el Bluetooth para continuar") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        context.startActivity(enableBtIntent)
                    }
                }
            ) {
                Text("Activar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}