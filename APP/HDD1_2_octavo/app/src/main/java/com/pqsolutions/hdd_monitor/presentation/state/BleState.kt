package com.pqsolutions.hdd_monitor.presentation.state

import com.pqsolutions.hdd_monitor.esp32.ESP32Device
import com.pqsolutions.hdd_monitor.presentation.components.ESP32ConfigStep

sealed class BleState {
    data object Initial : BleState()
    data object Scanning : BleState()
    data object Connecting : BleState()
    data object Connected : BleState()
    data object Disconnected : BleState()
    data object WifiConfigReceived : BleState()
    data object WifiConfiguring : BleState()
    data object MqttConfiguring : BleState()
    data class ConfigurationSuccess(val esp32Device: ESP32Device) : BleState()
    data class WifiConfigured(val esp32Device: ESP32Device) : BleState()
    data class SelectingClient(val esp32Device: ESP32Device) : BleState()
    data class CreatingPanel(val esp32Device: ESP32Device, val clientId: String) : BleState()
    data class ConfigurationError(val message: String) : BleState()
    data class Error(val message: String) : BleState()
    data object WaitingForRunningMode : BleState()
    data class RequiresPermission(
        val permissions: List<String>,
        val onPermissionGranted: () -> Unit
    ) : BleState()

    // BleState.kt - Añadir este nuevo estado
    data class ESP32ConfigurationProgress(
        val bleConnected: Boolean = false,
        val wifiConfigured: Boolean = false,
        val wifiError: String? = null,
        val mqttConnected: Boolean = false,
        val configReceived: Boolean = false,
        val relaysConfigured: Boolean = false,
        val currentStep: ESP32ConfigStep = ESP32ConfigStep.BLE_CONNECT,
        val esp32Device: ESP32Device? = null
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