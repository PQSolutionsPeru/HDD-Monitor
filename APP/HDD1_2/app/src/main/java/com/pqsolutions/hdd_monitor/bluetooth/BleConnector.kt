package com.pqsolutions.hdd_monitor.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
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

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Conectado al dispositivo GATT")
                    _connectionState.value = BleConnectionState.Connected
                    gatt.discoverServices()
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
                findWriteCharacteristic(gatt)
            } else {
                Log.e(TAG, "Error descubriendo servicios: $status")
                disconnect()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            Log.d(TAG, "Escritura ${if (success) "exitosa" else "fallida"}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        try {
            _connectionState.value = BleConnectionState.Connecting
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            Log.d(TAG, "Iniciando conexión a ${device.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error conectando", e)
            _connectionState.value = BleConnectionState.Error("Error de conexión: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            writeCharacteristic = null
            _connectionState.value = BleConnectionState.Disconnected
        } catch (e: Exception) {
            Log.e(TAG, "Error desconectando", e)
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
                characteristic.value = message.toByteArray()
                bluetoothGatt?.writeCharacteristic(characteristic) == true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando configuración WiFi", e)
            return false
        }
    }

    private fun findWriteCharacteristic(gatt: BluetoothGatt) {
        val service = gatt.getService(UART_SERVICE_UUID)
        writeCharacteristic = service?.getCharacteristic(UART_RX_CHAR_UUID)

        if (writeCharacteristic == null) {
            Log.e(TAG, "No se encontró característica de escritura")
            disconnect()
        }
    }

    companion object {
        private const val TAG = "BleConnector"
        val UART_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }
}