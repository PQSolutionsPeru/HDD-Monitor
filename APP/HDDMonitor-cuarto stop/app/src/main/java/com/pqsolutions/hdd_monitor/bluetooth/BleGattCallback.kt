package com.pqsolutions.hdd_monitor.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class BleGattCallback(
    private val scope: CoroutineScope,
    private val onStateChange: (BleConnectionState) -> Unit,
    private val onDataReceived: (String) -> Unit
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "BleGattCallback"
    }

    private val writeCompletionChannel = Channel<Boolean>(Channel.CONFLATED)

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.d(TAG, "Conectado a GATT server")
                onStateChange(BleConnectionState.Connected)
                gatt.discoverServices()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.d(TAG, "Desconectado de GATT server")
                onStateChange(BleConnectionState.Disconnected)
                safeClose(gatt)
            }
            else -> {
                Log.d(TAG, "Estado de conexión inesperado: $newState")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStateChange(BleConnectionState.Error("Error de conexión: $status"))
                    safeClose(gatt)
                }
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Servicios GATT descubiertos")
            onStateChange(BleConnectionState.ServicesDiscovered)
        } else {
            Log.e(TAG, "Error descubriendo servicios: $status")
            onStateChange(BleConnectionState.Error("Error descubriendo servicios"))
            safeDisconnect(gatt)
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        try {
            val value = characteristic.value.toString(Charsets.UTF_8).trim()
            Log.d(TAG, "Datos recibidos: $value")
            onDataReceived(value)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando datos recibidos", e)
            onStateChange(BleConnectionState.Error("Error procesando datos: ${e.message}"))
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Característica escrita exitosamente")
            scope.launch {
                writeCompletionChannel.send(true)
            }
        } else {
            Log.e(TAG, "Error en escritura de característica: $status")
            scope.launch {
                writeCompletionChannel.send(false)
                onStateChange(BleConnectionState.Error("Error enviando datos al dispositivo"))
            }
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "Error en escritura de descriptor: $status")
            onStateChange(BleConnectionState.Error("Error configurando notificaciones"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDisconnect(gatt: BluetoothGatt) {
        try {
            gatt.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeClose(gatt: BluetoothGatt) {
        try {
            gatt.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar GATT", e)
        }
    }

    suspend fun awaitWriteCompletion(): Boolean {
        return writeCompletionChannel.receive()
    }
}