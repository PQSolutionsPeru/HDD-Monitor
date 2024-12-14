package com.pqsolutions.hdd_monitor.data.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.os.Build
import android.util.Log

object BlePermissionHandler {
    private const val TAG = "BlePermissionHandler"
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun hasRequiredPermissions(): Boolean {
        val context = requireContext()
        return getRequiredPermissions().all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    fun checkBluetoothEnabled(): Boolean {
        val context = requireContext()
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        return adapter?.isEnabled == true
    }

    fun logPermissionStatus() {
        val context = requireContext()
        getRequiredPermissions().forEach { permission ->
            val isGranted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permiso $permission: ${if (isGranted) "CONCEDIDO" else "DENEGADO"}")
        }
        val bluetoothEnabled = checkBluetoothEnabled()
        Log.d(TAG, "Bluetooth habilitado: $bluetoothEnabled")
    }

    private fun requireContext(): Context {
        return applicationContext ?: throw IllegalStateException(
            "BlePermissionHandler no ha sido inicializado. Llama a initialize() primero."
        )
    }
}