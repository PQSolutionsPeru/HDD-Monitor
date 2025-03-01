package com.pqsolutions.hdd_monitor.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.pqsolutions.hdd_monitor.util.BlePermissionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private val _foundDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val foundDevices: StateFlow<Set<BluetoothDevice>> = _foundDevices.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                if (!BlePermissionHandler.allPermissionsGranted(context)) {
                    Log.e(TAG, "No hay permisos suficientes para procesar resultado del escaneo")
                    return
                }

                val device = result.device
                try {
                    val deviceName = if (BlePermissionHandler.checkBluetoothPermission(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.Manifest.permission.BLUETOOTH_CONNECT else android.Manifest.permission.BLUETOOTH)) {
                        device.name
                    } else null

                    // Log todos los dispositivos encontrados
                    Log.d(TAG, "Dispositivo encontrado - Nombre: $deviceName, MAC: ${device.address}, RSSI: ${result.rssi}")

                    if (deviceName?.startsWith("ESP32") == true) {
                        Log.d(TAG, "ESP32 encontrado y agregado: $deviceName")
                        _foundDevices.update { currentDevices ->
                            currentDevices + device
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error de permisos al acceder al nombre del dispositivo", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando resultado del escaneo", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMessage = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Escaneo ya iniciado"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Error de registro de aplicación"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Error interno"
                else -> "Error desconocido: $errorCode"
            }
            Log.e(TAG, "Error en escaneo BLE: $errorMessage")
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        Log.d(TAG, "Iniciando startScan()")
        if (!BlePermissionHandler.allPermissionsGranted(context)) {
            Log.e(TAG, "No hay permisos suficientes para iniciar el escaneo")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Escaneo ya en progreso, retornando")
            return
        }

        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.e(TAG, "Bluetooth no disponible o desactivado")
                return
            }

            Log.d(TAG, "Obteniendo scanner BLE")
            val localScanner = bluetoothAdapter.bluetoothLeScanner
            if (localScanner == null) {
                Log.e(TAG, "Scanner BLE no disponible")
                return
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            clearDevices()
            scanner = localScanner // Asignar el scanner después de todas las verificaciones
            isScanning = true

            try {
                scanner?.startScan(null, settings, scanCallback)
                Log.d(TAG, "Escaneo BLE iniciado exitosamente")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error al iniciar el escaneo: estado ilegal", e)
                isScanning = false
                scanner = null
                return
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando escaneo", e)
            isScanning = false
            scanner = null
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!BlePermissionHandler.allPermissionsGranted(context)) {
            Log.e(TAG, "No hay permisos suficientes para detener el escaneo")
            isScanning = false
            scanner = null
            return
        }

        try {
            Log.d(TAG, "Deteniendo escaneo BLE")
            val localScanner = scanner
            isScanning = false
            if (localScanner != null) {
                try {
                    localScanner.stopScan(scanCallback)
                    Log.d(TAG, "Escaneo BLE detenido exitosamente")
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Error al detener el escaneo: estado ilegal", e)
                }
            }
            scanner = null
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo escaneo", e)
        } finally {
            isScanning = false
            scanner = null
        }
    }

    fun clearDevices() {
        _foundDevices.value = emptySet()
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}