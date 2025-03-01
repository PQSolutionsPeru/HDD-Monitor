package com.pqsolutions.hdd_monitor.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleConnector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var isReconnecting = false

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Initial)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Conectado al dispositivo GATT")
                    _connectionState.value = BleConnectionState.Connected

                    // Añadir delay antes de descubrir servicios
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (checkBluetoothPermission()) {
                            try {
                                gatt.discoverServices()
                            } catch (e: SecurityException) {
                                handleError("Error de permisos al descubrir servicios: ${e.message}")
                            }
                        } else {
                            handleError("Se requieren permisos de Bluetooth")
                        }
                    }, 2000) // 2 segundos de delay
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Desconectado del dispositivo GATT")
                    disconnect()
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            try {
                if (!checkBluetoothPermission()) {
                    Log.e(TAG, "Permiso Bluetooth no disponible")
                    handleError("Se requieren permisos de Bluetooth")
                    return
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "MTU cambiado a: $mtu")
                    gatt.discoverServices()
                } else {
                    Log.e(TAG, "Error cambiando MTU")
                    // Continuar con el MTU por defecto
                    gatt.discoverServices()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de permisos en onMtuChanged", e)
                handleError("Error de permisos: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error en onMtuChanged", e)
                handleError("Error: ${e.message}")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Handler(Looper.getMainLooper()).postDelayed({
                    findCharacteristics(gatt)
                }, 1000) // 1 segundo de delay
            } else {
                handleError("Error descubriendo servicios: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            Log.d(TAG, if (success) "Escritura exitosa" else "Escritura fallida")

            if (!success) {
                handleError("Error enviando configuración")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            try {
                val message = characteristic.value.toString(Charsets.UTF_8)
                Log.d(TAG, "Respuesta BLE recibida: $message")

                when {
                    message.startsWith("status:init") -> {
                        Log.d(TAG, "Recibido mensaje de inicialización del ESP32")
                        _connectionState.value = BleConnectionState.Connecting
                    }
                    message.startsWith("status:ready") -> {
                        Log.d(TAG, "ESP32 listo para configuración")
                        _connectionState.value = BleConnectionState.Connected
                    }
                    message.startsWith("status:wifi_connecting") -> {
                        Log.d(TAG, "ESP32 intentando conexión WiFi")
                        _connectionState.value = BleConnectionState.WifiConfiguring
                    }
                    message.startsWith("status:wifi_connected") -> {
                        Log.d(TAG, "ESP32 conectado a WiFi")
                        val parts = message.split(",")
                        val ip = parts.find { it.startsWith("ip:") }?.substringAfter("ip:") ?: ""
                        val esp32Id = parts.find { it.startsWith("id:") }?.substringAfter("id:") ?: ""
                        onWifiConfigSuccess?.invoke(ip, esp32Id)
                    }
                    message.startsWith("status:mqtt_connecting") -> {
                        Log.d(TAG, "ESP32 intentando conexión MQTT")
                        _connectionState.value = BleConnectionState.MqttConfiguring
                    }
                    message.startsWith("status:mqtt_connected") -> {
                        Log.d(TAG, "ESP32 conectado a MQTT")
                    }
                    message.startsWith("status:complete") -> {
                        Log.d(TAG, "Configuración completada exitosamente")
                    }
                    message.startsWith("error:wifi_failed") -> {
                        val errorMsg = message.substringAfter("error:wifi_failed:")
                            .takeIf { it.isNotBlank() } ?: "Error en configuración WiFi"
                        Log.e(TAG, "Error WiFi: $errorMsg")
                        onWifiConfigError?.invoke(errorMsg)
                    }
                    message.startsWith("error:") -> {
                        val errorMsg = message.substringAfter("error:")
                        Log.e(TAG, "Error recibido: $errorMsg")
                        onWifiConfigError?.invoke(errorMsg)
                    }
                    message.startsWith("status:cleanup") -> {
                        Log.d(TAG, "ESP32 iniciando limpieza")
                    }
                    message.startsWith("bye:closing_connection") -> {
                        Log.d(TAG, "ESP32 cerrando conexión")
                        // Dar tiempo para procesar mensajes pendientes antes de desconectar
                        Handler(Looper.getMainLooper()).postDelayed({
                            disconnect()
                        }, 2000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando mensaje BLE", e)
                onWifiConfigError?.invoke("Error procesando respuesta: ${e.message}")
            }
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun findCharacteristics(gatt: BluetoothGatt) {
        try {
            Log.d(TAG, "Buscando características UART...")

            // Verificar si ya tenemos las características configuradas
            if (writeCharacteristic != null && notifyCharacteristic != null) {
                Log.d(TAG, "Características ya configuradas, activando notificaciones...")
                gatt.setCharacteristicNotification(notifyCharacteristic!!, true)

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val descriptor = notifyCharacteristic!!.getDescriptor(CCCD_UUID)
                        if (descriptor == null) {
                            Log.e(TAG, "Descriptor CCCD no encontrado")
                            handleError("Descriptor CCCD no encontrado")
                            return@postDelayed
                        }

                        Log.d(TAG, "Habilitando notificaciones...")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Log.d(TAG, "Notificaciones reactivadas correctamente")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reactivando notificaciones", e)
                        handleError("Error reactivando notificaciones: ${e.message}")
                    }
                }, 1000)
                return
            }

            val service = gatt.getService(UART_SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Servicio UART no encontrado")
                handleError("Servicio UART no encontrado")
                return
            }

            Log.d(TAG, "Servicio UART encontrado, buscando características...")
            writeCharacteristic = service.getCharacteristic(UART_RX_CHAR_UUID)
            notifyCharacteristic = service.getCharacteristic(UART_TX_CHAR_UUID)

            if (writeCharacteristic == null) {
                Log.e(TAG, "Característica RX no encontrada")
                handleError("Característica RX no encontrada")
                return
            }

            if (notifyCharacteristic == null) {
                Log.e(TAG, "Característica TX no encontrada")
                handleError("Característica TX no encontrada")
                return
            }

            Log.d(TAG, "Características encontradas, configurando notificaciones...")
            gatt.setCharacteristicNotification(notifyCharacteristic!!, true)

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val descriptor = notifyCharacteristic!!.getDescriptor(CCCD_UUID)
                    if (descriptor == null) {
                        Log.e(TAG, "Descriptor CCCD no encontrado")
                        handleError("Descriptor CCCD no encontrado")
                        return@postDelayed
                    }

                    Log.d(TAG, "Habilitando notificaciones...")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Log.d(TAG, "Notificaciones configuradas correctamente")
                } catch (e: Exception) {
                    Log.e(TAG, "Error configurando notificaciones", e)
                    handleError("Error configurando notificaciones: ${e.message}")
                }
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error en findCharacteristics", e)
            handleError("Error configurando características BLE: ${e.message}")
        }
    }

    private fun handleError(message: String) {
        Log.e(TAG, message)
        _connectionState.value = BleConnectionState.Error(message)
        disconnect()
    }

    @SuppressLint("MissingPermission")
    fun sendWifiConfig(config: WifiConfig): Boolean {
        try {
            val message = """
                {START}
                {"ssid":"${config.ssid}","password":"${config.password}"}
                {END}
            """.trimIndent()

            return writeCharacteristic?.let { characteristic ->
                characteristic.value = message.toByteArray()
                val result = bluetoothGatt?.writeCharacteristic(characteristic) == true
                if (result) {
                    Log.d(TAG, "Configuración WiFi enviada exitosamente")
                } else {
                    Log.e(TAG, "Error enviando configuración WiFi")
                    onWifiConfigError?.invoke("Error enviando configuración")
                }
                result
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando configuración WiFi", e)
            onWifiConfigError?.invoke(e.message ?: "Error desconocido")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        try {
            _connectionState.value = BleConnectionState.Connecting
            var retryCount = 0
            var connected = false

            while (retryCount < 3 && !connected) {
                try {
                    bluetoothGatt = device.connectGatt(context, false, gattCallback)
                    connected = true
                    Log.d(TAG, "Conexión exitosa en intento ${retryCount + 1}")
                } catch (e: Exception) {
                    retryCount++
                    if (retryCount < 3) {
                        Log.d(TAG, "Reintentando conexión... Intento $retryCount")
                        Thread.sleep(2000)
                    } else {
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error conectando después de 3 intentos", e)
            _connectionState.value = BleConnectionState.Error(e.message ?: "Error de conexión")
            disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverServices() {
        Log.d(TAG, "Descubriendo servicios GATT...")
        try {
            if (checkBluetoothPermission()) {
                bluetoothGatt?.discoverServices()
            } else {
                handleError("Se requieren permisos Bluetooth para descubrir servicios")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al descubrir servicios", e)
            handleError("Error de permisos: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al descubrir servicios", e)
            handleError("Error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            writeCharacteristic = null
            notifyCharacteristic = null
            _connectionState.value = BleConnectionState.Disconnected
        } catch (e: Exception) {
            Log.e(TAG, "Error desconectando", e)
        }
    }

    // Callbacks para notificar eventos WiFi
    var onWifiConfigSuccess: ((String, String) -> Unit)? = null
    var onWifiConfigError: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "BleConnector"
        val UART_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}