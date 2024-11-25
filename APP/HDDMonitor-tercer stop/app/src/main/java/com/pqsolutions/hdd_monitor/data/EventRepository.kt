package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.pqsolutions.hdd_monitor.data.util.Cache
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private var currentListenerRegistration: ListenerRegistration? = null
    private val eventsCache = Cache<String, List<Event>>(20, Cache.getMillis(30, TimeUnit.SECONDS))

    companion object {
        private const val TAG = "EventRepository"
        private const val BASE_PATH = "hdd-monitor/accounts/clients"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    }

    fun getAllEventsFlow(): Flow<List<Event>> = callbackFlow {
        // Enviar datos cacheados primero si existen
        eventsCache.get("all_events")?.let { cachedEvents ->
            trySend(cachedEvents)
        }

        currentListenerRegistration?.remove()

        // Consulta simple sin orderBy
        val query = firestore.collectionGroup("events")

        val listenerRegistration = query
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting events", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    try {
                        val events = snapshot.documents.mapNotNull { doc ->
                            try {
                                val clientDocName = doc.reference.path
                                    .split("/")
                                    .find { segment -> segment.startsWith("client_") }

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
                                    type = doc.getString("type"),
                                    createdByUserId = doc.getString("createdByUserId"),
                                    createdByUserRole = doc.getString("createdByUserRole"),
                                    isRead = doc.getBoolean("isRead") ?: false,
                                    acceptedAt = doc.getTimestampAsString("acceptedAt"),
                                    finalizedAt = doc.getTimestampAsString("finalizedAt"),
                                    lastUpdate = doc.getTimestampAsString("lastUpdate")
                                        ?: LocalDateTime.now().format(DATE_FORMATTER)
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting document", e)
                                null
                            }
                        }

                        // Ordenar en memoria y filtrar eventos recientes
                        val sortedEvents = events
                            .sortedByDescending { event ->
                                try {
                                    LocalDateTime.parse(event.lastUpdate, DATE_FORMATTER)
                                } catch (e: Exception) {
                                    LocalDateTime.now()
                                }
                            }
                            .take(50) // Limitar a los 50 eventos más recientes

                        eventsCache.put("all_events", sortedEvents)
                        trySend(sortedEvents)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing events", e)
                        trySend(emptyList())
                    }
                }
            }

        currentListenerRegistration = listenerRegistration
        awaitClose {
            listenerRegistration.remove()
            currentListenerRegistration = null
        }
    }

    fun getEventsFlow(clientDocName: String): Flow<List<Event>> = callbackFlow {
        if (clientDocName.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        eventsCache.get(clientDocName)?.let { cachedEvents ->
            trySend(cachedEvents)
        }

        currentListenerRegistration?.remove()

        val query = firestore
            .collection("$BASE_PATH/$clientDocName/events")

        val listenerRegistration = query
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting client events", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    try {
                        val events = snapshot.documents.mapNotNull { doc ->
                            try {
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
                                    type = doc.getString("type"),
                                    createdByUserId = doc.getString("createdByUserId"),
                                    createdByUserRole = doc.getString("createdByUserRole"),
                                    isRead = doc.getBoolean("isRead") ?: false,
                                    acceptedAt = doc.getTimestampAsString("acceptedAt"),
                                    finalizedAt = doc.getTimestampAsString("finalizedAt"),
                                    lastUpdate = doc.getTimestampAsString("lastUpdate")
                                        ?: LocalDateTime.now().format(DATE_FORMATTER)
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting document", e)
                                null
                            }
                        }

                        // Ordenar en memoria
                        val sortedEvents = events.sortedByDescending { event ->
                            try {
                                LocalDateTime.parse(event.lastUpdate, DATE_FORMATTER)
                            } catch (e: Exception) {
                                LocalDateTime.now()
                            }
                        }

                        eventsCache.put(clientDocName, sortedEvents)
                        trySend(sortedEvents)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing events", e)
                        trySend(emptyList())
                    }
                }
            }

        currentListenerRegistration = listenerRegistration
        awaitClose {
            listenerRegistration.remove()
            currentListenerRegistration = null
        }
    }

    suspend fun createEvent(clientDocNames: List<String>, event: Event): Result<Unit> = runCatching {
        if (clientDocNames.isEmpty()) {
            throw IllegalArgumentException("Client document names list cannot be empty")
        }

        val batch = firestore.batch()

        for (clientDocName in clientDocNames) {
            // Aquí usamos IdManager para generar el nombre correcto
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
                "type" to event.type,
                "adminAcceptDocName" to null,
                "panelDocName" to event.panelDocName,
                "panelName" to panelName,
                "userAcceptDocName" to null,
                "acceptedAt" to null,
                "finalizedAt" to null,
                "createdByUserId" to event.createdByUserId,
                "createdByUserRole" to event.createdByUserRole,
                "isRead" to false,
                "lastUpdate" to LocalDateTime.now().format(DATE_FORMATTER)
            )

            Log.d(TAG, "Creating event in Firestore with data: $eventData")
            batch.set(eventRef, eventData)
        }

        batch.commit().await()
        Log.d(TAG, "Event batch committed successfully")
    }

    suspend fun updateEvent(clientDocName: String, event: Event): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || event.documentName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val eventsCollection = firestore.collection("$BASE_PATH/$clientDocName/events")
        Log.d(TAG, "Base path for update: ${eventsCollection.path}")
        Log.d(TAG, "Attempting to update events with document name: ${event.documentName}")

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

        val eventData = hashMapOf<String, Any?>(
            "title" to event.title,
            "text" to event.text,
            "date_time" to event.date_time,
            "panelDocName" to event.panelDocName,
            "panelName" to panelName,
            "type" to event.type?.takeIf { it.isNotEmpty() },
            "createdByUserId" to event.createdByUserId,
            "createdByUserRole" to event.createdByUserRole,
            "isRead" to false,
            "lastUpdate" to LocalDateTime.now().format(DATE_FORMATTER)
        ).apply {
            values.removeAll { it == null }
        }

        Log.d(TAG, "Updating events in Firestore with data: $eventData")
        eventDoc.update(eventData).await()
        Log.d(TAG, "Event updated successfully")
    }

    suspend fun updateEventStatus(
        clientDocName: String,
        eventDocName: String,
        newStatus: String,
        acceptorDocName: String?,
        isAdmin: Boolean
    ): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || eventDocName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val now = LocalDateTime.now().format(DATE_FORMATTER)
        val eventsCollection = firestore.collection("$BASE_PATH/$clientDocName/events")
        val eventDoc = eventsCollection.document(eventDocName)

        val updates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "isRead" to false,
            "lastUpdate" to now
        )

        when (newStatus) {
            EventStatus.STATUS_ACEPTADO -> {
                if (acceptorDocName != null) {
                    if (isAdmin) {
                        updates["adminAcceptDocName"] = acceptorDocName
                    } else {
                        updates["userAcceptDocName"] = acceptorDocName
                    }
                }
                updates["acceptedAt"] = now
            }
            EventStatus.STATUS_FINALIZADO -> {
                updates["finalizedAt"] = now
            }
        }

        eventDoc.update(updates).await()
        Log.d(TAG, "Event status updated successfully")
    }

    fun getAllPanelsFlow(): Flow<List<Panel>> = callbackFlow {
        val listenerRegistration = firestore.collectionGroup("panels")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting all panels", error)
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let { querySnapshot ->
                    val panels = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            val clientDocName = doc.reference.path
                                .split("/")
                                .find { segment -> segment.startsWith("client_") }
                                ?: return@mapNotNull null

                            doc.toObject(Panel::class.java)?.copy(
                                documentName = doc.id,
                                clientDocName = clientDocName
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting panel document", e)
                            null
                        }
                    }
                    trySend(panels)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun deleteEvent(clientDocName: String, eventDocName: String): Result<Unit> = runCatching {
        if (clientDocName.isEmpty() || eventDocName.isEmpty()) {
            throw IllegalArgumentException("Client and Event document names cannot be empty")
        }

        val eventRef = firestore.document("$BASE_PATH/$clientDocName/events/$eventDocName")
        Log.d(TAG, "Deleting events: $eventDocName for client: $clientDocName")

        eventRef.delete().await()
        Log.d(TAG, "Event deleted successfully")
    }

    suspend fun getClients(): Result<List<Client>> = runCatching {
        val snapshot = firestore.collection(BASE_PATH)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            try {
                doc.toObject(Client::class.java)?.copy(
                    documentName = doc.id
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error converting client document: ${doc.id}", e)
                null
            }
        }
    }

    fun getPanelStatusFlow(clientDocName: String): Flow<List<Panel>> = callbackFlow {
        if (clientDocName.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore
            .collection("$BASE_PATH/$clientDocName/panels")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting panels", error)
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val panels = it.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Panel::class.java)?.copy(
                                documentName = doc.id,
                                clientDocName = clientDocName
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting panel document", e)
                            null
                        }
                    }
                    trySend(panels)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    private fun DocumentSnapshot.getTimestampAsString(field: String): String? {
        return try {
            getLong(field)?.let { timestamp ->
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.systemDefault()
                ).format(DATE_FORMATTER)
            }
        } catch (e: Exception) {
            try {
                getString(field)
            } catch (e: Exception) {
                null
            }
        }
    }
}