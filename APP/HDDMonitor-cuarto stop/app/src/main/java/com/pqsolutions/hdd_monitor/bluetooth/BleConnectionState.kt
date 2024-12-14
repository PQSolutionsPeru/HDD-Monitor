package com.pqsolutions.hdd_monitor.bluetooth

sealed class BleConnectionState {
    data object Connecting : BleConnectionState()
    data object Connected : BleConnectionState()
    data object Disconnected : BleConnectionState()
    data object ServicesDiscovered : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}