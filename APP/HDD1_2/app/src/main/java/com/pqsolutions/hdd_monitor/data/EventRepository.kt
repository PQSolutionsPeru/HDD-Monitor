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

class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "EventRepository"
        private const val BASE_PATH = "hdd-monitor/accounts/clients"
    }

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
                            // Extraer el nombre del documento del cliente del path
                            val clientDocName = doc.reference.path
                                .split("/")
                                .find { segment -> segment.startsWith("client_") }

                            doc.toObject(Event::class.java)?.copy(
                                documentName = doc.id,
                                clientDocName = clientDocName ?: ""
                            )?.also { event ->
                                Log.d(TAG, "Loading event: ${event.toLogString()} from doc ${doc.reference.path}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document ${doc.id}", e)
                            null
                        }
                    }
                    trySend(events)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

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
                            doc.toObject(Event::class.java)?.copy(
                                documentName = doc.id,
                                clientDocName = clientDocName
                            )?.also { event ->
                                Log.d(TAG, "Loading client event: ${event.toLogString()} from doc ${doc.reference.path}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document ${doc.id}", e)
                            null
                        }
                    }
                    trySend(events)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

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

            val eventData = hashMapOf(
                "title" to event.title,
                "text" to event.text,
                "status" to EventStatus.STATUS_PROGRAMADO,
                "date_time" to event.date_time,
                "panelDocName" to event.panelDocName,
                "panelName" to panelName
            )

            batch.set(eventRef, eventData)
        }

        batch.commit().await()
    }

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

        val currentEvent = snapshot.toObject(Event::class.java)
        Log.d(TAG, "Current event from DB: ${currentEvent?.toLogString()}")

        if (currentEvent?.status != EventStatus.STATUS_PROGRAMADO) {
            throw IllegalStateException("Solo se pueden actualizar eventos en estado PROGRAMADO")
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

        val eventData = mapOf(
            "title" to event.title,
            "text" to event.text,
            "date_time" to event.date_time,
            "panelDocName" to event.panelDocName,
            "panelName" to panelName
        )

        eventDoc.update(eventData).await()
        Log.d(TAG, "Event updated successfully")
    }

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

        val event = snapshot.toObject(Event::class.java)
        Log.d(TAG, "Event to delete: ${event?.toLogString()}")

        if (event?.status != EventStatus.STATUS_PROGRAMADO) {
            throw IllegalStateException("Solo se pueden eliminar eventos en estado PROGRAMADO")
        }

        eventDoc.delete().await()
        Log.d(TAG, "Event deleted successfully: $eventDocName")
    }

    suspend fun getClients(): List<Client> {
        return firestore.collection(BASE_PATH)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(Client::class.java)?.copy(documentName = doc.id)
            }
    }

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