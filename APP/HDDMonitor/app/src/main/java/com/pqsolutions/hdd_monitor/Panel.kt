package com.pqsolutions.hdd_monitor

data class Panel(
    var id: String = "",
    var name: String = "",
    var location: String = "",
    var ESP32_IP: String = "",
    var SSID: String = "",
    var SSID_CON: String = "",
    var SSID_PW: String = "",
    var alarmaStatus: String = "Unknown",
    var problemaStatus: String = "Unknown",
    var supervisionStatus: String = "Unknown"
)
