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
    fun getEventsFlow(clientId: String): Flow<List<EventWithMetadata>> = callbackFlow {
        val eventsRef = firestore.collection("hdd-monitor/accounts/clients/$clientId/panels")
        val listenerRegistration = eventsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val allEvents = mutableListOf<EventWithMetadata>()
            snapshot?.documents?.forEach { panelDoc ->
                val panelEventsRef = panelDoc.reference.collection("panel_events_log")
                panelEventsRef.get().addOnSuccessListener { panelEventsSnapshot ->
                    panelEventsSnapshot.documents.forEach { eventDoc ->
                        val event = eventDoc.toObject(Event::class.java)
                        event?.let {
                            val eventWithMetadata = EventWithMetadata(
                                event = it,
                                eventId = eventDoc.id,
                                panelId = panelDoc.id,
                                panelName = panelDoc.getString("name") ?: ""
                            )
                            allEvents.add(eventWithMetadata)
                        }
                    }
                    trySend(allEvents)
                }.addOnFailureListener { e ->
                    close(e)
                }
            }
        }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun createEvent(clientId: String, panelId: String, event: Event): Result<Unit> = runCatching {
        firestore.collection("hdd-monitor/accounts/clients/$clientId/panels/$panelId/panel_events_log")
            .add(event)
            .await()
    }

    suspend fun updateEvent(clientId: String, panelId: String, eventId: String, event: Event): Result<Unit> = runCatching {
        firestore.collection("hdd-monitor/accounts/clients/$clientId/panels/$panelId/panel_events_log")
            .document(eventId)
            .set(event)
            .await()
    }

    suspend fun deleteEvent(clientId: String, panelId: String, eventId: String): Result<Unit> = runCatching {
        firestore.collection("hdd-monitor/accounts/clients/$clientId/panels/$panelId/panel_events_log")
            .document(eventId)
            .delete()
            .await()
    }
}

data class Event(
    val date_time: String = "",
    val description: String = "",
    val solved_status: String = "",
    val type: String = ""
)

data class EventWithMetadata(
    val event: Event,
    val eventId: String,
    val panelId: String,
    val panelName: String
)