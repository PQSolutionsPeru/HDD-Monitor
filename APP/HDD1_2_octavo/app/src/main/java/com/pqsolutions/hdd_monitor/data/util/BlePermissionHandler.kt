package com.pqsolutions.hdd_monitor.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object BlePermissionHandler {
    private const val TAG = "BlePermissionHandler"

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun allPermissionsGranted(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkBluetoothEnabled(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter?.isEnabled == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth status", e)
            false
        }
    }

    fun checkBluetoothPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun logPermissionStatus(context: Context) {
        getRequiredPermissions().forEach { permission ->
            val isGranted = checkBluetoothPermission(context, permission)
            Log.d(TAG, "Permiso $permission: ${if (isGranted) "CONCEDIDO" else "DENEGADO"}")
        }
        val bluetoothEnabled = checkBluetoothEnabled(context)
        Log.d(TAG, "Bluetooth habilitado: $bluetoothEnabled")
    }
}