package com.pqsolutions.hdd_monitor.presentation.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.BleViewModel
import com.pqsolutions.hdd_monitor.presentation.state.BleState
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            if (viewModel.isBluetoothEnabled()) {
                viewModel.startScan()
            } else {
                showBluetoothDialog = true
            }
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
                // Selector de Cliente actualizado
                ExposedDropdownMenuBox(
                    expanded = showMenu,
                    onExpandedChange = { showMenu = it }
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
                        onDismissRequest = { showMenu = false }
                    ) {
                        clients.forEach { client ->
                            DropdownMenuItem(
                                text = { Text(client.name) },
                                onClick = {
                                    selectedClientName = client.name
                                    selectedClientId = client.documentName // Guardamos también el ID
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it },
                    label = { Text("Nombre de red WiFi") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = wifiPassword,
                    onValueChange = { wifiPassword = it },
                    label = { Text("Contraseña WiFi") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = panelName,
                    onValueChange = { panelName = it },
                    label = { Text("Nombre del panel") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = panelLocation,
                    onValueChange = { panelLocation = it },
                    label = { Text("Ubicación del panel") },
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
                            Text("Buscar dispositivos")
                        }
                    }
                    is BleState.Scanning -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Buscando dispositivos...")
                            Spacer(modifier = Modifier.height(16.dp))
                            devices.forEach { device ->
                                DeviceButton(device = device, onClick = { viewModel.connectToDevice(it) })
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    is BleState.Connected -> {
                        Button(
                            onClick = {
                                viewModel.sendConfiguration(
                                    wifiSsid = wifiSsid,
                                    wifiPassword = wifiPassword,
                                    panelName = panelName,
                                    panelLocation = panelLocation,
                                    clientName = selectedClientName,
                                    clientId = selectedClientId
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = wifiSsid.isNotBlank() &&
                                    wifiPassword.isNotBlank() &&
                                    panelName.isNotBlank() &&
                                    panelLocation.isNotBlank() &&
                                    selectedClientName.isNotBlank() &&
                                    selectedClientId.isNotBlank()
                        ) {
                            Text("Enviar configuración")
                        }
                    }
                    is BleState.ConfigurationReceived -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Configuración recibida, conectando WiFi...",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    is BleState.AttemptingWifiConnection -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Intentando conectar a la red WiFi...\nEsto puede tomar unos momentos",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    is BleState.ConfigurationSuccess -> {
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
                                "¡Configuración exitosa!\nEl dispositivo se está reiniciando...",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    is BleState.DataSent -> {
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
                                "Panel configurado y registrado exitosamente",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LaunchedEffect(Unit) {
                            delay(2000)
                            onConfigurationComplete()
                        }
                    }
                    is BleState.ConfigurationError -> {
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
                                (state as BleState.ConfigurationError).message,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.startScan() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                    is BleState.Error -> {
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
                                (state as BleState.Error).message,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.startScan() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reintentar")
                            }
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
            title = { Text("Se requieren permisos") },
            text = { Text("Se necesitan permisos de Bluetooth para configurar el dispositivo") },
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
                    Text("Ir a Ajustes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            title = { Text("Bluetooth Desactivado") },
            text = { Text("Por favor activa el Bluetooth para continuar") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBluetoothDialog = false
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                            PackageManager.PERMISSION_GRANTED) {
                            context.startActivity(enableBtIntent)
                        }
                    }
                ) {
                    Text("Activar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBluetoothDialog = false }) {
                    Text("Cancelar")
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
            device.name ?: "Dispositivo desconocido"
        }
    }

    Button(
        onClick = { onClick(device) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(deviceName)
    }
}