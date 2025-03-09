package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.esp32.ESP32Device
import com.pqsolutions.hdd_monitor.esp32.ESP32Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import com.google.firebase.firestore.DocumentChange

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
                        // Procesar cambios, incluyendo eliminaciones
                        for (change in snapshot.documentChanges) {
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    // Panel fue eliminado de Firestore
                                    val docId = change.document.id
                                    Log.d(TAG, "Panel eliminado detectado: $docId")

                                    // Eliminar el panel de la lista actual
                                    val panelIndex = currentPanels.indexOfFirst { it.documentName == docId }
                                    if (panelIndex >= 0) {
                                        // Eliminar listener de relays si existe
                                        relayListeners[docId]?.remove()
                                        relayListeners.remove(docId)

                                        // Eliminar el panel
                                        currentPanels.removeAt(panelIndex)

                                        // Enviar la lista actualizada sin el panel eliminado
                                        trySend(currentPanels.toList())
                                        Log.d(TAG, "Panel ${docId} eliminado de la lista actual")
                                    }
                                }
                                else -> {
                                    // Continuar con el procesamiento normal para ADDED y MODIFIED
                                }
                            }
                        }

                        // Procesar todos los paneles primero
                        val panelsList = snapshot.documents.mapNotNull { doc ->
                            try {
                                val panel = doc.toObject(Panel::class.java)?.copy(
                                    documentName = doc.id,
                                    clientName = clientDocName,
                                    lastUpdate = doc.getLong("lastUpdate") ?: System.currentTimeMillis()
                                )
                                panel
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting panel", e)
                                null
                            }
                        }

                        // Actualizar la lista actual con los paneles existentes
                        currentPanels.clear()
                        currentPanels.addAll(panelsList)
                        trySend(currentPanels.toList())

                        // Luego procesar cada panel para actualizar su estado ESP32
                        panelsList.forEach { panel ->
                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    // Actualizar el estado ESP32
                                    val updatedPanel = updatePanelWithESP32Status(panel, clientDocName)

                                    // Verificar y eliminar listener anterior si existe
                                    relayListeners[panel.documentName]?.remove()

                                    // Crear nuevo listener para relays
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

                                                // Encuentra el panel en la lista actual
                                                val index = currentPanels.indexOfFirst { it.documentName == panel.documentName }
                                                if (index >= 0) {
                                                    // Actualiza los relays del panel existente
                                                    currentPanels[index].relays = updatedRelays
                                                    // Enviar actualización después de procesar los relays
                                                    trySend(currentPanels.toList())
                                                }
                                            }
                                        }

                                    // Actualizar la lista y enviar cambios
                                    val index = currentPanels.indexOfFirst { it.documentName == panel.documentName }
                                    if (index >= 0) {
                                        currentPanels[index] = updatedPanel
                                        trySend(currentPanels.toList())
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error updating panel ESP32 status", e)
                                }
                            }
                        }
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

                    // Limpiar todos los paneles y obtener lista de clientes actualizada
                    currentPanels.clear()

                    clientSnapshot?.let { clients ->
                        // Si no hay clientes, enviar lista vacía
                        if (clients.isEmpty) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }

                        // Mantener conteo de clientes pendientes
                        val pendingClients = clients.size()
                        val clientsProcessed = AtomicInteger(0)

                        // Para cada cliente, obtener sus paneles
                        clients.documents.forEach { clientDoc ->
                            val currentClientDocName = clientDoc.id
                            firestore.collection("$BASE_PATH/$currentClientDocName/panels")
                                .addSnapshotListener { panelsSnapshot, panelsError ->
                                    if (panelsError != null) {
                                        Log.e(TAG, "Error getting panels for client $currentClientDocName", panelsError)

                                        // Incrementar contador aún en caso de error
                                        if (clientsProcessed.incrementAndGet() == pendingClients) {
                                            // Todos los clientes procesados, enviar resultado aunque sea con error
                                            trySend(currentPanels.toList())
                                        }

                                        return@addSnapshotListener
                                    }

                                    panelsSnapshot?.let { panels ->
                                        // Procesar cambios, incluyendo eliminaciones
                                        for (change in panels.documentChanges) {
                                            when (change.type) {
                                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                                    val docId = change.document.id
                                                    Log.d(TAG, "Panel eliminado detectado: $docId")

                                                    // Eliminar el panel de la lista actual
                                                    val panelIndex = currentPanels.indexOfFirst {
                                                        it.documentName == docId && it.clientName == currentClientDocName
                                                    }
                                                    if (panelIndex >= 0) {
                                                        // Eliminar listener de relays si existe
                                                        relayListeners["${currentClientDocName}_${docId}"]?.remove()
                                                        relayListeners.remove("${currentClientDocName}_${docId}")

                                                        // Eliminar el panel
                                                        currentPanels.removeAt(panelIndex)

                                                        // Enviar la lista actualizada
                                                        trySend(currentPanels.toList())
                                                        Log.d(TAG, "Panel ${docId} eliminado de la lista actual")
                                                    }
                                                }
                                                else -> {
                                                    // Continuar con el procesamiento normal para ADDED y MODIFIED
                                                }
                                            }
                                        }

                                        // Procesar paneles existentes
                                        val clientPanels = panels.documents.mapNotNull { doc ->
                                            try {
                                                val panel = doc.toObject(Panel::class.java)?.copy(
                                                    documentName = doc.id,
                                                    clientName = currentClientDocName,
                                                    lastUpdate = doc.getLong("lastUpdate") ?: System.currentTimeMillis()
                                                )
                                                panel
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error converting panel", e)
                                                null
                                            }
                                        }

                                        // Eliminar paneles antiguos de este cliente si existen
                                        currentPanels.removeAll { it.clientName == currentClientDocName }

                                        // Añadir los paneles actualizados a la lista
                                        currentPanels.addAll(clientPanels)

                                        // Enviar actualización
                                        trySend(currentPanels.toList())
                                        Log.d(TAG, "Cliente $currentClientDocName: ${clientPanels.size} paneles obtenidos")

                                        // Procesar cada panel para actualizar su estado ESP32
                                        clientPanels.forEach { panel ->
                                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    // Actualizar el estado ESP32
                                                    val updatedPanel = updatePanelWithESP32Status(panel, currentClientDocName)

                                                    // Crear ID único para el listener
                                                    val listenerId = "${currentClientDocName}_${panel.documentName}"

                                                    // Verificar y eliminar listener anterior si existe
                                                    relayListeners[listenerId]?.remove()

                                                    // Crear nuevo listener para relays
                                                    relayListeners[listenerId] = firestore
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

                                                                // Encuentra el panel en la lista actual
                                                                val index = currentPanels.indexOfFirst {
                                                                    it.documentName == panel.documentName &&
                                                                            it.clientName == currentClientDocName
                                                                }
                                                                if (index >= 0) {
                                                                    // Actualiza los relays del panel existente
                                                                    currentPanels[index].relays = updatedRelays
                                                                    // Enviar actualización
                                                                    trySend(currentPanels.toList())
                                                                }
                                                            }
                                                        }

                                                    // Actualizar la lista y enviar cambios
                                                    val index = currentPanels.indexOfFirst {
                                                        it.documentName == panel.documentName &&
                                                                it.clientName == currentClientDocName
                                                    }
                                                    if (index >= 0) {
                                                        currentPanels[index] = updatedPanel
                                                        trySend(currentPanels.toList())
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error updating panel ESP32 status", e)
                                                }
                                            }
                                        }

                                        // Incrementar contador de clientes procesados
                                        clientsProcessed.incrementAndGet()
                                    }
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

    private suspend fun updatePanelWithESP32Status(panel: Panel, clientDocName: String): Panel {
        if (panel.esp32_id.isEmpty()) {
            // Si no tiene ESP32 asignado, mantenerlo como OFFLINE
            panel.esp32Status = ESP32Device.STATUS_OFFLINE
            return panel
        }

        try {
            // Consultar el estado actual del ESP32 en Firestore
            val esp32Doc = firestore
                .collection("hdd-monitor/esp32/registered")
                .document(panel.esp32_id)
                .get()
                .await()

            if (esp32Doc.exists()) {
                // Obtener el estado actual
                val currentStatus = esp32Doc.getString("status") ?: ESP32Device.STATUS_OFFLINE

                // Verificar última actualización para determinar si está realmente OFFLINE
                val lastUpdate = esp32Doc.getTimestamp("lastUpdate")
                val currentTime = com.google.firebase.Timestamp.now()
                val fiveMinutesAgo = com.google.firebase.Timestamp(
                    currentTime.seconds - (5 * 60), // 5 minutos en segundos
                    currentTime.nanoseconds
                )

                // Si la última actualización es más antigua que 5 minutos, considerar OFFLINE
                panel.esp32Status = if (lastUpdate == null || lastUpdate.compareTo(fiveMinutesAgo) < 0) {
                    Log.d(TAG, "ESP32 ${panel.esp32_id} marcado como OFFLINE por inactividad")
                    ESP32Device.STATUS_OFFLINE
                } else if (currentStatus == ESP32Device.STATUS_RUNNING ||
                    currentStatus == ESP32Device.STATUS_ONLINE ||
                    currentStatus == ESP32Device.STATUS_CONFIGURED) {
                    Log.d(TAG, "ESP32 ${panel.esp32_id} está ONLINE con status $currentStatus")
                    ESP32Device.STATUS_ONLINE
                } else {
                    Log.d(TAG, "ESP32 ${panel.esp32_id} tiene status $currentStatus")
                    currentStatus
                }
            } else {
                Log.d(TAG, "No se encontró documento para ESP32 ${panel.esp32_id}")
                panel.esp32Status = ESP32Device.STATUS_OFFLINE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando estado de ESP32 ${panel.esp32_id}", e)
            panel.esp32Status = ESP32Device.STATUS_OFFLINE
        }

        return panel
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

        // Eliminar ESP32 si existe (en lugar de solo desasignarlo)
        val esp32Id = panelDoc.getString("esp32_id")
        if (!esp32Id.isNullOrEmpty()) {
            esp32Repository.deleteESP32(esp32Id)
                .onFailure { e ->
                    Log.e(TAG, "Error deleting ESP32", e)
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

        Log.d(TAG, "Panel, relays and ESP32 deleted successfully")
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