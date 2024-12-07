package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.ClientRepository
import com.pqsolutions.hdd_monitor.data.Relay
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import com.pqsolutions.hdd_monitor.util.BlePermissionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

private const val TAG = "BleViewModel"
private const val SCAN_PERIOD: Long = 10000 // 10 segundos de escaneo

@HiltViewModel
class BleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val panelRepository: PanelRepository,
    private val clientRepository: ClientRepository
) : ViewModel() {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _state = MutableStateFlow<BleState>(BleState.Initial)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients: StateFlow<List<Client>> = _clients.asStateFlow()

    private var currentGatt: BluetoothGatt? = null
    private var scanJob: Job? = null
    private var panelObserverJob: Job? = null
    private var dataReceivedJob: CompletableDeferred<Boolean>? = null
    private var currentClientName: String? = null
    private var currentPanel: Panel? = null
    private var lastKnownState: BleState = BleState.Initial
    private var expectingDisconnect = false
    private var currentClientId: String? = null


    companion object {
        val UART_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    init {
        loadClients()
        Log.d(TAG, "BleViewModel inicializado")
    }

    fun hasRequiredPermissions(): Boolean {
        return BlePermissionHandler.allPermissionsGranted(context)
    }

    fun isBluetoothEnabled(): Boolean {
        return BlePermissionHandler.checkBluetoothEnabled(context)
    }

    private fun loadClients() {
        viewModelScope.launch {
            clientRepository.getClients()
                .onSuccess { clientList ->
                    _clients.value = clientList
                    Log.d(TAG, "Clientes cargados: ${clientList.size}")
                }
                .onFailure { error ->
                    Log.e(TAG, "Error cargando clientes", error)
                    _state.value = BleState.Error("Error cargando lista de clientes")
                }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        Log.d(TAG, "Iniciando proceso de escaneo...")
        BlePermissionHandler.logPermissionStatus(context)

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Faltan permisos de Bluetooth")
            _state.value = BleState.Error("Se requieren permisos de Bluetooth")
            return
        }

        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth no está habilitado")
            _state.value = BleState.Error("Por favor activa el Bluetooth")
            return
        }

        _devices.value = emptyList()
        _state.value = BleState.Scanning
        lastKnownState = BleState.Scanning

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                Log.d(TAG, "Dispositivo encontrado: " +
                        "Nombre=${device.name ?: "Desconocido"}, " +
                        "Address=${device.address}, " +
                        "RSSI=${result.rssi}")

                if (device.name == "ESP32-Monitor") {
                    Log.i(TAG, "¡ESP32-Monitor encontrado!")
                    if (!_devices.value.contains(device)) {
                        _devices.value = _devices.value + device
                    }
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { result ->
                    onScanResult(0, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorMsg = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                        "El escaneo ya está en progreso"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                        "Error al registrar la aplicación para escaneo BLE"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                        "Escaneo BLE no soportado en este dispositivo"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
                        "Error interno del sistema de escaneo"
                    else -> "Error desconocido en escaneo: $errorCode"
                }
                Log.e(TAG, "Error en escaneo: $errorMsg")
                _state.value = BleState.Error(errorMsg)
            }
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Iniciando escaneo BLE...")
                withContext(Dispatchers.IO) {
                    bleScanner?.startScan(null, scanSettings, scanCallback)
                    delay(SCAN_PERIOD)
                    bleScanner?.stopScan(scanCallback)

                    if (_devices.value.isEmpty()) {
                        Log.d(TAG, "No se encontró ESP32-Monitor")
                        _state.value = BleState.Error(
                            "No se encontró ESP32-Monitor.\n" +
                                    "Asegúrate que el dispositivo esté encendido y cercano."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en escaneo", e)
                _state.value = BleState.Error("Error en escaneo: ${e.message}")
            } finally {
                withContext(Dispatchers.IO) {
                    bleScanner?.stopScan(scanCallback)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Intentando conectar a: ${device.name ?: device.address}")
        expectingDisconnect = false

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Conectado a GATT server")
                        currentGatt = gatt
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Desconectado de GATT server. Estado anterior: $lastKnownState")
                        currentGatt = null

                        when (lastKnownState) {
                            is BleState.ConfigurationReceived -> {
                                _state.value = BleState.AttemptingWifiConnection
                            }
                            is BleState.ConfigurationSuccess,
                            is BleState.AttemptingWifiConnection,
                            is BleState.DataSent -> {
                                // Mantener el estado actual
                            }
                            else -> {
                                if (lastKnownState !is BleState.ConfigurationError) {
                                    _state.value = BleState.Disconnected
                                }
                            }
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Servicios GATT descubiertos")
                    setupUartNotifications(gatt)
                    _state.value = BleState.Connected
                    lastKnownState = BleState.Connected
                } else {
                    Log.e(TAG, "Error descubriendo servicios")
                    _state.value = BleState.Error("Error descubriendo servicios")
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val value = characteristic.value.toString(Charsets.UTF_8)
                Log.d(TAG, "Datos recibidos del ESP32: $value")

                when {
                    value.contains("status:received") -> {
                        Log.d(TAG, "Configuración recibida por ESP32")
                        _state.value = BleState.ConfigurationReceived
                        lastKnownState = BleState.ConfigurationReceived
                    }
                    value.contains("status:success") -> {
                        Log.d(TAG, "Configuración exitosa en ESP32")
                        expectingDisconnect = true
                        lastKnownState = BleState.ConfigurationSuccess
                        viewModelScope.launch {
                            currentPanel?.let { panel ->
                                currentClientId?.let { clientId ->
                                    try {
                                        Log.d(TAG, "Creando panel en Firebase: ${panel.toLogString()}")
                                        Log.d(TAG, "Usando clientId: $clientId para generar documentName")

                                        // Aquí el Repository usará el clientId para generar el documentName
                                        panelRepository.createNewPanel(clientId, panel)
                                            .onSuccess { panelId ->
                                                _state.value = BleState.ConfigurationSuccess
                                                observeNewPanel(clientId, panelId)
                                            }
                                            .onFailure { error ->
                                                val errorMsg = when {
                                                    error.message?.contains("Panel data is invalid") == true ->
                                                        "Error en datos del panel: Verifica que todos los campos estén completos"
                                                    error.message?.contains("already exists") == true ->
                                                        "Ya existe un panel con este nombre"
                                                    else -> "Error creando panel: ${error.message}"
                                                }
                                                Log.e(TAG, errorMsg, error)
                                                _state.value = BleState.Error(errorMsg)
                                                lastKnownState = _state.value
                                            }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error creando panel", e)
                                        _state.value = BleState.Error("Error creando panel: ${e.message}")
                                        lastKnownState = _state.value
                                    }
                                }
                            }
                        }
                    }
                    value.contains("status:error") -> {
                        val errorMessage = when {
                            value.contains("wifi_connection_failed") ->
                                "No se pudo conectar a la red WiFi. Verifica el SSID y contraseña."
                            else -> "Error en la configuración del dispositivo"
                        }
                        Log.e(TAG, "Error recibido del ESP32: $errorMessage")
                        _state.value = BleState.ConfigurationError(errorMessage)
                        lastKnownState = BleState.ConfigurationError(errorMessage)
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Escritura exitosa en característica")
                } else {
                    Log.e(TAG, "Error en escritura de característica")
                    _state.value = BleState.Error("Error enviando datos al dispositivo")
                }
            }
        }

        device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    private fun setupUartNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(UART_SERVICE_UUID)
        val txCharacteristic = service?.getCharacteristic(UART_TX_CHAR_UUID)

        if (txCharacteristic != null) {
            gatt.setCharacteristicNotification(txCharacteristic, true)
            Log.d(TAG, "Notificaciones UART configuradas")
        } else {
            Log.e(TAG, "No se encontró característica TX UART")
        }
    }

    private fun observeNewPanel(clientId: String, panelId: String) {
        Log.d(TAG, "Iniciando observación del panel: $panelId")
        panelObserverJob?.cancel()
        panelObserverJob = viewModelScope.launch {
            try {
                panelRepository.observePanelUpdates(clientId, panelId)
                    .collect { panel ->
                        when {
                            panel == null -> {
                                Log.e(TAG, "Panel no encontrado en la BD")
                                _state.value = BleState.Error("No se pudo encontrar el panel en la base de datos")
                                panelObserverJob?.cancel()
                            }
                            panel.SSID_CON == "OK" -> {
                                Log.d(TAG, "Panel conectado exitosamente: ${panel.toLogString()}")
                                _state.value = BleState.ConfigurationSuccess
                                delay(2000)
                                _state.value = BleState.DataSent
                                panelObserverJob?.cancel()
                            }
                            panel.SSID_CON == "DISC" -> {
                                Log.d(TAG, "Panel desconectado: ${panel.toLogString()}")
                                _state.value = BleState.Error("El panel no logró conectarse a la red WiFi")
                                panelObserverJob?.cancel()
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error observando panel", e)
                _state.value = BleState.Error("Error monitoreando el estado del panel: ${e.message}")
                panelObserverJob?.cancel()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendConfiguration(
        wifiSsid: String,
        wifiPassword: String,
        panelName: String,
        panelLocation: String,
        clientName: String,
        clientId: String
    ) {
        viewModelScope.launch {
            try {
                if (currentGatt == null) {
                    throw Exception("No hay conexión BLE activa")
                }

                Log.d(TAG, "Preparando configuración para enviar...")

                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
                currentPanel = Panel(
                    documentName = "", // Se generará por IdManager en el Repository
                    name = panelName.trim(),
                    location = panelLocation.trim(),
                    SSID = wifiSsid.trim(),
                    SSID_PW = wifiPassword.trim(),
                    ESP32_IP = "",
                    clientName = clientName, // Nombre del cliente para mostrar
                    SSID_CON = null,
                    relays = listOf(
                        Relay(
                            name = "Alarma",
                            status = "DISC",
                            date_time = now
                        ),
                        Relay(
                            name = "Problema",
                            status = "DISC",
                            date_time = now
                        ),
                        Relay(
                            name = "Supervision",
                            status = "DISC",
                            date_time = now
                        )
                    ),
                    overallStatus = "DISC"
                )

                currentClientName = clientName
                currentClientId = clientId

                val configMessage = buildString {
                    append("ssid:${wifiSsid.trim()},")
                    append("password:${wifiPassword.trim()},")
                    append("name:${panelName.trim()},")
                    append("location:${panelLocation.trim()}")
                }

                Log.d(TAG, "Enviando configuración al ESP32...")

                val service = currentGatt?.getService(UART_SERVICE_UUID)
                val rxCharacteristic = service?.getCharacteristic(UART_RX_CHAR_UUID)

                if (rxCharacteristic == null) {
                    throw Exception("No se encontraron las características UART necesarias")
                }

                rxCharacteristic.value = configMessage.toByteArray()
                if (!currentGatt?.writeCharacteristic(rxCharacteristic)!!) {
                    throw Exception("Error al enviar configuración al ESP32")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en configuración: ${e.message}", e)
                _state.value = BleState.Error(e.message ?: "Error desconocido")
                lastKnownState = _state.value
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        panelObserverJob?.cancel()
        try {
            if (hasRequiredPermissions()) {
                currentGatt?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando conexión GATT", e)
        }
        dataReceivedJob?.cancel()
        Log.d(TAG, "BleViewModel liberado")
    }
}

