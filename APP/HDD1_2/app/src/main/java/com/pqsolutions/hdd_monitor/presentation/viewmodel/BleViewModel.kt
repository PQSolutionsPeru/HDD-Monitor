package com.pqsolutions.hdd_monitor.presentation.viewmodel

import com.pqsolutions.hdd_monitor.esp32.ESP32Device
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.bluetooth.BleConnector
import com.pqsolutions.hdd_monitor.bluetooth.BleScanner
import com.pqsolutions.hdd_monitor.bluetooth.BleConnectionState
import com.pqsolutions.hdd_monitor.bluetooth.WifiConfig
import com.pqsolutions.hdd_monitor.bluetooth.WifiConfigState
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.data.ClientRepository
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.esp32.ESP32Repository
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import com.pqsolutions.hdd_monitor.util.BlePermissionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BleViewModel"
private const val SCAN_TIMEOUT = 30000L // 30 segundos
private const val WIFI_CONFIG_TIMEOUT = 30000L // 30 segundos

@HiltViewModel
class BleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleScanner: BleScanner,
    private val bleConnector: BleConnector,
    private val clientRepository: ClientRepository,
    private val panelRepository: PanelRepository,
    private val esp32Repository: ESP32Repository
) : ViewModel() {

    private val _state = MutableStateFlow<BleState>(BleState.Initial)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients: StateFlow<List<Client>> = _clients.asStateFlow()

    private val _esp32s = MutableStateFlow<List<ESP32Device>>(emptyList())
    val esp32s: StateFlow<List<ESP32Device>> = _esp32s.asStateFlow()

    private var currentESP32: ESP32Device? = null

    private var scanJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        viewModelScope.launch {
            Log.d(TAG, "Iniciando BleViewModel")
            _state.value = BleState.Initial
            observeDevices()
            observeConnectionState()
            observeWifiConfigState()
            loadClients()
            observeESP32s()

            if (_state.value is BleState.Initial) {
                Log.d(TAG, "Verificando condiciones iniciales")
                when {
                    !hasRequiredPermissions() -> {
                        Log.d(TAG, "Se requieren permisos")
                        requestPermissions { startScan() }
                    }
                    !isBluetoothEnabled() -> {
                        Log.d(TAG, "Bluetooth desactivado")
                        _state.value = BleState.Error("Bluetooth no está activado")
                    }
                    else -> {
                        Log.d(TAG, "Iniciando escaneo automático")
                        startScan()
                    }
                }
            }
        }
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

    fun hasRequiredPermissions(): Boolean =
        BlePermissionHandler.allPermissionsGranted(context)

    fun isBluetoothEnabled(): Boolean =
        BlePermissionHandler.checkBluetoothEnabled(context)

    private fun observeDevices() {
        viewModelScope.launch {
            bleScanner.foundDevices.collect { devices ->
                _devices.value = devices.toList()
            }
        }
    }

    private fun observeESP32s() {
        viewModelScope.launch {
            esp32Repository.observeESP32s().collect { devices ->
                _esp32s.value = devices
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            bleConnector.connectionState.collect { connectionState ->
                Log.d(TAG, "Nuevo estado de conexión: $connectionState")
                when (connectionState) {
                    is BleConnectionState.Connected -> {
                        Log.d(TAG, "Dispositivo conectado")
                        _state.value = BleState.Connected
                        timeoutJob?.cancel()
                    }
                    is BleConnectionState.Connecting -> {
                        Log.d(TAG, "Iniciando conexión con dispositivo")
                        _state.value = BleState.Connecting
                    }
                    is BleConnectionState.Disconnected -> {
                        Log.d(TAG, "Dispositivo desconectado. Estado actual: ${_state.value}")
                        when (_state.value) {
                            is BleState.Connecting,
                            is BleState.Connected -> {
                                Log.d(TAG, "Cambiando a estado Disconnected")
                                _state.value = BleState.Disconnected
                            }
                            else -> {
                                Log.d(TAG, "Manteniendo estado actual: ${_state.value}")
                            }
                        }
                    }
                    is BleConnectionState.Error -> {
                        Log.e(TAG, "Error de conexión: ${connectionState.message}")
                        _state.value = BleState.Error(connectionState.message)
                    }
                }
            }
        }
    }

    fun startScan() {
        viewModelScope.launch {
            Log.d(TAG, "Iniciando escaneo BLE")
            if (!hasRequiredPermissions()) {
                Log.d(TAG, "Requiriendo permisos para escaneo")
                requestPermissions { startScan() }
                return@launch
            }

            if (!isBluetoothEnabled()) {
                Log.d(TAG, "Bluetooth no está activado")
                _state.value = BleState.Error("Bluetooth no está activado")
                return@launch
            }

            scanJob?.cancel()
            bleScanner.clearDevices()
            _state.value = BleState.Scanning

            Log.d(TAG, "Comenzando escaneo de dispositivos")
            scanJob = viewModelScope.launch {
                try {
                    bleScanner.startScan()
                    delay(SCAN_TIMEOUT)
                    stopScan()

                    if (_devices.value.isEmpty()) {
                        Log.d(TAG, "No se encontraron dispositivos")
                        _state.value = BleState.Error(
                            "No se encontraron dispositivos ESP32.\n" +
                                    "Asegúrate que el dispositivo esté encendido y cercano."
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error durante el escaneo", e)
                    _state.value = BleState.Error("Error durante el escaneo: ${e.message}")
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        bleScanner.stopScan()
        if (_state.value is BleState.Scanning) {
            _state.value = BleState.Initial
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            Log.d(TAG, "Intentando conectar a dispositivo: ${device.address}")
            if (!hasRequiredPermissions()) {
                Log.d(TAG, "Requiriendo permisos para conexión")
                requestPermissions { connectToDevice(device) }
                return@launch
            }

            _state.value = BleState.Connecting
            startTimeoutTimer(CONNECTION_TIMEOUT)  // Especificamos el timeout
            try {
                bleConnector.connect(device)
            } catch (e: Exception) {
                Log.e(TAG, "Error conectando al dispositivo", e)
                _state.value = BleState.Error("Error conectando al dispositivo: ${e.message}")
            }
        }
    }

    fun sendWifiConfiguration(ssid: String, password: String) {
        if (_state.value != BleState.Connected) {
            Log.e(TAG, "Intento de enviar configuración WiFi sin estar conectado")
            return
        }

        viewModelScope.launch {
            _state.value = BleState.WifiConfigReceived

            val success = bleConnector.sendWifiConfig(WifiConfig(ssid, password))
            if (!success) {
                _state.value = BleState.Error("Error enviando configuración WiFi")
            } else {
                _state.value = BleState.WifiConfiguring
                startTimeoutTimer(WIFI_CONFIG_TIMEOUT)
            }
        }
    }

    private fun handleWifiConfigured(mac: String, ip: String) {
        viewModelScope.launch {
            try {
                val esp32Id = devices.value.firstOrNull()?.let { device ->
                    try {
                        device.name?.let { name ->
                            if (name.startsWith("ESP32-")) {
                                name.substringAfter("ESP32-")
                            } else null
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Error de permisos al leer nombre del dispositivo", e)
                        null
                    }
                } ?: throw Exception("No se pudo obtener ID del ESP32")

                // Actualizar Firestore
                try {
                    withTimeout(FIRESTORE_TIMEOUT) {
                        esp32Repository.updateNetworkInfo(
                            esp32Id = esp32Id,
                            ip = ip,
                            mac = mac
                        ).getOrThrow()
                    }
                } catch (e: Exception) {
                    throw Exception("Timeout o error actualizando Firestore: ${e.message}")
                }

                // Esperar confirmación de registro
                var registered = false
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < FIRESTORE_TIMEOUT) {
                    val esp32 = esp32Repository.findByMAC(mac)
                    if (esp32 != null && esp32.status == ESP32Device.STATUS_AWAITING_CONFIG) {
                        registered = true
                        break
                    }
                    delay(1000)
                }

                if (!registered) {
                    throw Exception("No se pudo confirmar el registro del dispositivo")
                }

                currentESP32 = ESP32Device(
                    documentName = esp32Id,
                    MAC = mac,
                    IP = ip,
                    status = ESP32Device.STATUS_AWAITING_CONFIG
                )

                _state.value = BleState.WaitingForRunningMode
                startTimeoutTimer(RUNNING_MODE_TIMEOUT)

            } catch (e: Exception) {
                Log.e(TAG, "Error en handleWifiConfigured", e)
                _state.value = BleState.ConfigurationError(e.message ?: "Error configurando dispositivo")
            }
        }
    }

    fun onRunningModeConfirmed(esp32Device: ESP32Device) {
        _state.value = BleState.SelectingClient(esp32Device)
    }

    fun createNewPanel(
        clientId: String,
        panelName: String,
        location: String
    ) {
        viewModelScope.launch {
            try {
                timeoutJob?.cancel()
                _state.value = BleState.CreatingPanel(currentESP32!!, clientId)

                val panel = Panel.createNew(
                    name = panelName,
                    location = location,
                    clientName = clientId,
                    esp32Id = currentESP32!!.documentName
                )

                val panelId = panelRepository.createNewPanel(
                    clientDocName = clientId,
                    panel = panel,
                    esp32Id = currentESP32!!.documentName
                ).getOrThrow()

                esp32Repository.assignToPanelAndClient(
                    esp32Id = currentESP32!!.documentName,
                    clientId = clientId,
                    panelId = panelId
                )

                _state.value = BleState.ConfigurationSuccess
                disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating panel", e)
                _state.value = BleState.ConfigurationError(e.message ?: "Error creando panel")
            }
        }
    }

    fun observeWifiConfigState() {
        viewModelScope.launch {
            bleConnector.wifiConfigState.collect { wifiState ->
                when (wifiState) {
                    is WifiConfigState.Initial -> {
                        Log.d(TAG, "Estado WiFi inicial")
                    }
                    is WifiConfigState.Sending -> {
                        Log.d(TAG, "Enviando configuración WiFi")
                        _state.value = BleState.WifiConfiguring
                    }
                    is WifiConfigState.Success -> {
                        Log.d(TAG, "Configuración WiFi exitosa")
                        val device = devices.value.firstOrNull()
                        if (device != null) {
                            Log.d(TAG, "Dispositivo encontrado: ${device.address}")
                            handleWifiConfigured(mac = device.address, ip = wifiState.ip)
                        } else {
                            Log.e(TAG, "No se encontró el dispositivo")
                            _state.value = BleState.Error("No se pudo identificar el dispositivo")
                        }
                    }
                    is WifiConfigState.Error -> {
                        Log.e(TAG, "Error en configuración WiFi: ${wifiState.message}")
                        _state.value = BleState.Error("Error en configuración WiFi: ${wifiState.message}")
                    }
                }
            }
        }
    }

    fun moveToClientSelection(esp32Device: ESP32Device) {
        timeoutJob?.cancel()
        _state.value = BleState.SelectingClient(esp32Device)
    }

    fun moveToCreatePanel(esp32Device: ESP32Device, clientId: String) {
        timeoutJob?.cancel()
        _state.value = BleState.CreatingPanel(esp32Device, clientId)
    }

    fun onWifiConfigured(esp32Device: ESP32Device) {
        _state.value = BleState.WifiConfigured(esp32Device)
    }

    fun startTimeoutTimer(timeout: Long) {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(timeout)
            when (_state.value) {
                is BleState.WifiConfiguring -> {
                    _state.value = BleState.Error(
                        "Timeout configurando WiFi"
                    )
                    disconnect()
                }
                is BleState.WaitingForRunningMode -> {
                    _state.value = BleState.Error(
                        "Timeout esperando confirmación"
                    )
                    disconnect()
                }
                else -> {
                    _state.value = BleState.Error(
                        "Operación cancelada por timeout"
                    )
                    disconnect()
                }
            }
        }
    }

    private fun requestPermissions(onGranted: () -> Unit) {
        _state.value = BleState.RequiresPermission(
            permissions = BlePermissionHandler.getRequiredPermissions().toList(),
            onPermissionGranted = onGranted
        )
    }

    fun disconnect() {
        bleConnector.disconnect()
        timeoutJob?.cancel()
    }

    fun onPermissionsGranted() {
        when (val currentState = state.value) {
            is BleState.RequiresPermission -> currentState.onPermissionGranted()
            else -> startScan()
        }
    }

    fun selectESP32(esp32: ESP32Device) {
        currentESP32 = esp32
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        stopScan()
        scanJob?.cancel()
        timeoutJob?.cancel()
    }

    companion object {
        private const val TAG = "BleViewModel"
        private const val SCAN_TIMEOUT = 45000L         // 45 segundos
        const val WIFI_CONFIG_TIMEOUT = 60000L  // 60 segundos
        private const val RUNNING_MODE_TIMEOUT = 90000L // 90 segundos
        private const val CONNECTION_TIMEOUT = 20000L   // 20 segundos
        private const val FIRESTORE_TIMEOUT = 30000L    // 30 segundos
    }
}