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

    private val activeListeners = mutableListOf<ListenerRegistration>()
    private val relayListeners = mutableMapOf<String, ListenerRegistration>()

    fun clearListeners() {
        Log.d(TAG, "Clearing all panel and relay listeners")
        synchronized(activeListeners) {
            activeListeners.forEach { listener ->
                try {
                    listener.remove()
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing panel listener", e)
                }
            }
            activeListeners.clear()
        }
        synchronized(relayListeners) {
            relayListeners.values.forEach { listener ->
                try {
                    listener.remove()
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing relay listener", e)
                }
            }
            relayListeners.clear()
        }
    }

    fun getPanels(clientDocName: String?): Flow<List<Panel>> = callbackFlow {
        Log.d(TAG, "getPanels called with clientDocName: $clientDocName")
        val currentPanels = mutableListOf<Panel>()
        val relayListeners = mutableMapOf<String, ListenerRegistration>()

        val registration = if (clientDocName != null) {
            // Para un cliente específico
            firestore.collection("$BASE_PATH/$clientDocName/panels")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error getting panels for client $clientDocName", error)
                        return@addSnapshotListener
                    }

                    snapshot?.let {
                        currentPanels.clear()
                        currentPanels.addAll(snapshot.documents.mapNotNull { doc ->
                            try {
                                val panel = doc.toObject(Panel::class.java)?.copy(
                                    documentName = doc.id,
                                    clientName = clientDocName,
                                    lastUpdate = doc.getLong("lastUpdate") ?: System.currentTimeMillis()
                                )

                                panel?.let {
                                    // Verificar y eliminar listener anterior si existe
                                    relayListeners[panel.documentName]?.remove()

                                    // Crear nuevo listener
                                    relayListeners[panel.documentName] = firestore
                                        .collection("$BASE_PATH/$clientDocName/panels/${panel.documentName}/relays")
                                        .addSnapshotListener { relaysSnapshot, relayError ->
                                            if (relayError != null) {
                                                Log.e(TAG, "Error fetching relays", relayError)
                                                return@addSnapshotListener
                                            }

                                            relaysSnapshot?.let { rs ->
                                                val updatedRelays = rs.documents.mapNotNull { relayDoc ->
                                                    try {
                                                        Relay.fromMap(relayDoc.data?.plus(mapOf("name" to relayDoc.id)) ?: emptyMap())
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Error converting relay", e)
                                                        null
                                                    }
                                                }
                                                panel.relays = updatedRelays
                                                trySend(currentPanels.toList())
                                            }
                                        }
                                }
                                panel
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting panel", e)
                                null
                            }
                        })
                        trySend(currentPanels.toList())
                    }
                }
        } else {
            // Para administrador (todos los clientes)
            Log.d(TAG, "Starting to fetch panels for admin")
            firestore.collection(BASE_PATH)
                .addSnapshotListener { clientSnapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error getting clients", error)
                        return@addSnapshotListener
                    }

                    clientSnapshot?.let { clients ->
                        currentPanels.clear()
                        clients.documents.forEach { clientDoc ->
                            val currentClientDocName = clientDoc.id
                            firestore.collection("$BASE_PATH/$currentClientDocName/panels")
                                .get()
                                .addOnSuccessListener { panelsSnapshot ->
                                    panelsSnapshot.documents.forEach { doc ->
                                        try {
                                            val panel = doc.toObject(Panel::class.java)?.copy(
                                                documentName = doc.id,
                                                clientName = currentClientDocName,
                                                lastUpdate = doc.getLong("lastUpdate") ?: System.currentTimeMillis()
                                            )

                                            panel?.let {
                                                // Verificar y eliminar listener anterior si existe
                                                relayListeners[panel.documentName]?.remove()

                                                // Crear nuevo listener
                                                relayListeners[panel.documentName] = firestore
                                                    .collection("$BASE_PATH/$currentClientDocName/panels/${panel.documentName}/relays")
                                                    .addSnapshotListener { relaysSnapshot, relayError ->
                                                        if (relayError != null) {
                                                            Log.e(TAG, "Error fetching relays", relayError)
                                                            return@addSnapshotListener
                                                        }

                                                        relaysSnapshot?.let { rs ->
                                                            val updatedRelays = rs.documents.mapNotNull { relayDoc ->
                                                                try {
                                                                    Relay.fromMap(relayDoc.data?.plus(mapOf("name" to relayDoc.id)) ?: emptyMap())
                                                                } catch (e: Exception) {
                                                                    Log.e(TAG, "Error converting relay", e)
                                                                    null
                                                                }
                                                            }
                                                            panel.relays = updatedRelays
                                                            trySend(currentPanels.toList())
                                                        }
                                                    }
                                                currentPanels.add(panel)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error converting panel", e)
                                        }
                                    }
                                    trySend(currentPanels.toList())
                                }
                        }
                    }
                }
        }

        awaitClose {
            Log.d(TAG, "Closing panel and relay listeners")
            try {
                registration.remove()
                relayListeners.values.forEach { it.remove() }
            } catch (e: Exception) {
                Log.e(TAG, "Error closing listeners", e)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun setupRelayListener(clientDocName: String, panel: Panel, currentPanels: MutableList<Panel>) {
        synchronized(relayListeners) {
            relayListeners[panel.documentName]?.remove()
            relayListeners[panel.documentName] = firestore
                .collection("$BASE_PATH/$clientDocName/panels/${panel.documentName}/relays")
                .addSnapshotListener { relaysSnapshot, relayError ->
                    if (relayError != null) {
                        if (relayError.message?.contains("PERMISSION_DENIED") == true) {
                            Log.w(TAG, "Permission denied for relays, removing listener")
                            relayListeners[panel.documentName]?.remove()
                            relayListeners.remove(panel.documentName)
                            return@addSnapshotListener
                        }
                        Log.e(TAG, "Error fetching relays", relayError)
                        return@addSnapshotListener
                    }

                    relaysSnapshot?.let { rs ->
                        val updatedRelays = rs.documents.mapNotNull { relayDoc ->
                            try {
                                Relay.fromMap(relayDoc.data?.plus(mapOf("name" to relayDoc.id)) ?: emptyMap())
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting relay", e)
                                null
                            }
                        }
                        panel.relays = updatedRelays
                    }
                }
        }
    }

    private fun setupClientPanelsListener(clientDocName: String, currentPanels: MutableList<Panel>) {
        val listener = firestore.collection("$BASE_PATH/$clientDocName/panels")
            .addSnapshotListener { panelSnapshot, error ->
                if (error != null) {
                    if (error.message?.contains("PERMISSION_DENIED") == true) {
                        Log.w(TAG, "Permission denied for client panels, cleaning up")
                        return@addSnapshotListener
                    }
                    Log.e(TAG, "Error getting panels for client", error)
                    return@addSnapshotListener
                }

                panelSnapshot?.documents?.forEach { doc ->
                    try {
                        val panel = doc.toObject(Panel::class.java)?.copy(
                            documentName = doc.id,
                            clientName = clientDocName,
                            lastUpdate = doc.getLong("lastUpdate") ?: System.currentTimeMillis()
                        )
                        panel?.let {
                            setupRelayListener(clientDocName, it, currentPanels)
                            currentPanels.add(it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting panel", e)
                    }
                }
            }
        synchronized(activeListeners) {
            activeListeners.add(listener)
        }
    }

    fun observePanelUpdates(clientDocName: String, panelDocName: String): Flow<Panel?> = callbackFlow {
        Log.d(TAG, "Starting panel updates observation for $clientDocName/$panelDocName")

        val registration = firestore.document("$BASE_PATH/$clientDocName/panels/$panelDocName")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing panel updates", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val panel = snapshot.toObject(Panel::class.java)?.copy(
                            documentName = snapshot.id,
                            clientName = clientDocName,
                            lastUpdate = snapshot.getLong("lastUpdate") ?: System.currentTimeMillis()
                        )

                        panel?.let { fetchRelaysForPanel(it) }
                        trySend(panel)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting panel", e)
                        close(e)
                    }
                } else {
                    trySend(null)
                }
            }

        awaitClose {
            Log.d(TAG, "Closing panel updates observation")
            registration.remove()
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchRelaysForPanel(panel: Panel): ListenerRegistration {
        return firestore.collection("$BASE_PATH/${panel.clientName}/panels/${panel.documentName}/relays")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching relays", error)
                    return@addSnapshotListener
                }

                snapshot?.let {
                    val relays = it.documents.mapNotNull { relayDoc ->
                        try {
                            Relay.fromMap(relayDoc.data?.plus(mapOf("name" to relayDoc.id)) ?: emptyMap())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting relay", e)
                            null
                        }
                    }
                    panel.relays = relays
                }
            }
    }

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

        val panelDocName = IdManager.generatePanelDocumentName(panel.name, clientDocName)
        Log.d(TAG, "Creating new panel: $panelDocName")

        // Ejecutar todo en una transacción
        firestore.runTransaction { transaction ->
            // Referencias
            val panelRef = firestore.document("$BASE_PATH/$clientDocName/panels/$panelDocName")
            val esp32Ref = esp32Id?.let {
                firestore.document("hdd-monitor/esp32/registered/$it")
            }

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

            // Crear panel
            transaction.set(panelRef, updatedPanel.toMap())

            // Crear relays
            updatedPanel.relays.forEach { relay ->
                val relayRef = panelRef.collection("relays").document(relay.name)
                transaction.set(relayRef, relay.toMap())
            }

            // Si hay ESP32, asignarlo
            esp32Ref?.let {
                transaction.update(it, mapOf(
                    "client_id" to clientDocName,
                    "panel_id" to panelDocName,
                    "lastUpdate" to com.google.firebase.Timestamp.now()
                ))
            }
        }.await()

        Log.d(TAG, "Panel created successfully with ID: $panelDocName")
        panelDocName

    }.onFailure { e ->
        Log.e(TAG, "Error creating panel", e)
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