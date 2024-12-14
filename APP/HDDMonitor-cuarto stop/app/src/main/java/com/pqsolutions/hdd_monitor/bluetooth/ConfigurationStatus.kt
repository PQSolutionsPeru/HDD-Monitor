package com.pqsolutions.hdd_monitor.bluetooth

sealed class ConfigurationStatus {
    data object Initial : ConfigurationStatus()
    data object CreatingPanel : ConfigurationStatus()
    data object PanelCreated : ConfigurationStatus()
    data object SendingConfiguration : ConfigurationStatus()
    data object ConfigurationReceived : ConfigurationStatus()
    data object WifiConnecting : ConfigurationStatus()
    data object WifiConnected : ConfigurationStatus()
    data object Completed : ConfigurationStatus()
    data class Error(val message: String) : ConfigurationStatus()
}