package com.pqsolutions.hdd_monitor.presentation.state

import com.pqsolutions.hdd_monitor.esp32.ESP32Device

sealed class BleState {
    data object Initial : BleState()
    data object Scanning : BleState()
    data object Connecting : BleState()
    data object Connected : BleState()
    data object Disconnected : BleState()
    data object WifiConfigReceived : BleState()
    data object WifiConfiguring : BleState() // Nuevo estado
    data class WifiConfigured(val esp32Device: ESP32Device) : BleState()
    data class SelectingClient(val esp32Device: ESP32Device) : BleState()
    data class CreatingPanel(val esp32Device: ESP32Device, val clientId: String) : BleState()
    data object ConfigurationSuccess : BleState()
    data class ConfigurationError(val message: String) : BleState()
    data class Error(val message: String) : BleState()
    data object WaitingForRunningMode : BleState()
    data class RequiresPermission(
        val permissions: List<String>,
        val onPermissionGranted: () -> Unit
    ) : BleState()

    data class DeviceFound(
        val esp32Device: ESP32Device,
        val clientName: String,
        val panelName: String,
        val location: String
    ) : BleState()

    data class WaitingDeviceConfirmation(
        val esp32Device: ESP32Device,
        val clientName: String,
        val panelName: String,
        val location: String
    ) : BleState()
}