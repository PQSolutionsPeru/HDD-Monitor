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
    fun getAlertsFlow(clientId: String): Flow<List<Alert>> = callbackFlow {
        val listenerRegistration = if (clientId.isNotEmpty()) {
            firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    snapshot?.let { trySend(it.toObjects(Alert::class.java)) }
                }
        } else {
            firestore.collectionGroup("alerts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    snapshot?.let { trySend(it.toObjects(Alert::class.java)) }
                }
        }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getAlerts(clientId: String): Result<List<Alert>> = runCatching {
        if (clientId.isNotEmpty()) {
            firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
                .get()
                .await()
                .toObjects(Alert::class.java)
        } else {
            firestore.collectionGroup("alerts")
                .get()
                .await()
                .toObjects(Alert::class.java)
        }
    }

    suspend fun createAlert(clientId: String, alert: Alert): Result<Unit> = runCatching {
        val alertWithId = if (alert.id.isEmpty()) {
            alert.copy(id = firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts").document().id)
        } else {
            alert
        }
        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .document(alertWithId.id)
            .set(alertWithId)
            .await()
    }

    suspend fun updateAlert(clientId: String, alert: Alert): Result<Unit> = runCatching {
        firestore.collection("hdd-monitor/accounts/clients/$clientId/alerts")
            .document(alert.id)
            .set(alert)
            .await()
    }

    suspend fun deleteAlert(clientId: String, alertId: String): Result<Unit> = runCatching {
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
    val id: String = "",
    val clientId: String = "",
    val title: String = "",
    val description: String = "",
    val dateTime: String = "",
    val status: String = "",
    val priority: String = ""
)