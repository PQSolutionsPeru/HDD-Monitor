package com.pqsolutions.hdd_monitor.presentation.theme

import androidx.compose.ui.graphics.Color

object PanelColors {
    val StatusOk = Color(0xFF4CAF50)      // Verde para OK
    val StatusDisc = Color(0xFFE53935)    // Rojo para DISC
    val StatusUnknown = Color(0xFFFFB300) // Amarillo para estados desconocidos

    val PanelBackgroundOk = Color(0xFFE8F5E9)    // Verde claro para fondo OK
    val PanelBackgroundDisc = Color(0xFFFFEBEE)  // Rojo claro para fondo DISC

    fun getStatusColor(status: String): Color = when (status) {
        com.pqsolutions.hdd_monitor.util.Constants.Status.OK -> StatusOk
        com.pqsolutions.hdd_monitor.util.Constants.Status.DISC -> StatusDisc
        else -> StatusUnknown
    }

    fun getPanelBackgroundColor(status: String): Color = when (status) {
        com.pqsolutions.hdd_monitor.util.Constants.Status.OK -> PanelBackgroundOk
        com.pqsolutions.hdd_monitor.util.Constants.Status.DISC -> PanelBackgroundDisc
        else -> PanelBackgroundDisc
    }
}