package com.pqsolutions.hdd_monitor

import com.pqsolutions.hddmonitor.Relay

data class Panel(
    val name: String = "",
    val location: String = "",
    val status: String = "",
    val relays: Map<String, Relay> = emptyMap()
)
