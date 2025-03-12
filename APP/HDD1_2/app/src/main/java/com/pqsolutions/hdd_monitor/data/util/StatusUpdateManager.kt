package com.pqsolutions.hdd_monitor.util

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await

/**
 * Manager centralizado para actualizaciones de estado en tiempo real.
 * Permite que múltiples componentes reciban notificaciones de cambios.
 */
object StatusUpdateManager {
    private const val TAG = "StatusUpdateManager"
    private const val BASE_PATH = "hdd-monitor/accounts/clients"
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

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

        // Obtener información del panel para incluir el nombre real
        val panelInfo = getPanelInfo(panelDocName)

        _statusUpdates.emit(
            StatusUpdate(
                panelDocName = panelDocName,
                panelName = panelInfo.second,  // Incluir el nombre real
                clientDocName = panelInfo.first,
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

        // Obtener información del panel para incluir el nombre real
        val panelInfo = getPanelInfo(panelDocName)

        _statusUpdates.emit(
            StatusUpdate(
                panelDocName = panelDocName,
                panelName = panelInfo.second,  // Incluir el nombre real
                clientDocName = panelInfo.first,
                relayName = relayName,
                newStatus = newStatus,
                isEsp32 = false
            )
        )
    }

    /**
     * Obtiene la información del panel (clientDocName, nombre real del panel)
     */
    private suspend fun getPanelInfo(panelDocName: String): Pair<String, String> {
        try {
            // Buscar el panel en todos los clientes
            val clientsCollection = firestore.collection(BASE_PATH).get().await()

            for (clientDoc in clientsCollection.documents) {
                val clientDocName = clientDoc.id
                val panelDoc = firestore.document("$BASE_PATH/$clientDocName/panels/$panelDocName").get().await()

                if (panelDoc.exists()) {
                    val panelName = panelDoc.getString("name") ?: ""
                    return Pair(clientDocName, panelName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener información del panel: $panelDocName", e)
        }

        return Pair("", "")
    }
}

/**
 * Modelo de datos para actualizaciones de estado
 */
data class StatusUpdate(
    val panelDocName: String,
    val panelName: String = "",  // Nombre real del panel
    val clientDocName: String = "", // Cliente al que pertenece
    val newStatus: String,
    val relayName: String = "",
    val isEsp32: Boolean
)