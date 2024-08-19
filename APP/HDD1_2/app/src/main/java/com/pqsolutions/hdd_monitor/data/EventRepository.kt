package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun getEvents(clientId: String): Result<List<Event>> = runCatching {
        firestore.collection("hdd-monitor/accounts/clients/$clientId/panel_events_log")
            .get()
            .await()
            .toObjects(Event::class.java)
    }
}

data class Event(
    val id: String = "",
    val dateTime: String = "",
    val description: String = "",
    val type: String = "",
    val solvedStatus: String = ""
)