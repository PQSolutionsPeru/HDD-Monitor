package com.pqsolutions.hdd_monitor.presentation.state

import com.pqsolutions.hdd_monitor.bluetooth.BleConnectionState

sealed class BleState {
    data object Initial : BleState()
    data object Scanning : BleState()
    data object Connecting : BleState()
    data object Connected : BleState()
    data object Disconnected : BleState()
    object WaitingForConnection : BleState()
    data object ConfigurationReceived : BleState()
    data object AttemptingWifiConnection : BleState()
    data object WifiConnected : BleState()
    data object CreatingPanel : BleState()
    data object PanelCreated : BleState()
    data object ConfigurationSuccess : BleState()
    data object DataSent : BleState()
    data class ConfigurationError(val message: String) : BleState() {
        companion object {
            fun fromResponse(response: String): ConfigurationError = when {
                response.contains("wifi_connection_failed") ->
                    ConfigurationError("No se pudo conectar a la red WiFi. Verifica el SSID y contraseña.")
                response.contains("mqtt_connection_failed") ->
                    ConfigurationError("Error conectando al servidor MQTT.")
                else -> ConfigurationError(response)
            }
        }
    }
    data class Error(val message: String) : BleState()

    fun isTerminalState(): Boolean = when (this) {
        is DataSent, is ConfigurationError, is Error -> true
        else -> false
    }

    fun isErrorState(): Boolean = this is ConfigurationError || this is Error

    companion object {
        fun fromConnectionState(state: BleConnectionState): BleState = when (state) {
            BleConnectionState.Connecting -> Connecting
            BleConnectionState.Connected -> Connected
            BleConnectionState.Disconnected -> Disconnected
            BleConnectionState.ServicesDiscovered -> Connected
            is BleConnectionState.Error -> Error(state.message)
        }
    }
}