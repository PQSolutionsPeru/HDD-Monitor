package com.pqsolutions.hdd_monitor.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var currentGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null
    private var isReconnecting = false
    private var pendingData: String? = null
    private val connectionLock = Mutex()
    private var currentGattCallback: BleGattCallback? = null

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _uartConfigured = MutableStateFlow(false)
    private val _readyToSend = MutableStateFlow(false)

    companion object {
        private const val TAG = "BleManager"
        private const val DEVICE_NAME = "ESP32-Monitor"
        private const val SCAN_TIMEOUT = 10000L
        private const val MAX_CHUNK_SIZE = 18
        private const val WRITE_DELAY = 100L
        private const val MAX_RETRIES = 3

        private val PERMISSIONS_S = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        private val PERMISSIONS_BASE = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun checkPermissions(): Boolean {
        val permissionsToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSIONS_S
        } else {
            PERMISSIONS_BASE
        }

        val hasPermissions = permissionsToCheck.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, """
            Verificando permisos BLE:
            SDK Version: ${Build.VERSION.SDK_INT}
            Permisos requeridos: ${permissionsToCheck.joinToString()}
            Tiene permisos: $hasPermissions
        """.trimIndent())

        return hasPermissions
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        device: BluetoothDevice,
        onStateChange: (BleConnectionState) -> Unit,
        onDataReceived: (String) -> Unit
    ) {
        if (!checkPermissions()) {
            onStateChange(BleConnectionState.Error("Se requieren permisos de Bluetooth"))
            return
        }

        coroutineScope.launch {
            connectionLock.withLock {
                try {
                    disconnect()

                    currentGattCallback = BleGattCallback(
                        scope = coroutineScope,
                        onStateChange = { state ->
                            _connectionState.value = state
                            onStateChange(state)
                            when (state) {
                                is BleConnectionState.ServicesDiscovered -> {
                                    if (checkPermissions()) {
                                        setupUartService(currentGatt!!)
                                        pendingData?.let { data ->
                                            coroutineScope.launch {
                                                delay(500)
                                                sendData(data)
                                                pendingData = null
                                            }
                                        }
                                    }
                                }
                                is BleConnectionState.Disconnected -> {
                                    if (!isReconnecting && checkPermissions()) {
                                        isReconnecting = true
                                        Log.d(TAG, "Intentando reconexión...")
                                        coroutineScope.launch {
                                            delay(1000)
                                            connectToDevice(device, onStateChange, onDataReceived)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        },
                        onDataReceived = onDataReceived
                    )

                    if (checkPermissions()) {
                        currentGatt = device.connectGatt(context, false, currentGattCallback)
                    } else {
                        throw SecurityException("Permisos Bluetooth revocados durante la conexión")
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error de permisos conectando al dispositivo", e)
                    onStateChange(BleConnectionState.Error("Error de permisos: ${e.message}"))
                } catch (e: Exception) {
                    Log.e(TAG, "Error conectando al dispositivo", e)
                    onStateChange(BleConnectionState.Error("Error conectando: ${e.message}"))
                }
            }
        }
    }

    fun sendData(data: String): Boolean {
        if (!checkPermissions()) {
            Log.e(TAG, "No hay permisos para enviar datos")
            return false
        }

        if (!isConnected() || !_readyToSend.value) {
            Log.e(TAG, "No hay conexión BLE activa o no está lista para enviar")
            pendingData = data
            return false
        }

        var success = false
        runBlocking {
            try {
                Log.d(TAG, "Enviando datos: $data")
                var retryCount = 0

                while (retryCount < MAX_RETRIES && !success) {
                    if (!isConnected()) {
                        Log.e(TAG, "Conexión perdida durante el envío")
                        break
                    }

                    success = sendDataChunks(data)
                    if (!success) {
                        Log.d(TAG, "Reintento ${retryCount + 1} de $MAX_RETRIES")
                        withContext(Dispatchers.IO) {
                            delay(WRITE_DELAY * 2)
                        }
                        retryCount++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando datos", e)
                success = false
            }
        }

        return success
    }

    private suspend fun sendDataChunks(data: String): Boolean {
        if (!checkPermissions()) return false

        val chunks = data.chunked(MAX_CHUNK_SIZE)
        Log.d(TAG, "Dividiendo datos en ${chunks.size} chunks: $data")

        for ((index, chunk) in chunks.withIndex()) {
            val chunkToSend = if (index == chunks.size - 1) "$chunk\n" else chunk
            if (!sendSingleChunk(chunkToSend, index)) {
                Log.e(TAG, "Error enviando chunk $index")
                return false
            }
        }

        Log.d(TAG, "Todos los chunks enviados correctamente")
        return true
    }

    private suspend fun sendSingleChunk(chunk: String, index: Int): Boolean {
        var retryCount = 0
        while (retryCount < MAX_RETRIES) {
            try {
                val writeSuccess = writeCharacteristic(chunk)
                if (writeSuccess) {
                    val confirmationSuccess = currentGattCallback?.awaitWriteCompletion() ?: false
                    if (confirmationSuccess) {
                        Log.d(TAG, "Chunk $index enviado exitosamente")
                        return true
                    }
                }
                Log.d(TAG, "Reintentando envío de chunk $index (intento ${retryCount + 1})")
                retryCount++
                withContext(Dispatchers.IO) {
                    delay(WRITE_DELAY)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando chunk $index", e)
                retryCount++
                withContext(Dispatchers.IO) {
                    delay(WRITE_DELAY)
                }
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCharacteristic(data: String): Boolean {
        if (!checkPermissions()) return false

        return try {
            rxCharacteristic?.let { characteristic ->
                characteristic.value = data.toByteArray()
                if (!checkPermissions()) return false

                val result = currentGatt?.writeCharacteristic(characteristic) == true
                if (result) {
                    withContext(Dispatchers.IO) {
                        delay(WRITE_DELAY)
                    }
                    true
                } else {
                    Log.e(TAG, "Error escribiendo característica")
                    false
                }
            } ?: run {
                Log.e(TAG, "Característica RX no disponible")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos escribiendo característica", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo característica", e)
            false
        }
    }

    suspend fun waitForConnection() {
        try {
            if (!checkPermissions()) {
                throw SecurityException("Permisos Bluetooth no concedidos")
            }
            _connectionState.first { it is BleConnectionState.ServicesDiscovered }
            _uartConfigured.first { it }
            _readyToSend.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error esperando conexión", e)
            throw e
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupUartService(gatt: BluetoothGatt): Boolean {
        if (!checkPermissions()) return false

        try {
            Log.d(TAG, "Configurando servicio UART")
            _uartConfigured.value = false

            val service = gatt.getService(BleConstants.UART_SERVICE_UUID) ?: run {
                Log.e(TAG, "Servicio UART no encontrado")
                return false
            }

            rxCharacteristic = service.getCharacteristic(BleConstants.UART_RX_CHAR_UUID)
            txCharacteristic = service.getCharacteristic(BleConstants.UART_TX_CHAR_UUID)

            if (txCharacteristic == null || rxCharacteristic == null) {
                Log.e(TAG, "Características UART no encontradas")
                return false
            }

            if (!checkPermissions()) return false

            if (!gatt.setCharacteristicNotification(txCharacteristic, true)) {
                Log.e(TAG, "Error habilitando notificaciones")
                return false
            }

            val descriptor = txCharacteristic?.getDescriptor(
                UUID.fromString(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            ) ?: run {
                Log.e(TAG, "Descriptor no encontrado")
                return false
            }

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!checkPermissions()) return false

            if (!gatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "Error escribiendo descriptor")
                return false
            }

            _uartConfigured.value = true
            Log.d(TAG, "Servicio UART configurado exitosamente")
            return true

        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos configurando UART", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando UART", e)
            return false
        }
    }

    fun startScan(onDeviceFound: (BluetoothDevice) -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "Iniciando escaneo BLE...")

        if (!checkPermissions()) {
            val errorMsg = buildString {
                append("Se requieren permisos: ")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    append("BLUETOOTH_SCAN, BLUETOOTH_CONNECT")
                } else {
                    append("BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION")
                }
            }
            Log.e(TAG, errorMsg)
            onError(errorMsg)
            return
        }

        clearDevices()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val device = result.device
                    val deviceName = try {
                        device.name
                    } catch (e: SecurityException) {
                        null
                    }

                    Log.d(TAG, "Dispositivo encontrado - Nombre: $deviceName, Dirección: ${device.address}")

                    if (deviceName == DEVICE_NAME && !_devices.value.contains(device)) {
                        Log.d(TAG, "ESP32-Monitor encontrado: ${device.address}")
                        _devices.value = _devices.value + device
                        onDeviceFound(device)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error de permisos en escaneo", e)
                    onError("Error de permisos: ${e.message}")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorMsg = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "El escaneo ya está en progreso"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Error al registrar la aplicación"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Escaneo BLE no soportado"
                    SCAN_FAILED_INTERNAL_ERROR -> "Error interno del sistema"
                    else -> "Error desconocido: $errorCode"
                }
                Log.e(TAG, "Error en escaneo: $errorMsg")
                onError(errorMsg)
            }
        }

        try {
            Log.d(TAG, "Iniciando escaneo con scanner: ${bleScanner != null}")
            bleScanner?.startScan(null, scanSettings, scanCallback)
            android.os.Handler(context.mainLooper).postDelayed({
                stopScan()
            }, SCAN_TIMEOUT)
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos iniciando escaneo", e)
            onError("Error de permisos: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando escaneo", e)
            onError("Error iniciando escaneo: ${e.message}")
        }
    }

    fun stopScan() {
        if (!checkPermissions()) return

        try {
            scanCallback?.let { bleScanner?.stopScan(it) }
            scanCallback = null
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos deteniendo escaneo", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo escaneo", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!checkPermissions()) return

        try {
            _readyToSend.value = false
            _uartConfigured.value = false

            currentGatt?.let { gatt ->
                Log.d(TAG, "Desconectando GATT")
                if (checkPermissions()) {
                    gatt.disconnect()
                    gatt.close()
                }
            }
            currentGatt = null
            txCharacteristic = null
            rxCharacteristic = null
            isReconnecting = false
            currentGattCallback = null
            _connectionState.value = BleConnectionState.Disconnected
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos en desconexión", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error en desconexión", e)
        }
    }

    fun isBluetoothEnabled(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return try {
            adapter.isEnabled
        } catch (e: SecurityException) {
            Log.e(TAG, "Error verificando estado del Bluetooth", e)
            false
        }
    }

    fun clearDevices() {
        _devices.value = emptyList()
    }

    fun isConnected(): Boolean =
        _connectionState.value is BleConnectionState.ServicesDiscovered &&
                _uartConfigured.value &&
                currentGatt != null &&
                rxCharacteristic != null
}