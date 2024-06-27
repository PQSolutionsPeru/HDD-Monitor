package com.pqsolutions.hdd_monitor

data class Panel(
    val ESP32_IP: String = "",
    val SSID: String = "",
    val SSID_CON: String = "",
    val SSID_PW: String = "",
    val location: String = "",
    val name: String = "",
    val relays: List<Relay> = emptyList()
)
