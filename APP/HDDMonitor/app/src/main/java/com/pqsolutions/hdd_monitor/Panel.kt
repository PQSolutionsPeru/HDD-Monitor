package com.pqsolutions.hdd_monitor

data class Panel(
    var id: String = "",
    var clientId: String = "", // Agregar este campo
    var name: String = "",
    var location: String = "",
    var ESP32_IP: String = "",
    var SSID: String = "",
    var SSID_CON: String = "",
    var SSID_PW: String = "",
    var relays: Map<String, Relay> = emptyMap()
)
