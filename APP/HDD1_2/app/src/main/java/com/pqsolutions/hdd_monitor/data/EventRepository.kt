package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Repositorio para manejar las operaciones de eventos en Firestore.
 *
 * @property firestore Instancia de FirebaseFirestore para acceder a la base de datos.
 */
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "EventRepository"
        private const val BASE_PATH = "hdd-monitor/accounts/clients"
    }

    /**
     * Obtiene un flujo de todos los eventos de todos los clientes.
     *
     * @return Flow<List<Event>> Flujo de lista de eventos.
     */
    fun getAllEventsFlow(): Flow<List<Event>> = callbackFlow {
        val listenerRegistration = firestore.collectionGroup("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting events", error)
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val events = it.documents.mapNotNull { doc ->
                        try {
                            val clientDocName = doc.reference.path
                                .split("/")
                                .find { segment -> segment.startsWith("client_") }

                            val eventType = doc.getString("type")
                            Log.d(TAG, "Reading event type from Firestore: $eventType for event ${doc.id}")

                            Event(
                                documentName = doc.id,
                                clientDocName = clientDocName ?: "",
                                panelDocName = doc.getString("panelDocName"),
                                panelName = doc.getString("panelName"),
                                title = doc.getString("title") ?: "",
                                text = doc.getString("text") ?: "",
                                status = doc.getString("status") ?: EventStatus.STATUS_PROGRAMADO,
                                date_time = doc.getString("date_time") ?: "",
                                userAcceptDocName = doc.getString("userAcceptDocName"),
                                type = eventType ?: "",  // Aseguramos que nunca sea null
                                createdByUserId = doc.getString("createdByUserId"),
                                createdByUserRole = doc.getString("createdByUserRole")
                            ).also { event ->
                                Log.d(TAG, "Created event object with type ${event.type}: ${event.toLogString()} from doc ${doc.reference.path}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document ${doc.id}: ${e.message}", e)
                            null
                        }
                    }
                    trySend(events)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Obtiene un flujo de eventos para un cliente específico.
     *
     * @param clientDocName Identificador del documento del cliente.
     * @return Flow<List<Event>> Flujo de lista de eventos del cliente.
     */
    fun getEventsFlow(clientDocName: String): Flow<List<Event>> = callbackFlow {
        if (clientDocName.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore
            .collection("$BASE_PATH/$clientDocName/events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting client events", error)
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val events = it.documents.mapNotNull { doc ->
                        try {
                            val eventType = doc.getString("type")
                            Log.d(TAG, "Reading event type from Firestore: $eventType for event ${doc.id}")

                            Event(
                                documentName = doc.id,
                                clientDocName = clientDocName,
                                panelDocName = doc.getString("panelDocName"),
                                panelName = doc.getString("panelName"),
                                title = doc.getString("title") ?: "",
                                text = doc.getString("text") ?: "",
                                status = doc.getString("status") ?: EventStatus.STATUS_PROGRAMADO,
                                date_time = doc.getString("date_time") ?: "",
                                userAcceptDocName = doc.getString("userAcceptDocName"),
                                type = eventType ?: "",  // Aseguramos que nunca sea null
                                createdByUserId = doc.getString("createdByUserId"),
                                createdByUserRole = doc.getString("createdByUserRole")
                            ).also { event ->
                                Log.d(TAG, "Created event object with type ${event.type}: ${event.toLogString()} from doc ${doc.reference.path}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document ${doc.id}: ${e.message}", e)
                            null
                        }
                    }
                    trySend(events)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Crea nuevos eventos para una lista de clientes.
     */
    suspend fun createEvent(clientDocNames: List<String>, event: Event): Result<Unit> = runCatching {
        if (clientDocNames.isEmpty()) {
            throw IllegalArgumentException("Client document names list cannot be empty")
        }

        val batch = firestore.batch()

        for (clientDocName in clientDocNames) {
            val eventDocName = IdManager.generateEventDocumentName(clientDocName)
            Log.d(TAG, "Creating new event with document name: $eventDocName for client: $clientDocName")

            // Obtener el nombre del panel si existe un panelDocName
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
                "panelDocName" to event.panelDocName,
                "panelName" to panelName,
                "type" to event.type?.takeIf { it.isNotEmpty() },  // Solo guardar si no está vacío
                "createdByUserId" to event.createdByUserId,
                "createdByUserRole" to event.createdByUserRole
            ).apply {
                values.removeAll { it == null }  // Remover campos nulos
            }

            Log.d(TAG, "Creating event in Firestore with type '${event.type}'")
            batch.set(eventRef, eventData)
        }

        batch.commit().await()
        Log.d(TAG, "Events batch committed successfully with type ${event.type}")
    }

    /**
     * Actualiza un evento existente.
     */
    suspend fun updateEvent(clientDocName: String, event: Event): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || event.documentName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val eventsCollection = firestore.collection("$BASE_PATH/$clientDocName/events")
        Log.d(TAG, "Base path for update: ${eventsCollection.path}")
        Log.d(TAG, "Attempting to update event with document name: ${event.documentName}")

        val eventDoc = eventsCollection.document(event.documentName)
        val snapshot = eventDoc.get().await()

        if (!snapshot.exists()) {
            throw IllegalStateException("El evento no existe: ${event.documentName}")
        }

        // Obtener el nombre del panel si existe un panelDocName
        var panelName: String? = null
        event.panelDocName?.let { pDocName ->
            val panelDoc = firestore
                .document("$BASE_PATH/$clientDocName/panels/$pDocName")
                .get()
                .await()
            panelName = panelDoc.getString("name")
        }

        val eventData = hashMapOf<String, Any?>(
            "title" to event.title,
            "text" to event.text,
            "date_time" to event.date_time,
            "panelDocName" to event.panelDocName,
            "panelName" to panelName,
            "type" to event.type?.takeIf { it.isNotEmpty() },  // Solo guardar si no está vacío
            "createdByUserId" to event.createdByUserId,
            "createdByUserRole" to event.createdByUserRole
        ).apply {
            values.removeAll { it == null }  // Remover campos nulos
        }

        Log.d(TAG, "Updating event in Firestore with type '${event.type}'")
        eventDoc.update(eventData).await()
        Log.d(TAG, "Event updated successfully with type ${event.type}")
    }

    /**
     * Actualiza el estado de un evento.
     */
    suspend fun updateEventStatus(
        clientDocName: String,
        eventDocName: String,
        newStatus: String,
        userDocName: String? = null
    ): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || eventDocName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val eventsCollection = firestore.collection("$BASE_PATH/$clientDocName/events")
        Log.d(TAG, "Updating event status: $eventDocName for client: $clientDocName to: $newStatus")

        val eventDoc = eventsCollection.document(eventDocName)
        val snapshot = eventDoc.get().await()

        if (!snapshot.exists()) {
            throw IllegalStateException("El evento no existe: $eventDocName")
        }

        val updates = mutableMapOf<String, Any?>(
            "status" to newStatus
        )

        if (newStatus == EventStatus.STATUS_ACEPTADO) {
            updates["userAcceptDocName"] = userDocName
        }

        eventDoc.update(updates).await()
        Log.d(TAG, "Event status updated successfully: $eventDocName to $newStatus")
    }

    /**
     * Elimina un evento.
     */
    suspend fun deleteEvent(clientDocName: String, eventDocName: String): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || eventDocName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val eventsCollection = firestore.collection("$BASE_PATH/$clientDocName/events")
        Log.d(TAG, "Attempting to delete event: $eventDocName for client: $clientDocName")

        val eventDoc = eventsCollection.document(eventDocName)
        val snapshot = eventDoc.get().await()

        if (!snapshot.exists()) {
            throw IllegalStateException("El evento no existe: $eventDocName")
        }

        // Obtener información actual del evento incluyendo el tipo
        val currentEvent = Event(
            documentName = snapshot.id,
            clientDocName = clientDocName,
            title = snapshot.getString("title") ?: "",
            text = snapshot.getString("text") ?: "",
            status = snapshot.getString("status") ?: EventStatus.STATUS_PROGRAMADO,
            date_time = snapshot.getString("date_time") ?: "",
            type = snapshot.getString("type") ?: "",
            createdByUserId = snapshot.getString("createdByUserId"),
            createdByUserRole = snapshot.getString("createdByUserRole")
        )

        Log.d(TAG, "Event to delete: ${currentEvent.toLogString()}")

        if (currentEvent.status != EventStatus.STATUS_PROGRAMADO) {
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
}