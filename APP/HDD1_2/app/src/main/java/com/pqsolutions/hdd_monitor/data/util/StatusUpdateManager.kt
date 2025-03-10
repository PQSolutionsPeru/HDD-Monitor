package com.pqsolutions.hdd_monitor.util

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manager centralizado para actualizaciones de estado en tiempo real.
 * Permite que múltiples componentes reciban notificaciones de cambios.
 */
object StatusUpdateManager {
    private const val TAG = "StatusUpdateManager"

    // Usar MutableSharedFlow con replay para que los nuevos suscriptores reciban las actualizaciones más recientes
    private val _statusUpdates = MutableSharedFlow<StatusUpdate>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Flow público para suscribirse a las actualizaciones
    val statusUpdates = _statusUpdates.asSharedFlow()

    /**
     * Emite una actualización de estado ESP32
     */
    suspend fun emitEsp32StatusUpdate(panelDocName: String, newStatus: String) {
        Log.d(TAG, "Emitiendo actualización ESP32: $panelDocName -> $newStatus")
        _statusUpdates.emit(
            StatusUpdate(
                panelDocName = panelDocName,
                newStatus = newStatus,
                isEsp32 = true
            )
        )
    }

    /**
     * Emite una actualización de estado de relay
     */
    suspend fun emitRelayStatusUpdate(panelDocName: String, relayName: String, newStatus: String) {
        Log.d(TAG, "Emitiendo actualización relay: $panelDocName, $relayName -> $newStatus")
        _statusUpdates.emit(
            StatusUpdate(
                panelDocName = panelDocName,
                relayName = relayName,
                newStatus = newStatus,
                isEsp32 = false
            )
        )
    }
}

/**
 * Modelo de datos para actualizaciones de estado
 */
data class StatusUpdate(
    val panelDocName: String,
    val newStatus: String,
    val relayName: String = "",
    val isEsp32: Boolean
)