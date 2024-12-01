package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BleViewModel @Inject constructor(
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "BleViewModel"
        val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val _state = MutableStateFlow<BleState>(BleState.Initial)
    val state: StateFlow<BleState> = _state

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                if (context.checkSelfPermission(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Manifest.permission.BLUETOOTH_CONNECT
                        else Manifest.permission.BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val device = result.device
                    val scanRecord = result.scanRecord
                    val deviceName = device.name ?: "Unknown"
                    val rssi = result.rssi

                    // Loggear todos los dispositivos encontrados para debug
                    Log.d(TAG, "Dispositivo encontrado: $deviceName (${device.address}), RSSI: $rssi dBm")

                    // Filtrar solo por el nombre ESP32-Monitor
                    if (deviceName.contains("ESP32-Monitor", ignoreCase = true)) {
                        Log.d(TAG, "¡ESP32-Monitor encontrado! RSSI: $rssi dBm")
                        val currentDevices = _devices.value
                        if (!currentDevices.contains(device)) {
                            _devices.value = currentDevices + device
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de permisos en onScanResult: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando resultado de escaneo: ${e.message}")
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { result -> onScanResult(0, result) }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Escaneo ya iniciado"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Error de registro de la aplicación"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada"
                SCAN_FAILED_INTERNAL_ERROR -> "Error interno"
                else -> "Error desconocido: $errorCode"
            }
            Log.e(TAG, "Escaneo fallido: $errorMsg")
            _state.value = BleState.Error(errorMsg)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d(TAG, "Conectado al dispositivo")
                    try {
                        if (context.checkSelfPermission(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    Manifest.permission.BLUETOOTH_CONNECT
                                else Manifest.permission.BLUETOOTH
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            gatt?.discoverServices()
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Error de permisos al descubrir servicios: ${e.message}")
                        _state.value = BleState.Error("Error de permisos al descubrir servicios")
                    }
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Desconectado del dispositivo")
                    _state.value = BleState.Disconnected
                    closeConnection()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            try {
                if (context.checkSelfPermission(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Manifest.permission.BLUETOOTH_CONNECT
                        else Manifest.permission.BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val uartService = gatt?.getService(UART_SERVICE_UUID)
                        if (uartService != null) {
                            rxCharacteristic = uartService.getCharacteristic(UART_RX_CHAR_UUID)
                            _state.value = BleState.Connected
                            Log.d(TAG, "Servicios UART encontrados")
                        } else {
                            _state.value = BleState.Error("Servicio UART no encontrado")
                            Log.e(TAG, "Servicio UART no encontrado")
                        }
                    } else {
                        _state.value = BleState.Error("Error descubriendo servicios")
                        Log.e(TAG, "Error descubriendo servicios: $status")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de permisos al descubrir servicios: ${e.message}")
                _state.value = BleState.Error("Error de permisos al descubrir servicios")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            try {
                if (context.checkSelfPermission(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Manifest.permission.BLUETOOTH_CONNECT
                        else Manifest.permission.BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Datos enviados correctamente")
                        _state.value = BleState.DataSent
                    } else {
                        Log.e(TAG, "Error enviando datos: $status")
                        _state.value = BleState.Error("Error enviando datos")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de permisos al escribir característica: ${e.message}")
                _state.value = BleState.Error("Error de permisos al enviar datos")
            }
        }
    }

    fun startScan() {
        viewModelScope.launch {
            try {
                if (!bluetoothAdapter.isEnabled) {
                    _state.value = BleState.Error("Bluetooth está desactivado")
                    return@launch
                }

                // Verificación explícita de permisos
                if (context.checkSelfPermission(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Manifest.permission.BLUETOOTH_SCAN
                        else Manifest.permission.BLUETOOTH
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    _state.value = BleState.Error("No se tienen los permisos necesarios")
                    return@launch
                }

                _devices.value = emptyList()
                _state.value = BleState.Scanning

                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    .setReportDelay(0)
                    .build()

                try {
                    if (context.checkSelfPermission(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                Manifest.permission.BLUETOOTH_SCAN
                            else Manifest.permission.BLUETOOTH
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothAdapter.bluetoothLeScanner?.startScan(
                            null,
                            settings,
                            scanCallback
                        )
                        Log.d(TAG, "Escaneo iniciado en modo general...")
                        stopScanAfterDelay()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error de permisos al iniciar escaneo: ${e.message}")
                    _state.value = BleState.Error("Error de permisos al iniciar escaneo")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando escaneo: ${e.message}")
                _state.value = BleState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    private fun stopScanAfterDelay() {
        viewModelScope.launch {
            delay(10000) // 10 segundos
            try {
                if (context.checkSelfPermission(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Manifest.permission.BLUETOOTH_SCAN
                        else Manifest.permission.BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
                    if (_devices.value.isEmpty()) {
                        _state.value = BleState.Error("No se encontraron dispositivos")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de permisos al detener escaneo: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error deteniendo escaneo: ${e.message}")
            }
        }
    }

    fun stopScan() {
        try {
            if (context.checkSelfPermission(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        Manifest.permission.BLUETOOTH_SCAN
                    else Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
                _state.value = BleState.Initial
                Log.d(TAG, "Escaneo detenido")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al detener escaneo: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo escaneo: ${e.message}")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        try {
            if (context.checkSelfPermission(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        Manifest.permission.BLUETOOTH_CONNECT
                    else Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val deviceName = device.name ?: "Dispositivo desconocido"
                Log.d(TAG, "Intentando conectar a: $deviceName")
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            } else {
                _state.value = BleState.Error("No se tienen permisos suficientes para conectar")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al conectar: ${e.message}")
            _state.value = BleState.Error("Error de permisos al conectar")
        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar: ${e.message}")
            _state.value = BleState.Error(e.message ?: "Error conectando al dispositivo")
        }
    }

    fun sendConfiguration(wifiSsid: String, wifiPassword: String, panelName: String, panelLocation: String) {
        try {
            if (context.checkSelfPermission(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        Manifest.permission.BLUETOOTH_CONNECT
                    else Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                viewModelScope.launch {
                    try {
                        val data = "ssid:$wifiSsid,password:$wifiPassword,panel_name:$panelName,panel_location:$panelLocation"
                        Log.d(TAG, "Enviando configuración: $data")

                        rxCharacteristic?.let { characteristic ->
                            characteristic.setValue(data)
                            bluetoothGatt?.writeCharacteristic(characteristic)
                        } ?: run {
                            _state.value = BleState.Error("No se encontró la característica de escritura")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enviando configuración: ${e.message}")
                        _state.value = BleState.Error("Error enviando configuración: ${e.message}")
                    }
                }
            } else {
                _state.value = BleState.Error("No se tienen permisos suficientes para enviar datos")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al enviar configuración: ${e.message}")
            _state.value = BleState.Error("Error de permisos al enviar configuración")
        }
    }

    private fun closeConnection() {
        try {
            if (context.checkSelfPermission(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        Manifest.permission.BLUETOOTH_CONNECT
                    else Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothGatt?.close()
                bluetoothGatt = null
                rxCharacteristic = null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al cerrar conexión: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando conexión: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        closeConnection()
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
}