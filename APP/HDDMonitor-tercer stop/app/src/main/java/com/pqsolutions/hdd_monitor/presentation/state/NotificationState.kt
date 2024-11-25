package com.pqsolutions.hdd_monitor.presentation.state

data class CombinedNotificationState(
    val totalCount: Int = 0,
    val hasUnread: Boolean = false,
    val eventsCount: Int = 0,
    val panelAlertsCount: Int = 0,
    val lastUpdate: Long = System.currentTimeMillis()
)