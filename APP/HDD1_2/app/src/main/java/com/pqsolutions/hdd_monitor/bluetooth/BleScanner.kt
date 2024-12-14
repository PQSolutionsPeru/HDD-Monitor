package com.pqsolutions.hdd_monitor.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _foundDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val foundDevices: StateFlow<Set<BluetoothDevice>> = _foundDevices.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name?.startsWith("ESP32-") == true) {
                val currentDevices = _foundDevices.value.toMutableSet()
                currentDevices.add(device)
                _foundDevices.value = currentDevices
                Log.d(TAG, "ESP32 encontrado: ${device.name}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Escaneo ya en progreso"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                    "Error registrando aplicación para escaneo"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                    "Bluetooth LE no soportado"
                else -> "Error desconocido: $errorCode"
            }
            Log.e(TAG, errorMsg)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        try {
            _foundDevices.value = emptySet()
            _isScanning.value = true

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bleScanner?.startScan(null, settings, scanCallback)
            Log.d(TAG, "Escaneo BLE iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando escaneo", e)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo escaneo", e)
        } finally {
            _isScanning.value = false
        }
    }

    fun clearDevices() {
        _foundDevices.value = emptySet()
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}