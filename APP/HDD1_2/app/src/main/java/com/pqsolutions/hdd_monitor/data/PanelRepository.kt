package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.esp32.ESP32Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class PanelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val esp32Repository: ESP32Repository
) {
    companion object {
        private const val TAG = "PanelRepository"
        private const val BASE_PATH = "hdd-monitor/accounts/clients"
    }

    fun getPanels(clientDocName: String?): Flow<List<Panel>> = callbackFlow {
        Log.d(TAG, "getPanels called with clientDocName: $clientDocName")

        val panelListeners = mutableMapOf<String, ListenerRegistration>()
        val currentPanels = mutableMapOf<String, Panel>()

        val clientsRef = firestore.collection(BASE_PATH)

        val clientListener = clientsRef.addSnapshotListener { clientSnapshot, clientError ->
            if (clientError != null) {
                Log.e(TAG, "Error listening to clients", clientError)
                close(clientError)
                return@addSnapshotListener
            }

            if (clientSnapshot != null) {
                for (clientDoc in clientSnapshot.documents) {
                    val currentClientDocName = clientDoc.id

                    if (clientDocName == null || currentClientDocName == clientDocName) {
                        val panelsRef = clientDoc.reference.collection("panels")
                        val panelListener = panelsRef.addSnapshotListener { panelSnapshot, panelError ->
                            if (panelError != null) {
                                Log.e(TAG, "Error listening to panels for client $currentClientDocName", panelError)
                                return@addSnapshotListener
                            }

                            if (panelSnapshot != null) {
                                for (panelDoc in panelSnapshot.documents) {
                                    val panel = panelDoc.toObject(Panel::class.java)?.copy(
                                        documentName = panelDoc.id,
                                        clientName = currentClientDocName,
                                        lastUpdate = panelDoc.getLong("lastUpdate") ?: System.currentTimeMillis()
                                    )

                                    if (panel != null) {
                                        Log.d(TAG, "Panel added/updated: ${panel.documentName}")
                                        currentPanels["${currentClientDocName}_${panel.documentName}"] = panel
                                        fetchRelaysForPanel(panel) { updatedPanel ->
                                            currentPanels["${currentClientDocName}_${updatedPanel.documentName}"] = updatedPanel
                                            trySend(currentPanels.values.toList())
                                        }
                                    }
                                }
                                trySend(currentPanels.values.toList())
                            }
                        }
                        panelListeners["client_$currentClientDocName"] = panelListener
                    }
                }
            }
        }

        awaitClose {
            Log.d(TAG, "Closing panel listeners")
            clientListener.remove()
            panelListeners.values.forEach { it.remove() }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun createNewPanel(
        clientDocName: String,
        panel: Panel,
        esp32Id: String? = null
    ): Result<String> = runCatching {
        if (!panel.isValid()) {
            throw IllegalArgumentException("Panel data is invalid")
        }

        // Verificar si el ESP32 ya está asignado a otro panel
        if (esp32Id != null) {
            val esp32Doc = firestore
                .collection("hdd-monitor/esp32/registered")
                .document(esp32Id)
                .get()
                .await()

            if (esp32Doc.exists()) {
                val existingPanelId = esp32Doc.getString("panel_id")
                if (!existingPanelId.isNullOrEmpty()) {
                    throw IllegalStateException("ESP32 ya está asignado a otro panel")
                }
            }
        }

        val panelDocName = IdManager.generatePanelDocumentName(clientDocName)
        Log.d(TAG, "Creating new panel: $panelDocName")

        val panelRef = firestore
            .document("$BASE_PATH/$clientDocName/panels/$panelDocName")

        // Crear panel con datos actualizados
        val updatedPanel = panel.copy(
            documentName = panelDocName,
            clientName = clientDocName,
            esp32_id = esp32Id ?: "",
            lastUpdate = System.currentTimeMillis(),
            relays = listOf(
                Relay(Panel.RELAY_ALARM, Panel.STATUS_DISC),
                Relay(Panel.RELAY_PROBLEM, Panel.STATUS_DISC),
                Relay(Panel.RELAY_SUPERVISION, Panel.STATUS_DISC)
            )
        )

        // Guardar panel
        panelRef.set(updatedPanel.toMap()).await()

        // Crear relays
        val batch = firestore.batch()
        updatedPanel.relays.forEach { relay ->
            val relayRef = panelRef.collection("relays").document(relay.name)
            batch.set(relayRef, relay.toMap())
        }
        batch.commit().await()

        // Si hay ESP32, asignarlo
        esp32Id?.let {
            esp32Repository.assignToPanelAndClient(it, clientDocName, panelDocName)
                .onFailure { e ->
                    Log.e(TAG, "Error assigning ESP32 to panel", e)
                    // Si falla la asignación, eliminar el panel
                    panelRef.delete().await()
                    throw e
                }
        }

        panelDocName
    }

    suspend fun updatePanel(
        clientDocName: String,
        panel: Panel,
        newEsp32Id: String? = null
    ): Result<Unit> = runCatching {
        if (!panel.isValid()) {
            throw IllegalArgumentException("Panel data is invalid")
        }

        val panelRef = firestore
            .document("$BASE_PATH/$clientDocName/panels/${panel.documentName}")

        // Manejar cambio de ESP32
        if (newEsp32Id != panel.esp32_id) {
            // Desasignar ESP32 anterior
            if (panel.esp32_id.isNotEmpty()) {
                esp32Repository.unassignFromPanel(panel.esp32_id)
                    .onFailure { e ->
                        Log.e(TAG, "Error unassigning previous ESP32", e)
                    }
            }

            // Asignar nuevo ESP32
            newEsp32Id?.let {
                esp32Repository.assignToPanelAndClient(it, clientDocName, panel.documentName)
                    .onFailure { e ->
                        Log.e(TAG, "Error assigning new ESP32", e)
                    }
            }
        }

        // Actualizar panel
        val updatedPanel = panel.copy(
            esp32_id = newEsp32Id ?: panel.esp32_id,
            lastUpdate = System.currentTimeMillis()
        )
        panelRef.set(updatedPanel.toMap()).await()
    }

    suspend fun updateRelayStatus(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        relayStatus: String
    ): Result<Unit> = runCatching {
        Log.d(TAG, "Updating relay $relayName to $relayStatus")

        val dateTime = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))

        val updateData = mapOf(
            "status" to relayStatus,
            "date_time" to dateTime,
            "lastUpdate" to System.currentTimeMillis()
        )

        firestore.document("$BASE_PATH/$clientDocName/panels/$panelDocName/relays/$relayName")
            .set(updateData)
            .await()

        Log.d(TAG, "Relay status updated successfully")
    }

    private fun fetchRelaysForPanel(panel: Panel, onUpdate: (Panel) -> Unit) {
        Log.d(TAG, "Fetching relays for panel ${panel.documentName}")

        firestore.collection("$BASE_PATH/${panel.clientName}/panels/${panel.documentName}/relays")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching relays", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val relays = snapshot.documents.mapNotNull { relayDoc ->
                        try {
                            Relay.fromMap(relayDoc.data?.plus(mapOf("name" to relayDoc.id)) ?: emptyMap())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting relay", e)
                            null
                        }
                    }

                    val updatedPanel = panel.copy(relays = relays)
                    onUpdate(updatedPanel)
                }
            }
    }

    fun observePanelUpdates(clientDocName: String, panelDocName: String): Flow<Panel?> = callbackFlow {
        val panelRef = firestore
            .document("$BASE_PATH/$clientDocName/panels/$panelDocName")

        val listenerRegistration = panelRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error observing panel updates", error)
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val panel = snapshot.toObject(Panel::class.java)?.copy(
                    documentName = snapshot.id,
                    clientName = clientDocName,
                    lastUpdate = snapshot.getLong("lastUpdate") ?: System.currentTimeMillis()
                )
                trySend(panel)
            } else {
                trySend(null)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun deletePanel(
        clientDocName: String,
        panelDocName: String
    ): Result<Unit> = runCatching {
        // Obtener panel
        val panelDoc = firestore
            .document("$BASE_PATH/$clientDocName/panels/$panelDocName")
            .get()
            .await()

        // Desasignar ESP32 si existe
        val esp32Id = panelDoc.getString("esp32_id")
        if (!esp32Id.isNullOrEmpty()) {
            esp32Repository.unassignFromPanel(esp32Id)
                .onFailure { e ->
                    Log.e(TAG, "Error unassigning ESP32", e)
                }
        }

        // Eliminar relays
        val relaysSnapshot = firestore
            .collection("$BASE_PATH/$clientDocName/panels/$panelDocName/relays")
            .get()
            .await()

        val batch = firestore.batch()
        relaysSnapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        // Eliminar panel
        batch.delete(panelDoc.reference)
        batch.commit().await()

        Log.d(TAG, "Panel and relays deleted successfully")
    }

    suspend fun verifyPanelExists(clientDocName: String, panelDocName: String): Boolean {
        return try {
            val panelDoc = firestore
                .document("$BASE_PATH/$clientDocName/panels/$panelDocName")
                .get()
                .await()
            panelDoc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying panel existence", e)
            false
        }
    }
}