package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.content.Context
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.bluetooth.*
import com.pqsolutions.hdd_monitor.data.*
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class BleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val panelRepository: PanelRepository,
    private val clientRepository: ClientRepository,
    private val bleManager: BleManager
) : ViewModel() {

    private val _state = MutableStateFlow<BleState>(BleState.Initial)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients: StateFlow<List<Client>> = _clients.asStateFlow()

    private val _configurationProgress = MutableStateFlow<Int>(0)
    val configurationProgress: StateFlow<Int> = _configurationProgress.asStateFlow()

    private val _isReadyToSend = MutableStateFlow(false)
    private var panelObserverJob: Job? = null
    private var currentConfiguration: BleConfiguration? = null
    private var currentPanel: Panel? = null
    private var deviceToConnect: BluetoothDevice? = null
    private var pendingConfiguration: BleConfiguration? = null

    companion object {
        private const val TAG = "BleViewModel"
    }

    init {
        loadClients()
    }

    fun hasRequiredPermissions(): Boolean = bleManager.checkPermissions()

    fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()

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

    fun startScan() {
        Log.d(TAG, "Iniciando escaneo desde ViewModel")
        if (!bleManager.isBluetoothEnabled()) {
            _state.value = BleState.Error("Bluetooth no está habilitado")
            return
        }

        if (!bleManager.checkPermissions()) {
            _state.value = BleState.Error("Se requieren permisos de Bluetooth")
            return
        }

        bleManager.startScan(
            onDeviceFound = { device ->
                Log.d(TAG, "Dispositivo encontrado en ViewModel: ${device.address}")
                _devices.value = _devices.value + device
            },
            onError = { error ->
                Log.e(TAG, "Error en escaneo: $error")
                _state.value = BleState.Error(error)
            }
        )
        _state.value = BleState.Scanning
    }

    fun connectToDevice(device: BluetoothDevice) {
        _isReadyToSend.value = false
        deviceToConnect = device
        _state.value = BleState.Connecting

        bleManager.connectToDevice(
            device = device,
            onStateChange = { state ->
                handleConnectionStateChange(state)
            },
            onDataReceived = { data ->
                BleResponse.parse(data)?.let { response ->
                    handleBleResponse(response)
                }
            }
        )
    }

    private fun handleConnectionStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.ServicesDiscovered -> {
                _state.value = BleState.Connected
                // Esperamos hasta que el servicio UART esté configurado
                viewModelScope.launch {
                    try {
                        withTimeout(5000) { // 5 segundos de timeout
                            bleManager.waitForConnection()
                            _isReadyToSend.value = true
                            pendingConfiguration?.let { config ->
                                sendPendingConfiguration(config)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error esperando configuración UART", e)
                        _state.value = BleState.Error("Error configurando servicio UART")
                    }
                }
            }
            is BleConnectionState.Disconnected -> {
                _isReadyToSend.value = false
                handleDisconnection()
            }
            is BleConnectionState.Error -> {
                _isReadyToSend.value = false
                _state.value = BleState.Error(state.message)
            }
            else -> {}
        }
    }

    private fun handleDisconnection() {
        when (_state.value) {
            is BleState.ConfigurationReceived -> {
                _state.value = BleState.AttemptingWifiConnection
                currentConfiguration?.let { config ->
                    monitorPanelConfiguration(config.clientId, config.panelId)
                }
            }
            is BleState.WifiConnected,
            is BleState.ConfigurationSuccess,
            is BleState.DataSent -> {
                // Desconexión esperada
            }
            else -> {
                _state.value = BleState.Error("Conexión perdida con el dispositivo")
            }
        }
    }

    private fun handleBleResponse(response: BleResponse) {
        when (response.status) {
            BleConstants.ResponseValues.RECEIVED -> {
                _state.value = BleState.ConfigurationReceived
            }
            BleConstants.ResponseValues.WIFI_CONNECTED -> {
                _state.value = BleState.WifiConnected
            }
            BleConstants.ResponseValues.SUCCESS -> {
                handleSuccessfulConfiguration(response.ip)
            }
            BleConstants.ResponseValues.ERROR -> {
                _state.value = BleState.ConfigurationError(
                    response.message ?: "Error desconocido en la configuración"
                )
            }
        }
    }

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
                _state.value = BleState.CreatingPanel
                updateConfigurationProgress(30)

                val panel = createPanel(wifiSsid, wifiPassword, panelName, panelLocation, clientId)
                currentPanel = panel
                val panelId = panelRepository.createNewPanel(clientId, panel).getOrThrow()

                _state.value = BleState.PanelCreated
                updateConfigurationProgress(50)

                val config = BleConfiguration(
                    clientId = clientId,
                    clientName = clientName,
                    panelId = panelId,
                    panelName = panelName,
                    panelLocation = panelLocation,
                    wifiSsid = wifiSsid,
                    wifiPassword = wifiPassword
                )

                currentConfiguration = config
                pendingConfiguration = config

                if (_isReadyToSend.value) {
                    sendPendingConfiguration(config)
                } else {
                    deviceToConnect?.let { device ->
                        connectToDevice(device)
                    } ?: run {
                        throw Exception("No hay dispositivo BLE seleccionado")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en configuración", e)
                _state.value = BleState.Error(e.message ?: "Error desconocido")
                updateConfigurationProgress(0)
            }
        }
    }

    private fun sendPendingConfiguration(config: BleConfiguration) {
        viewModelScope.launch {
            try {
                _state.value = BleState.WaitingForConnection

                withTimeout(10000) { // 10 segundos de timeout
                    _isReadyToSend.first { it }
                }

                if (!bleManager.isConnected()) {
                    throw Exception("No hay conexión BLE activa")
                }

                val configMessage = config.toBleMessage()
                Log.d(TAG, "Enviando configuración: $configMessage")

                delay(500) // Pequeña pausa para estabilizar la conexión

                if (!bleManager.sendData(configMessage)) {
                    throw Exception("Error enviando configuración al ESP32")
                }
                pendingConfiguration = null
                updateConfigurationProgress(70)
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando configuración", e)
                _state.value = BleState.Error(e.message ?: "Error enviando configuración")
                updateConfigurationProgress(0)
            }
        }
    }

    private fun createPanel(
        wifiSsid: String,
        wifiPassword: String,
        panelName: String,
        panelLocation: String,
        clientId: String
    ): Panel {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
        return Panel(
            documentName = "",
            name = panelName.trim(),
            location = panelLocation.trim(),
            SSID = wifiSsid.trim(),
            SSID_PW = wifiPassword.trim(),
            ESP32_IP = "",
            clientName = clientId,
            SSID_CON = "DISC",
            relays = listOf(
                Relay(name = "Alarma", status = "DISC", date_time = now),
                Relay(name = "Problema", status = "DISC", date_time = now),
                Relay(name = "Supervision", status = "DISC", date_time = now)
            ),
            overallStatus = "DISC"
        )
    }

    private fun handleSuccessfulConfiguration(ip: String?) {
        viewModelScope.launch {
            try {
                currentPanel?.let { panel ->
                    currentConfiguration?.let { config ->
                        if (!ip.isNullOrBlank()) {
                            val updatedPanel = panel.copy(
                                ESP32_IP = ip,
                                SSID_CON = "OK",
                                overallStatus = "OK"
                            )
                            panelRepository.updatePanel(config.clientId, updatedPanel)
                        }

                        _state.value = BleState.ConfigurationSuccess
                        updateConfigurationProgress(90)
                        delay(1000)  // Breve pausa para que el usuario vea el estado de éxito
                        _state.value = BleState.DataSent
                        updateConfigurationProgress(100)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando panel", e)
                _state.value = BleState.Error("Error actualizando panel en la base de datos")
                updateConfigurationProgress(0)
            }
        }
    }

    private fun updateConfigurationProgress(progress: Int) {
        _configurationProgress.value = progress
    }

    private fun monitorPanelConfiguration(clientId: String, panelId: String) {
        panelObserverJob?.cancel()
        panelObserverJob = viewModelScope.launch {
            try {
                var attempts = 0
                panelRepository.observePanelUpdates(clientId, panelId)
                    .collect { panel ->
                        when {
                            panel == null -> throw Exception("Panel no encontrado")
                            panel.SSID_CON == "OK" && panel.ESP32_IP.isNotBlank() -> {
                                _state.value = BleState.ConfigurationSuccess
                                updateConfigurationProgress(100)
                                panelObserverJob?.cancel()
                            }
                            panel.SSID_CON == "DISC" -> {
                                attempts++
                                if (attempts >= BleConstants.MAX_RETRY_ATTEMPTS) {
                                    throw Exception("El panel no logró conectarse después de varios intentos")
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoreando panel", e)
                _state.value = BleState.Error(e.message ?: "Error desconocido")
                updateConfigurationProgress(0)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        panelObserverJob?.cancel()
        bleManager.disconnect()
    }
}