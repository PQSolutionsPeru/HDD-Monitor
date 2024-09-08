package com.pqsolutions.hdd_monitor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.*
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

    fun getPanelsFlow(clientId: String?): Flow<List<Panel>> = callbackFlow {
        val panelList = mutableListOf<Panel>()
        val listeners = mutableListOf<ListenerRegistration>()

        val clientsListener: ListenerRegistration = if (clientId != null) {
            val clientDocRef = firestore.collection("hdd-monitor/accounts/clients").document(clientId)
            clientDocRef.addSnapshotListener { snapshot: DocumentSnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    Log.e("PanelRepository", "Error listening to client: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.let { clientDoc ->
                    handleClientDocument(clientDoc, panelList, listeners) {
                        trySend(panelList.toList())
                    }
                }
            }
        } else {
            firestore.collection("hdd-monitor/accounts/clients")
                .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                    if (error != null) {
                        Log.e("PanelRepository", "Error listening to clients: ${error.message}")
                        return@addSnapshotListener
                    }

                    snapshot?.documentChanges?.forEach { change: DocumentChange ->
                        val clientDoc = change.document
                        when (change.type) {
                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {
                                handleClientDocument(clientDoc, panelList, listeners) {
                                    trySend(panelList.toList())
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                panelList.removeAll { it.clientId == clientDoc.id }
                                trySend(panelList.toList())
                            }
                        }
                    }
                }
        }

        listeners.add(clientsListener)

        awaitClose {
            listeners.forEach { it.remove() }
        }
    }

    private fun handleClientDocument(
        clientDoc: DocumentSnapshot,
        panelList: MutableList<Panel>,
        listeners: MutableList<ListenerRegistration>,
        onUpdate: () -> Unit
    ) {
        val clientId = clientDoc.id
        val panelsListener = clientDoc.reference.collection("panels")
            .addSnapshotListener { panelsSnapshot: QuerySnapshot?, panelsError: FirebaseFirestoreException? ->
                if (panelsError != null) {
                    Log.e("PanelRepository", "Error listening to panels: ${panelsError.message}")
                    return@addSnapshotListener
                }

                panelsSnapshot?.documentChanges?.forEach { panelChange: DocumentChange ->
                    val panelDoc = panelChange.document
                    val panel = panelDoc.toObject(Panel::class.java)?.copy(
                        ID = panelDoc.id,
                        clientId = clientId
                    ) ?: return@forEach

                    when (panelChange.type) {
                        DocumentChange.Type.ADDED -> {
                            panelList.add(panel)
                        }
                        DocumentChange.Type.MODIFIED -> {
                            val index = panelList.indexOfFirst { it.ID == panel.ID }
                            if (index != -1) {
                                panelList[index] = panel
                            } else {
                                panelList.add(panel)
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            panelList.removeAll { it.ID == panel.ID }
                        }
                    }

                    val relaysListener = panelDoc.reference.collection("relays")
                        .addSnapshotListener { relaysSnapshot: QuerySnapshot?, relaysError: FirebaseFirestoreException? ->
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

                            onUpdate()
                        }
                    listeners.add(relaysListener)
                }
                onUpdate()
            }
        listeners.add(panelsListener)
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