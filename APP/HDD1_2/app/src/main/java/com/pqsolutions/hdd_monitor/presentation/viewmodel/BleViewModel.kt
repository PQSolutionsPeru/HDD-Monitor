package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.bluetooth.BleConnector
import com.pqsolutions.hdd_monitor.bluetooth.BleScanner
import com.pqsolutions.hdd_monitor.bluetooth.BleConnectionState
import com.pqsolutions.hdd_monitor.bluetooth.WifiConfig
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.data.ClientRepository
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import com.pqsolutions.hdd_monitor.util.BlePermissionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val clientRepository: ClientRepository
) : ViewModel() {

    private val _state = MutableStateFlow<BleState>(BleState.Initial)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients: StateFlow<List<Client>> = _clients.asStateFlow()

    private var scanJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        observeDevices()
        observeConnectionState()
        loadClients()
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

    private fun observeConnectionState() {
        viewModelScope.launch {
            bleConnector.connectionState.collect { connectionState ->
                when (connectionState) {
                    is BleConnectionState.Connected -> {
                        _state.value = BleState.Connected
                        timeoutJob?.cancel()
                    }
                    is BleConnectionState.Disconnected -> {
                        if (_state.value !is BleState.ConfigurationSuccess) {
                            _state.value = BleState.Disconnected
                        }
                    }
                    is BleConnectionState.Error -> {
                        _state.value = BleState.Error(connectionState.message)
                    }
                    else -> {} // Estados manejados en otro lugar
                }
            }
        }
    }

    fun startScan() {
        if (!hasRequiredPermissions()) {
            requestPermissions { startScan() }
            return
        }

        scanJob?.cancel()
        bleScanner.clearDevices()
        _state.value = BleState.Scanning

        scanJob = viewModelScope.launch {
            bleScanner.startScan()
            delay(SCAN_TIMEOUT)
            stopScan()

            if (_devices.value.isEmpty()) {
                _state.value = BleState.Error(
                    "No se encontraron dispositivos ESP32.\n" +
                            "Asegúrate que el dispositivo esté encendido y cercano."
                )
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
        if (!hasRequiredPermissions()) {
            requestPermissions { connectToDevice(device) }
            return
        }

        _state.value = BleState.Connecting
        startTimeoutTimer()
        bleConnector.connect(device)
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
                startTimeoutTimer(WIFI_CONFIG_TIMEOUT)
            }
        }
    }

    fun sendPanelConfiguration(
        panelName: String,
        panelLocation: String,
        clientName: String,
        clientId: String
    ) {
        viewModelScope.launch {
            _state.value = BleState.ConfigurationSuccess

            // Por ahora solo simulamos éxito
            delay(2000)
            _state.value = BleState.WaitingPanelConfig
        }
    }

    private fun startTimeoutTimer(timeout: Long = WIFI_CONFIG_TIMEOUT) {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(timeout)
            if (_state.value !is BleState.ConfigurationSuccess) {
                _state.value = BleState.Error("Timeout de operación")
                disconnect()
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

    override fun onCleared() {
        super.onCleared()
        disconnect()
        stopScan()
        scanJob?.cancel()
        timeoutJob?.cancel()
    }
}