package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getEventsFlow(clientId: String): Flow<List<Event>> = callbackFlow {
        val listenerRegistration = if (clientId.isNotEmpty()) {
            firestore.collection("hdd-monitor/accounts/clients/$clientId/panel_events_log")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    snapshot?.let { trySend(it.toObjects(Event::class.java)) }
                }
        } else {
            // Si clientId está vacío, devolvemos una lista vacía
            trySend(emptyList())
            null
        }
        awaitClose { listenerRegistration?.remove() }
    }

    suspend fun getEvents(clientId: String): Result<List<Event>> = runCatching {
        if (clientId.isEmpty()) {
            emptyList()
        } else {
            firestore.collection("hdd-monitor/accounts/clients/$clientId/panel_events_log")
                .get()
                .await()
                .toObjects(Event::class.java)
        }
    }

    suspend fun createEvent(clientId: String, event: Event): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }
        val eventWithId = if (event.id.isEmpty()) {
            event.copy(id = firestore.collection("hdd-monitor/accounts/clients/$clientId/panel_events_log").document().id)
        } else {
            event
        }
        firestore.collection("hdd-monitor/accounts/clients/$clientId/panel_events_log")
            .document(eventWithId.id)
            .set(eventWithId)
            .await()
    }

    suspend fun updateEvent(clientId: String, event: Event): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }
        firestore.collection("hdd-monitor/accounts/clients/$clientId/panel_events_log")
            .document(event.id)
            .set(event)
            .await()
    }

    suspend fun deleteEvent(clientId: String, eventId: String): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }
        firestore.collection("hdd-monitor/accounts/clients/$clientId/panel_events_log")
            .document(eventId)
            .delete()
            .await()
    }
}

data class Event(
    val id: String = "",
    val dateTime: String = "",
    val description: String = "",
    val type: String = "",
    val solvedStatus: String = ""
)