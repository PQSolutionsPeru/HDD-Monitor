package com.pqsolutions.hdd_monitor.bluetooth

data class WifiConfig(
    val ssid: String,
    val password: String
)

data class BleDevice(
    val address: String,
    val name: String
)

sealed class BleConnectionState {
    data object Disconnected : BleConnectionState()
    data object Connecting : BleConnectionState()
    data object Connected : BleConnectionState()
    data object WifiConfiguring : BleConnectionState()
    data object MqttConfiguring : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}