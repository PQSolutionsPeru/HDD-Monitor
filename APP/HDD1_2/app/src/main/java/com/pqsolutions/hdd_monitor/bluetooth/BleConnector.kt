package com.pqsolutions.hdd_monitor.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
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

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val responseBuffer = StringBuilder()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Conectado al dispositivo GATT")
                    _connectionState.value = BleConnectionState.Connected

                    if (checkBluetoothPermission()) {
                        try {
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Error de permisos al descubrir servicios", e)
                            _connectionState.value = BleConnectionState.Error(
                                "Se requieren permisos de Bluetooth"
                            )
                            disconnect()
                        }
                    } else {
                        Log.e(TAG, "Permisos de Bluetooth no disponibles")
                        _connectionState.value = BleConnectionState.Error(
                            "Se requieren permisos de Bluetooth"
                        )
                        disconnect()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Desconectado del dispositivo GATT")
                    disconnect()
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                findCharacteristics(gatt)
            } else {
                Log.e(TAG, "Error descubriendo servicios: $status")
                disconnect()
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
                _connectionState.value = BleConnectionState.Error("Error enviando configuración")
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
                    message.startsWith("status:wifi_con") -> {
                        // Extraer información adicional del mensaje
                        val parts = message.split(",")
                        val ip = parts.find { it.startsWith("ip:") }?.substringAfter("ip:") ?: ""
                        val esp32Id = parts.find { it.startsWith("id:") }?.substringAfter("id:") ?: ""

                        Log.d(TAG, "WiFi configurado exitosamente. IP: $ip, ESP32 ID: $esp32Id")
                        onWifiConfigSuccess?.invoke(ip, esp32Id)
                    }
                    message.startsWith("error:wifi_failed") -> {
                        val errorMsg = message.substringAfter("error:wifi_failed:")
                            .takeIf { it.isNotBlank() } ?: "Error en configuración WiFi"
                        Log.e(TAG, "Error en configuración WiFi: $errorMsg")
                        onWifiConfigError?.invoke(errorMsg)
                    }
                    message.startsWith("bye:closing_con") -> {
                        Log.d(TAG, "ESP32 cerrando conexión")
                        disconnect()
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
            val service = gatt.getService(UART_SERVICE_UUID)
            writeCharacteristic = service?.getCharacteristic(UART_RX_CHAR_UUID)
            notifyCharacteristic = service?.getCharacteristic(UART_TX_CHAR_UUID)

            if (writeCharacteristic == null || notifyCharacteristic == null) {
                Log.e(TAG, "No se encontraron características necesarias")
                disconnect()
                return
            }

            // Habilitar notificaciones
            gatt.setCharacteristicNotification(notifyCharacteristic!!, true)
            val descriptor = notifyCharacteristic!!.getDescriptor(CCCD_UUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando características BLE", e)
            disconnect()
        }
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
                responseBuffer.clear()
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
    fun disconnect() {
        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            writeCharacteristic = null
            notifyCharacteristic = null
            _connectionState.value = BleConnectionState.Disconnected
            synchronized(responseBuffer) {
                responseBuffer.clear()
            }
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