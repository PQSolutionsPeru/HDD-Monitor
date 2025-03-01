package com.pqsolutions.hdd_monitor.presentation.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
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
import com.pqsolutions.hdd_monitor.esp32.ESP32Device
import com.pqsolutions.hdd_monitor.presentation.components.ESP32ConfigStep
import com.pqsolutions.hdd_monitor.presentation.components.ESP32ConfigurationStatus
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.BleViewModel

private const val TAG = "BleConfigScreen"

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
    var selectedClientName by remember { mutableStateOf("") }
    var selectedClientId by remember { mutableStateOf("") }
    var panelName by remember { mutableStateOf("") }
    var panelLocation by remember { mutableStateOf("") }
    var showClientMenu by remember { mutableStateOf(false) }

    val isAdmin = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAdmin.value = viewModel.isUserAdmin()
    }

    val state by viewModel.state.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val esp32s by viewModel.esp32s.collectAsState()

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Gestión de permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
        } else {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.hasRequiredPermissions()) {
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
            showBluetoothDialog = true
        }
    }

    BackHandler {
        onBackClick()
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
                // Extraer el estado de configuración de manera segura
                val configState = remember(state) {
                    (state as? BleState.ESP32ConfigurationProgress)
                }

                // Mostrar el estado de configuración si estamos en un estado de progreso
                configState?.let { config ->
                    ESP32ConfigurationStatus(
                        bleConnected = config.bleConnected,
                        wifiConfigured = config.wifiConfigured,
                        wifiError = config.wifiError,
                        mqttConnected = config.mqttConnected,
                        configReceived = config.configReceived,
                        relaysConfigured = config.relaysConfigured,
                        currentStep = config.currentStep
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                when (val currentState = state) {
                    is BleState.Initial,
                    is BleState.Scanning -> {
                        ScanningSection(
                            state = currentState,
                            devices = devices,
                            onScanClick = { viewModel.startScan() },
                            onDeviceClick = { device -> viewModel.connectToDevice(device) }
                        )
                    }

                    is BleState.ESP32ConfigurationProgress -> {
                        when (currentState.currentStep) {
                            ESP32ConfigStep.WIFI_CONFIG -> {
                                WifiConfigSection(
                                    ssid = wifiSsid,
                                    password = wifiPassword,
                                    onSsidChange = { wifiSsid = it },
                                    onPasswordChange = { wifiPassword = it },
                                    onSendClick = {
                                        viewModel.sendWifiConfiguration(wifiSsid, wifiPassword)
                                    }
                                )
                            }
                            ESP32ConfigStep.RELAY_CONFIG -> {
                                currentState.esp32Device?.let { esp32Device ->
                                    ESP32StatusSection(
                                        esp32Device = esp32Device,
                                        onContinueClick = {
                                            viewModel.moveToClientSelection(esp32Device)
                                        }
                                    )
                                }
                            }
                            else -> {
                                LoadingSection(
                                    message = when (currentState.currentStep) {
                                        ESP32ConfigStep.MQTT_CONNECT -> stringResource(R.string.mqtt_connection)
                                        ESP32ConfigStep.RECEIVING_CONFIG -> stringResource(R.string.receiving_config)
                                        else -> stringResource(R.string.please_wait)
                                    }
                                )
                            }
                        }
                    }

                    is BleState.SelectingClient -> {
                        if (isAdmin.value) {
                            ClientSelectionSection(
                                esp32Device = currentState.esp32Device,
                                clients = clients,
                                selectedClientName = selectedClientName,
                                showClientMenu = showClientMenu,
                                onClientMenuChange = { showClientMenu = it },
                                onClientSelect = { client ->
                                    selectedClientName = client.name
                                    selectedClientId = client.documentName
                                    showClientMenu = false
                                },
                                onContinueClick = {
                                    viewModel.moveToCreatePanel(currentState.esp32Device, selectedClientId)
                                }
                            )
                        } else {
                            CreatePanelSection(
                                esp32Device = currentState.esp32Device,
                                panelName = panelName,
                                panelLocation = panelLocation,
                                onPanelNameChange = { panelName = it },
                                onPanelLocationChange = { panelLocation = it },
                                onCreateClick = {
                                    viewModel.createNewPanelForNormalUser(
                                        panelName = panelName,
                                        location = panelLocation
                                    )
                                }
                            )
                        }
                    }

                    is BleState.ConfigurationSuccess -> {
                        SuccessSection(
                            message = buildString {
                                appendLine(stringResource(R.string.configuration_status))
                                appendLine(stringResource(
                                    R.string.esp32_configured,
                                    currentState.esp32Device.documentName
                                ))
                                appendLine(stringResource(R.string.configuration_completed))
                            },
                            onFinishClick = onConfigurationComplete
                        )
                    }

                    is BleState.ConfigurationError -> {
                        ErrorSection(
                            message = currentState.message,
                            onRetryClick = { viewModel.startScan() }
                        )
                    }

                    is BleState.Error -> {
                        ErrorSection(
                            message = currentState.message,
                            onRetryClick = { viewModel.startScan() }
                        )
                    }

                    else -> {
                        LoadingSection(
                            message = stringResource(R.string.please_wait)
                        )
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
            Text("Buscando dispositivos ESP32...")
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

@Composable
private fun WifiConfigSection(
    ssid: String,
    password: String,
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

        Text(
            "Por favor ingresa las credenciales de WiFi.\n" +
                    "Este proceso puede tomar hasta 2 minutos.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChange,
            label = { Text("Nombre de red WiFi") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña WiFi") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSendClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = ssid.isNotBlank() && password.isNotBlank()
        ) {
            Text("Configurar WiFi")
        }
    }
}

@Composable
private fun ESP32StatusSection(
    esp32Device: ESP32Device,
    onContinueClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "ESP32 Conectado",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("MAC: ${esp32Device.MAC}")
                Text("IP: ${esp32Device.IP}")
                Text("Estado: ${esp32Device.status}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onContinueClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continuar")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientSelectionSection(
    esp32Device: ESP32Device,
    clients: List<Client>,
    selectedClientName: String,
    showClientMenu: Boolean,
    onClientMenuChange: (Boolean) -> Unit,
    onClientSelect: (Client) -> Unit,
    onContinueClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Seleccionar Cliente",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = showClientMenu,
            onExpandedChange = onClientMenuChange
        ) {
            OutlinedTextField(
                value = selectedClientName,
                onValueChange = { },
                readOnly = true,
                label = { Text("Cliente") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showClientMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = showClientMenu,
                onDismissRequest = { onClientMenuChange(false) }
            ) {
                clients.forEach { client ->
                    DropdownMenuItem(
                        text = { Text(client.name) },
                        onClick = { onClientSelect(client) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onContinueClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedClientName.isNotBlank()
        ) {
            Text("Continuar")
        }
    }
}

@Composable
private fun CreatePanelSection(
    esp32Device: ESP32Device,
    panelName: String,
    panelLocation: String,
    onPanelNameChange: (String) -> Unit,
    onPanelLocationChange: (String) -> Unit,
    onCreateClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Crear Panel",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = panelName,
            onValueChange = onPanelNameChange,
            label = { Text("Nombre del Panel") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = panelLocation,
            onValueChange = onPanelLocationChange,
            label = { Text("Ubicación") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCreateClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = panelName.isNotBlank() && panelLocation.isNotBlank()
        ) {
            Text("Crear Panel")
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
private fun SuccessSection(
    message: String,
    onFinishClick: () -> Unit
) {
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onFinishClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Finalizar")
        }
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
                device.name?.takeIf { it.startsWith("ESP32-") }?.let { name ->
                    "ESP32 #${name.substringAfter("ESP32-")}"
                } ?: "ESP32 (Sin ID)"
            } else {
                "ESP32 (Sin permisos)"
            }
        } else {
            device.name?.takeIf { it.startsWith("ESP32-") }?.let { name ->
                "ESP32 #${name.substringAfter("ESP32-")}"
            } ?: "ESP32 (Sin ID)"
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
                    val enableBtIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
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