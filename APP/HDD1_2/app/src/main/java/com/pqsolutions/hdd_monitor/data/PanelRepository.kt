package com.pqsolutions.hdd_monitor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.pqsolutions.hdd_monitor.R

class PanelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseMessaging: FirebaseMessaging,
    private val context: Context
) {
    private var lastNotifiedStates = mutableMapOf<String, String>()

    fun getAllPanelsFlow(): Flow<List<Panel>> = callbackFlow {
        val panelList = mutableListOf<Panel>()
        val listeners = mutableListOf<() -> Unit>()

        val clientsListener = firestore.collection("hdd-monitor/accounts/clients")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PanelRepository", "Error listening to clients: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val clientDoc = change.document
                    val clientId = clientDoc.id

                    when (change.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            val panelsListener = clientDoc.reference.collection("panels")
                                .addSnapshotListener { panelsSnapshot, panelsError ->
                                    if (panelsError != null) {
                                        Log.e("PanelRepository", "Error listening to panels: ${panelsError.message}")
                                        return@addSnapshotListener
                                    }

                                    panelsSnapshot?.documentChanges?.forEach { panelChange ->
                                        val panelDoc = panelChange.document
                                        val panel = panelDoc.toObject(Panel::class.java).copy(
                                            ID = panelDoc.id,
                                            clientId = clientId
                                        )

                                        when (panelChange.type) {
                                            com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                                panelList.add(panel)
                                            }
                                            com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                                val index = panelList.indexOfFirst { it.ID == panel.ID }
                                                if (index != -1) {
                                                    panelList[index] = panel
                                                } else {
                                                    panelList.add(panel)
                                                }
                                            }
                                            com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                                panelList.removeAll { it.ID == panel.ID }
                                            }
                                        }

                                        val relaysListener = panelDoc.reference.collection("relays")
                                            .addSnapshotListener { relaysSnapshot, relaysError ->
                                                if (relaysError != null) {
                                                    Log.e("PanelRepository", "Error listening to relays: ${relaysError.message}")
                                                    return@addSnapshotListener
                                                }

                                                val relayList = relaysSnapshot?.documents?.mapNotNull { relayDoc ->
                                                    relayDoc.toObject(Relay::class.java)?.copy(name = relayDoc.id)
                                                } ?: emptyList()

                                                val updatedPanel = panelList.find { it.ID == panel.ID }
                                                updatedPanel?.let {
                                                    it.relays = relayList
                                                    checkForChangesAndNotify(it)
                                                }

                                                trySend(panelList.toList())
                                            }
                                        listeners.add { relaysListener.remove() }
                                    }
                                    trySend(panelList.toList())
                                }
                            listeners.add { panelsListener.remove() }
                        }
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                            panelList.removeAll { it.clientId == clientId }
                            trySend(panelList.toList())
                        }
                    }
                }
            }

        listeners.add { clientsListener.remove() }

        awaitClose {
            listeners.forEach { it() }
        }
    }

    private fun checkForChangesAndNotify(panel: Panel) {
        panel.relays.forEach { relay ->
            val relayKey = "${panel.ID}_${relay.name}"
            val lastStatus = lastNotifiedStates[relayKey]
            if (lastStatus != null && lastStatus != relay.status) {
                if (relay.status != "OK" || (lastStatus != "OK" && relay.status == "OK")) {
                    showNotification(panel, relay)
                }
                lastNotifiedStates[relayKey] = relay.status
            } else if (lastStatus == null) {
                lastNotifiedStates[relayKey] = relay.status
                if (relay.status != "OK") {
                    showNotification(panel, relay)
                }
            }
        }
    }

    private fun showNotification(panel: Panel, relay: Relay) {
        val channelId = "panel_updates"
        val notificationId = "${panel.ID}_${relay.name}".hashCode()

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Actualización en ${panel.name}")
            .setContentText("${relay.name} estado: ${relay.status}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Actualizaciones de Panel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de cambios en los paneles"
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    suspend fun subscribeToClientTopic(clientId: String) = withContext(Dispatchers.IO) {
        firebaseMessaging.subscribeToTopic("client_$clientId").await()
    }

    suspend fun unsubscribeFromClientTopic(clientId: String) = withContext(Dispatchers.IO) {
        firebaseMessaging.unsubscribeFromTopic("client_$clientId").await()
    }
}

data class Panel(
    val ID: String = "",
    val name: String = "",
    val location: String = "",
    val SSID: String = "",
    val SSID_CON: String = "",
    val SSID_PW: String = "",
    val ESP32_IP: String = "",
    val ID_CLIENT: String = "",
    var relays: List<Relay> = emptyList(),
    var clientId: String = ""
)

data class Relay(
    val name: String = "",
    val status: String = "",
    val date_time: String = ""
)