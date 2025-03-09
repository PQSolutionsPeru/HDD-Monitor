package com.pqsolutions.hdd_monitor.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Objeto simple para emitir y observar actualizaciones de estado de paneles en tiempo real.
 * Se usa para comunicar entre el FirebaseMessagingService y el DashboardViewModel.
 */
object StatusUpdateManager {
    private val _statusUpdates = MutableSharedFlow<StatusUpdate>(replay = 0)
    val statusUpdates = _statusUpdates.asSharedFlow()

    suspend fun emitUpdate(update: StatusUpdate) {
        _statusUpdates.emit(update)
    }
}

/**
 * Representa una actualización de estado para un panel o relay.
 */
data class StatusUpdate(
    val panelDocName: String,
    val newStatus: String,
    val isEsp32: Boolean = true,
    val relayName: String = ""
)