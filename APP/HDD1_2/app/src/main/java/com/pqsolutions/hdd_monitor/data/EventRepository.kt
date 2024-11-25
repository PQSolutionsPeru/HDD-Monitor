package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "EventRepository"
        private const val BASE_PATH = "hdd-monitor/accounts/clients"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    }

    private var eventListeners = mutableListOf<ListenerRegistration>()

    fun getAllEventsFlow(): Flow<List<Event>> = callbackFlow {
        if (auth.currentUser == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore.collectionGroup("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        trySend(emptyList())
                        close()
                        return@addSnapshotListener
                    }
                    Log.e(TAG, "Error getting events", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (auth.currentUser == null) {
                    trySend(emptyList())
                    close()
                    return@addSnapshotListener
                }

                snapshot?.let {
                    val events = it.documents.mapNotNull { doc ->
                        try {
                            val clientDocName = doc.reference.path
                                .split("/")
                                .find { segment -> segment.startsWith("client_") }

                            Event.fromMap(doc.data?.plus(mapOf(
                                "documentName" to doc.id,
                                "clientDocName" to (clientDocName ?: "")
                            )) ?: emptyMap())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document ${doc.id}: ${e.message}", e)
                            null
                        }
                    }
                    trySend(events)
                }
            }

        eventListeners.add(listenerRegistration)
        awaitClose {
            listenerRegistration.remove()
            eventListeners.remove(listenerRegistration)
        }
    }

    fun getEventsFlow(clientDocName: String): Flow<List<Event>> = callbackFlow {
        if (clientDocName.isEmpty() || auth.currentUser == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore
            .collection("$BASE_PATH/$clientDocName/events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        trySend(emptyList())
                        close()
                        return@addSnapshotListener
                    }
                    Log.e(TAG, "Error getting client events", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (auth.currentUser == null) {
                    trySend(emptyList())
                    close()
                    return@addSnapshotListener
                }

                snapshot?.let {
                    val events = it.documents.mapNotNull { doc ->
                        try {
                            Event.fromMap(doc.data?.plus(mapOf(
                                "documentName" to doc.id,
                                "clientDocName" to clientDocName
                            )) ?: emptyMap())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document ${doc.id}: ${e.message}", e)
                            null
                        }
                    }
                    trySend(events)
                }
            }

        eventListeners.add(listenerRegistration)
        awaitClose {
            listenerRegistration.remove()
            eventListeners.remove(listenerRegistration)
        }
    }


    /**
     * Crea nuevos eventos para una lista de clientes.
     */
    suspend fun createEvent(clientDocNames: List<String>, event: Event): Result<Unit> = runCatching {
        if (clientDocNames.isEmpty()) {
            throw IllegalArgumentException("Client document names list cannot be empty")
        }

        val batch = firestore.batch()
        val now = LocalDateTime.now().format(DATE_FORMATTER)

        for (clientDocName in clientDocNames) {
            val eventDocName = IdManager.generateEventDocumentName(clientDocName)
            Log.d(TAG, "Creating new event with document name: $eventDocName for client: $clientDocName")

            var panelName: String? = null
            event.panelDocName?.let { pDocName ->
                val panelDoc = firestore
                    .document("$BASE_PATH/$clientDocName/panels/$pDocName")
                    .get()
                    .await()
                panelName = panelDoc.getString("name")
            }

            val eventRef = firestore
                .collection("$BASE_PATH/$clientDocName/events")
                .document(eventDocName)

            val eventData = hashMapOf<String, Any?>(
                "title" to event.title,
                "text" to event.text,
                "status" to EventStatus.STATUS_PROGRAMADO,
                "date_time" to event.date_time,
                "lastUpdate" to now,
                "panelDocName" to event.panelDocName,
                "panelName" to panelName,
                "type" to event.type,
                "createdByUserId" to event.createdByUserId,
                "createdByUserRole" to event.createdByUserRole,
                "isRead" to false
            ).apply {
                values.removeAll { it == null }
            }

            batch.set(eventRef, eventData)
        }

        batch.commit().await()
        Log.d(TAG, "Events batch committed successfully")
    }

    /**
     * Actualiza un evento existente.
     */
    suspend fun updateEvent(clientDocName: String, event: Event): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || event.documentName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val eventsCollection = firestore.collection("$BASE_PATH/$clientDocName/events")
        Log.d(TAG, "Attempting to update event: ${event.documentName}")

        val eventDoc = eventsCollection.document(event.documentName)
        val snapshot = eventDoc.get().await()

        if (!snapshot.exists()) {
            throw IllegalStateException("El evento no existe: ${event.documentName}")
        }

        var panelName: String? = null
        event.panelDocName?.let { pDocName ->
            val panelDoc = firestore
                .document("$BASE_PATH/$clientDocName/panels/$pDocName")
                .get()
                .await()
            panelName = panelDoc.getString("name")
        }

        val eventData = event.toMap().toMutableMap().apply {
            this["panelName"] = panelName
            this["lastUpdate"] = LocalDateTime.now().format(DATE_FORMATTER)
        }

        eventDoc.update(eventData).await()
        Log.d(TAG, "Event updated successfully: ${event.documentName}")
    }

    /**
     * Actualiza el estado de un evento.
     */
    suspend fun updateEventStatus(
        clientDocName: String,
        eventDocName: String,
        newStatus: String,
        updatedByUserId: String,
        isAdmin: Boolean
    ): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || eventDocName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val now = LocalDateTime.now().format(DATE_FORMATTER)
        val eventDoc = firestore.document("$BASE_PATH/$clientDocName/events/$eventDocName")
        val snapshot = eventDoc.get().await()

        if (!snapshot.exists()) {
            throw IllegalStateException("El evento no existe: $eventDocName")
        }

        val currentStatus = snapshot.getString("status") ?: EventStatus.STATUS_PROGRAMADO
        if (!EventStatus.isValidTransition(currentStatus, newStatus)) {
            throw IllegalStateException("Transición de estado inválida: $currentStatus -> $newStatus")
        }

        val updates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "lastUpdate" to now,
            "isRead" to false
        )

        when (newStatus) {
            EventStatus.STATUS_ACEPTADO -> {
                updates["acceptedAt"] = now
                if (isAdmin) {
                    updates["adminAcceptDocName"] = updatedByUserId
                } else {
                    updates["userAcceptDocName"] = updatedByUserId
                }
            }
            EventStatus.STATUS_FINALIZADO -> {
                updates["finalizedAt"] = now
            }
        }

        eventDoc.update(updates).await()
        Log.d(TAG, "Event status updated successfully: $eventDocName to $newStatus")
    }

    /**
     * Marca un evento como leído.
     */
    suspend fun markEventAsRead(clientDocName: String, eventDocName: String): Result<Unit> = runCatching {
        firestore.document("$BASE_PATH/$clientDocName/events/$eventDocName")
            .update("isRead", true)
            .await()
    }

    /**
     * Elimina un evento.
     */
    suspend fun deleteEvent(clientDocName: String, eventDocName: String): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || eventDocName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val eventDoc = firestore.document("$BASE_PATH/$clientDocName/events/$eventDocName")
        val snapshot = eventDoc.get().await()

        if (!snapshot.exists()) {
            throw IllegalStateException("El evento no existe: $eventDocName")
        }

        val currentEvent = Event.fromMap(snapshot.data?.plus("documentName" to eventDocName) ?: emptyMap())

        if (!currentEvent.isProgramado) {
            throw IllegalStateException("Solo se pueden eliminar eventos en estado PROGRAMADO")
        }

        eventDoc.delete().await()
        Log.d(TAG, "Event deleted successfully: $eventDocName")
    }

    /**
     * Obtiene la lista de clientes.
     */
    suspend fun getClients(): List<Client> {
        return firestore.collection(BASE_PATH)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(Client::class.java)?.copy(documentName = doc.id)
            }
    }

    /**
     * Obtiene la lista de paneles de un cliente.
     */
    suspend fun getPanelsByClient(clientDocName: String): List<Panel> {
        return firestore.collection("$BASE_PATH/$clientDocName/panels")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(Panel::class.java)?.copy(documentName = doc.id)
            }
    }

    fun clearListeners() {
        eventListeners.forEach { it.remove() }
        eventListeners.clear()
    }
}