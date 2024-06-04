package com.pqsolutions.hdd_monitor

data class Panel(
    val name: String = "",
    val location: String = "",
    var relays: Map<String, Relay> = emptyMap()
)
