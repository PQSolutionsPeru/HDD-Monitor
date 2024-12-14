package com.pqsolutions.hdd_monitor.presentation.state

sealed class BleState {
    data object Initial : BleState()
    data object Scanning : BleState()
    data object Connecting : BleState()
    data object Connected : BleState()
    data object Disconnected : BleState()
    data object WifiConfigReceived : BleState()
    data object WifiConnected : BleState()
    data object WaitingPanelConfig : BleState()
    data object ConfigurationSuccess : BleState()
    data class ConfigurationError(val message: String) : BleState()
    data class Error(val message: String) : BleState()
    data class RequiresPermission(
        val permissions: List<String>,
        val onPermissionGranted: () -> Unit
    ) : BleState()
}