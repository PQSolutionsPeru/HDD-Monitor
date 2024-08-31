package com.pqsolutions.hdd_monitor.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {
    fun getAllAlertsFlow(): Flow<List<Alert>> = callbackFlow {
        val listenerRegistration = firestore.collectionGroup("alerts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val alerts = it.documents.mapNotNull { doc ->
                        doc.toObject(Alert::class.java)?.let { alert ->
                            if (alert.ID.isEmpty()) alert.copy(ID = doc.id) else alert
                        }
                    }
                    trySend(alerts)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun getAlertsFlow(clientId: String): Flow<List<Alert>> = callbackFlow {
        if (clientId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val alerts = it.documents.mapNotNull { doc ->
                        doc.toObject(Alert::class.java)
                    }
                    trySend(alerts)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getAlerts(clientId: String): Result<List<Alert>> = runCatching {
        if (clientId.isEmpty()) {
            return@runCatching emptyList()
        }

        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .get()
            .await()
            .documents.mapNotNull { doc ->
                doc.toObject(Alert::class.java)
            }
    }

    suspend fun createAlert(clientId: String, alert: Alert): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }

        val alertData = hashMapOf(
            "ID" to alert.ID,
            "ID_CLIENT" to clientId,
            "title" to alert.title,
            "text" to alert.text,
            "status" to alert.status
        )
        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .add(alertData)
            .await()
    }

    suspend fun createAlertForClient(clientId: String, alert: Alert): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }

        val alertData = hashMapOf(
            "ID" to alert.ID,
            "ID_CLIENT" to clientId,
            "title" to alert.title,
            "text" to alert.text,
            "status" to alert.status
        )
        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .add(alertData)
            .await()
    }

    suspend fun updateAlert(clientId: String, alert: Alert): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }

        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .document(alert.ID)
            .update(
                mapOf(
                    "title" to alert.title,
                    "text" to alert.text,
                    "status" to alert.status
                )
            )
            .await()
    }

    suspend fun updateAlertForClient(clientId: String, alert: Alert): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }

        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .document(alert.ID)
            .update(
                mapOf(
                    "title" to alert.title,
                    "text" to alert.text,
                    "status" to alert.status
                )
            )
            .await()
    }

    suspend fun deleteAlert(clientId: String, alertId: String): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }

        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .document(alertId)
            .delete()
            .await()
    }

    suspend fun deleteAlertForClient(clientId: String, alertId: String): Result<Unit> = runCatching {
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Client ID cannot be empty")
        }

        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .document(alertId)
            .delete()
            .await()
    }

    suspend fun subscribeToAlertTopic(clientId: String) {
        val topic = "client_${clientId}_alerts"
        messaging.subscribeToTopic(topic).await()
    }

    suspend fun unsubscribeFromAlertTopic(clientId: String) {
        val topic = "client_${clientId}_alerts"
        messaging.unsubscribeFromTopic(topic).await()
    }
}

data class Alert(
    val ID: String = "",
    val ID_CLIENT: String = "",
    val title: String = "",
    val text: String = "",
    val status: String = ""
)