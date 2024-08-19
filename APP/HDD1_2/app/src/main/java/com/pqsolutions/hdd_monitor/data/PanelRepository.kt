package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PanelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val alertRepository: AlertRepository
) {
    suspend fun getPanels(clientId: String): Result<List<Panel>> = runCatching {
        firestore.collection("hdd-monitor/accounts/clients/$clientId/panels")
            .get()
            .await()
            .toObjects(Panel::class.java)
    }

    fun getPanelsFlow(clientId: String): Flow<List<Panel>> = callbackFlow {
        val listener = firestore.collection("hdd-monitor/accounts/clients/$clientId/panels")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val panels = it.toObjects(Panel::class.java)
                    trySend(panels)
                    checkForRelayStateChanges(clientId, panels)
                }
            }
        awaitClose { listener.remove() }
    }

    private fun checkForRelayStateChanges(clientId: String, panels: List<Panel>) {
        panels.forEach { panel ->
            panel.relays.forEach { relay ->
                if (relay.status != "OK") {
                    val priority = when (relay.status) {
                        "CRITICAL" -> "HIGH"
                        "WARNING" -> "MEDIUM"
                        else -> "LOW"
                    }
                    val alert = Alert(
                        clientId = clientId,
                        title = "Cambio de Estado de Relé",
                        description = "El relé ${relay.name} en el panel ${panel.name} cambió a estado ${relay.status}",
                        dateTime = System.currentTimeMillis().toString(),
                        status = "NEW",
                        priority = priority
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        alertRepository.createAlert(clientId, alert)
                    }
                }
            }
        }
    }
}

data class Panel(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val relays: List<Relay> = emptyList()
)

data class Relay(
    val name: String = "",
    val status: String = ""
)