package com.pqsolutions.hdd_monitor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class PanelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseMessaging: FirebaseMessaging,
    private val context: Context
) {
    private val lastNotifiedStates = mutableMapOf<String, String>()

    companion object {
        private const val TAG = "PanelRepository"
    }

    fun getPanels(clientId: String?): Flow<List<Panel>> = callbackFlow {
        Log.d(TAG, "getPanels called with clientId: $clientId")

        val panelListeners = mutableMapOf<String, ListenerRegistration>()
        val currentPanels = mutableMapOf<String, Panel>()

        val clientsRef = firestore.collection("hdd-monitor/accounts/clients")

        val clientListener = clientsRef.addSnapshotListener { clientSnapshot, clientError ->
            if (clientError != null) {
                Log.e(TAG, "Error listening to clients", clientError)
                close(clientError)
                return@addSnapshotListener
            }

            if (clientSnapshot != null) {
                for (clientDoc in clientSnapshot.documents) {
                    val currentClientId = clientDoc.id
                    Log.d(TAG, "Processing client: $currentClientId")
                    if (clientId == null || currentClientId == clientId) {
                        val panelsRef = clientDoc.reference.collection("panels")
                        val panelListener = panelsRef.addSnapshotListener { panelSnapshot, panelError ->
                            if (panelError != null) {
                                Log.e(TAG, "Error listening to panels for client $currentClientId", panelError)
                                return@addSnapshotListener
                            }
                            if (panelSnapshot != null) {
                                for (panelDoc in panelSnapshot.documents) {
                                    val panel = panelDoc.toObject(Panel::class.java)?.copy(
                                        ID = panelDoc.id,
                                        ID_CLIENT = currentClientId
                                    )
                                    if (panel != null) {
                                        Log.d(TAG, "Panel added/updated: ${panel.ID} for client $currentClientId")
                                        currentPanels["${currentClientId}_${panel.ID}"] = panel
                                        fetchRelaysForPanel(panel) { updatedPanel ->
                                            currentPanels["${currentClientId}_${updatedPanel.ID}"] = updatedPanel
                                            trySend(currentPanels.values.toList())
                                        }
                                    }
                                }
                                trySend(currentPanels.values.toList())
                            }
                        }
                        panelListeners["client_$currentClientId"] = panelListener
                    }
                }
            }
        }

        awaitClose {
            Log.d(TAG, "Closing panel listeners")
            clientListener.remove()
            panelListeners.values.forEach { it.remove() }
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchRelaysForPanel(panel: Panel, onUpdate: (Panel) -> Unit) {
        Log.d(TAG, "Fetching relays for panel ${panel.ID} of client ${panel.ID_CLIENT}")
        firestore.collection("hdd-monitor/accounts/clients/${panel.ID_CLIENT}/panels/${panel.ID}/relays")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching relays for panel ${panel.ID}", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val relays = snapshot.documents.mapNotNull { relayDoc ->
                        relayDoc.toObject(Relay::class.java)?.copy(name = relayDoc.id).also { relay ->
                            Log.d(TAG, "Relay update from Firestore: ${relay?.name}, status: ${relay?.status}")
                        }
                    }
                    Log.d(TAG, "Fetched ${relays.size} relays for panel ${panel.ID} of client ${panel.ID_CLIENT}")
                    val updatedPanel = checkForChangesAndNotify(panel.copy(relays = relays))
                    Log.d(TAG, "Updated panel ${updatedPanel.ID}: ${updatedPanel.relays.map { "${it.name}: ${it.status}" }}")
                    onUpdate(updatedPanel)
                } else {
                    Log.d(TAG, "No relays found for panel ${panel.ID} of client ${panel.ID_CLIENT}")
                    onUpdate(panel.copy(relays = emptyList()))
                }
            }
    }

    private fun checkForChangesAndNotify(panel: Panel): Panel {
        Log.d(TAG, "Verificando cambios y notificaciones para el panel: ${panel.ID}")
        val updatedRelays = panel.relays.map { relay ->
            val relayKey = "${panel.ID}_${relay.name}"
            val lastStatus = lastNotifiedStates[relayKey]
            if (lastStatus != relay.status) {
                Log.d(TAG, "Se detectó cambio de estado para el relay ${relay.name}: $lastStatus -> ${relay.status}")
                sendNotification(panel, relay)
                lastNotifiedStates[relayKey] = relay.status
            }
            relay
        }
        val updatedPanel = panel.copy(relays = updatedRelays)
        updatedPanel.overallStatus = determineOverallPanelStatus(updatedPanel)
        return updatedPanel
    }

    private fun determineOverallPanelStatus(panel: Panel): String {
        val discRelays = panel.relays.filter { it.status == "DISC" }
        return when {
            discRelays.isNotEmpty() -> discRelays.joinToString(", ") { it.name }
            else -> "OK"
        }
    }

    private fun sendNotification(panel: Panel, relay: Relay) {
        Log.d(TAG, "Sending notification for panel: ${panel.ID}, relay: ${relay.name}")
        sendLocalNotification(panel, relay)
    }

    private fun sendLocalNotification(panel: Panel, relay: Relay) {
        Log.d(TAG, "Sending local notification for panel: ${panel.ID}, relay: ${relay.name}")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "panel_updates"
        val channelName = "Panel Updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notificationType", "relay_update")
            putExtra("panelId", panel.ID)
            putExtra("relayName", relay.name)
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Estado del Panel Actualizado")
            .setContentText("Panel: ${panel.name}, Relay: ${relay.name}, Estado: ${relay.status}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(panel.ID.hashCode(), notificationBuilder.build())
    }

    suspend fun updateRelayStatus(clientId: String, panelId: String, relayName: String, relayStatus: String) {
        try {
            Log.d(TAG, "Updating relay status: clientId=$clientId, panelId=$panelId, relayName=$relayName, status=$relayStatus")
            firestore.document("hdd-monitor/accounts/clients/$clientId/panels/$panelId/relays/$relayName")
                .update("status", relayStatus)
                .await()
            Log.d(TAG, "Relay status updated successfully: Panel=$panelId, Relay=$relayName, Status=$relayStatus")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating relay status: ${e.message}", e)
            throw e
        }
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
    val relays: List<Relay> = emptyList(),
    var overallStatus: String = "OK"
)

data class Relay(
    val name: String = "",
    val status: String = "",
    val date_time: String = ""
)