package com.pqsolutions.hdd_monitor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.*
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
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
    private val lastNotifiedStates = mutableMapOf<String, String>()
    private val firebaseFunctions: FirebaseFunctions = FirebaseFunctions.getInstance()

    fun getPanelsFlow(clientId: String?): Flow<List<Panel>> = callbackFlow {
        val panelList = mutableListOf<Panel>()
        val listeners = mutableListOf<ListenerRegistration>()

        val clientsListener: ListenerRegistration = if (clientId != null) {
            listenToSingleClient(clientId, panelList, listeners) { trySend(panelList.toList()) }
        } else {
            listenToAllClients(panelList, listeners) { trySend(panelList.toList()) }
        }

        listeners.add(clientsListener)

        awaitClose {
            listeners.forEach { it.remove() }
        }
    }.flowOn(Dispatchers.IO)

    private fun listenToSingleClient(
        clientId: String,
        panelList: MutableList<Panel>,
        listeners: MutableList<ListenerRegistration>,
        onUpdate: () -> Unit
    ): ListenerRegistration {
        val clientDocRef = firestore.collection("hdd-monitor/accounts/clients").document(clientId)
        return clientDocRef.addSnapshotListener { snapshot: DocumentSnapshot?, error: FirebaseFirestoreException? ->
            if (error != null) {
                Log.e(TAG, "Error listening to client: ${error.message}")
                return@addSnapshotListener
            }

            snapshot?.let { clientDoc ->
                handleClientDocument(clientDoc, panelList, listeners, onUpdate)
            }
        }
    }

    private fun listenToAllClients(
        panelList: MutableList<Panel>,
        listeners: MutableList<ListenerRegistration>,
        onUpdate: () -> Unit
    ): ListenerRegistration {
        return firestore.collection("hdd-monitor/accounts/clients")
            .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    Log.e(TAG, "Error listening to clients: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change: DocumentChange ->
                    val clientDoc = change.document
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> {
                            handleClientDocument(clientDoc, panelList, listeners, onUpdate)
                        }
                        DocumentChange.Type.REMOVED -> {
                            panelList.removeAll { it.clientId == clientDoc.id }
                            onUpdate()
                        }
                    }
                }
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
                    Log.e(TAG, "Error listening to panels: ${panelsError.message}")
                    return@addSnapshotListener
                }

                panelsSnapshot?.documentChanges?.forEach { panelChange: DocumentChange ->
                    handlePanelChange(panelChange, clientId, panelList, listeners, onUpdate)
                }
                onUpdate()
            }
        listeners.add(panelsListener)
    }

    private fun handlePanelChange(
        panelChange: DocumentChange,
        clientId: String,
        panelList: MutableList<Panel>,
        listeners: MutableList<ListenerRegistration>,
        onUpdate: () -> Unit
    ) {
        val panelDoc = panelChange.document
        val panel = panelDoc.toObject(Panel::class.java)?.copy(
            ID = panelDoc.id,
            clientId = clientId
        ) ?: return

        when (panelChange.type) {
            DocumentChange.Type.ADDED -> {
                panelList.add(panel)
                addRelaysListener(panelDoc, panel, panelList, listeners, onUpdate)
            }
            DocumentChange.Type.MODIFIED -> {
                val index = panelList.indexOfFirst { it.ID == panel.ID }
                if (index != -1) {
                    panelList[index] = panel.copy(relays = panelList[index].relays)
                } else {
                    panelList.add(panel)
                }
                addRelaysListener(panelDoc, panel, panelList, listeners, onUpdate)
            }
            DocumentChange.Type.REMOVED -> {
                panelList.removeAll { it.ID == panel.ID }
            }
        }
    }

    private fun addRelaysListener(
        panelDoc: DocumentSnapshot,
        panel: Panel,
        panelList: MutableList<Panel>,
        listeners: MutableList<ListenerRegistration>,
        onUpdate: () -> Unit
    ) {
        val relaysListener = panelDoc.reference.collection("relays")
            .addSnapshotListener { relaysSnapshot: QuerySnapshot?, relaysError: FirebaseFirestoreException? ->
                if (relaysError != null) {
                    Log.e(TAG, "Error listening to relays: ${relaysError.message}")
                    return@addSnapshotListener
                }

                val relayList = relaysSnapshot?.documents?.mapNotNull { relayDoc ->
                    relayDoc.toObject(Relay::class.java)?.copy(name = relayDoc.id)
                } ?: emptyList()

                val panelIndex = panelList.indexOfFirst { it.ID == panel.ID }
                if (panelIndex != -1) {
                    panelList[panelIndex] = panelList[panelIndex].copy(relays = relayList)
                    checkForChangesAndNotify(panelList[panelIndex])
                }

                Log.d(TAG, "Relays updated for panel ${panel.name}: ${relayList.size} relays")
                onUpdate()
            }
        listeners.add(relaysListener)
    }

    private fun checkForChangesAndNotify(panel: Panel) {
        panel.relays.forEach { relay ->
            val relayKey = "${panel.ID}_${relay.name}"
            val lastStatus = lastNotifiedStates[relayKey]
            if (lastStatus != null && lastStatus != relay.status) {
                if (relay.status != "OK" || (lastStatus != "OK" && relay.status == "OK")) {
                    sendCloudFunctionNotification(panel, relay)
                }
                lastNotifiedStates[relayKey] = relay.status
            } else if (lastStatus == null) {
                lastNotifiedStates[relayKey] = relay.status
                if (relay.status != "OK") {
                    sendCloudFunctionNotification(panel, relay)
                }
            }
        }
    }

    private fun sendCloudFunctionNotification(panel: Panel, relay: Relay) {
        val data = hashMapOf(
            "panelId" to panel.ID,
            "panelName" to panel.name,
            "relayName" to relay.name,
            "relayStatus" to relay.status,
            "clientId" to panel.clientId
        )

        Log.d(TAG, "Attempting to call Cloud Function")

        firebaseFunctions
            .getHttpsCallable("sendPanelNotification")
            .call(data)
            .addOnSuccessListener { result ->
                Log.d(TAG, "Notification sent successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending notification: ${e.message}", e)
                sendLocalNotification(panel, relay)
            }
    }

    private fun sendLocalNotification(panel: Panel, relay: Relay) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "panel_updates"
        val channelName = "Panel Updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Estado del Panel Actualizado")
            .setContentText("Panel: ${panel.name}, Relay: ${relay.name}, Estado: ${relay.status}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(panel.ID.hashCode(), notificationBuilder.build())
    }

    suspend fun subscribeToClientTopic(clientId: String) = withContext(Dispatchers.IO) {
        try {
            firebaseMessaging.subscribeToTopic("client_$clientId").await()
            Log.d(TAG, "Subscribed to topic: client_$clientId")
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topic: client_$clientId", e)
            throw e
        }
    }

    suspend fun unsubscribeFromClientTopic(clientId: String) = withContext(Dispatchers.IO) {
        try {
            firebaseMessaging.unsubscribeFromTopic("client_$clientId").await()
            Log.d(TAG, "Unsubscribed from topic: client_$clientId")
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing from topic: client_$clientId", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "PanelRepository"
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