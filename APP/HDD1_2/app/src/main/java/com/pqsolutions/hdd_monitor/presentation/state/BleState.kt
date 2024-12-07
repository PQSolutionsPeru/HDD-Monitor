package com.pqsolutions.hdd_monitor.presentation.state

sealed class BleState {
    data object Initial : BleState()
    data object Scanning : BleState()
    data object Connected : BleState()
    data object Disconnected : BleState()
    data object ConfigurationReceived : BleState()
    data object AttemptingWifiConnection : BleState()
    data object PanelCreated : BleState()
    data object ConfigurationSuccess : BleState()
    data object DataSent : BleState()
    data class ConfigurationError(val message: String) : BleState()
    data class Error(val message: String) : BleState()
}