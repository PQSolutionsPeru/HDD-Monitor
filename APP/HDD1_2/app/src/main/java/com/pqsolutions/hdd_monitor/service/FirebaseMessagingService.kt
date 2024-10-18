package com.pqsolutions.hdd_monitor.service

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HddFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var alertRepository: AlertRepository

    @Inject
    lateinit var userRepository: UserRepository

    companion object {
        private const val TAG = "HddFirebaseMessaging"
        const val PANEL_UPDATE_ACTION = "com.pqsolutions.hdd_monitor.PANEL_UPDATE"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received: ${remoteMessage.data}")
        remoteMessage.data.let { data ->
            when (data["type"]) {
                "RELAY_UPDATE" -> handleRelayUpdate(data)
                "ALERT" -> handleAlert(data)
                else -> Log.d(TAG, "Unknown message type: ${data["type"]}")
            }
        }
    }

    private fun handleRelayUpdate(data: Map<String, String>) {
        val intent = Intent(PANEL_UPDATE_ACTION).apply {
            putExtra("clientId", data["clientId"])
            putExtra("panelId", data["panelId"])
            putExtra("relayName", data["relayName"])
            putExtra("relayStatus", data["relayStatus"])
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun handleAlert(data: Map<String, String>) {
        val alert = Alert(
            ID = data["ID"] ?: "",
            ID_CLIENT = data["ID_CLIENT"] ?: "",
            title = data["title"] ?: "",
            text = data["text"] ?: "",
            status = data["status"] ?: "PROGRAMADO"
        )
        saveAlert(alert)
    }

    private fun saveAlert(alert: Alert) {
        CoroutineScope(Dispatchers.IO).launch {
            alertRepository.createAlert(listOf(alert.ID_CLIENT), alert)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    userRepository.updateUserToken(currentUser.id, token)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating token: ${e.message}")
            }
        }
    }
}