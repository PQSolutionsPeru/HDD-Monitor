package com.pqsolutions.hdd_monitor.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.data.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alertRepository: AlertRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                "com.pqsolutions.hdd_monitor.NEW_ALERT" -> handleNewAlert(intent)
                "com.pqsolutions.hdd_monitor.NEW_MESSAGE" -> handleNewMessage(intent)
                "com.pqsolutions.hdd_monitor.PANEL_UPDATE" -> handlePanelUpdate(intent)
            }
        }
    }

    private suspend fun handleNewAlert(intent: Intent) {
        try {
            val alertId = intent.getStringExtra("alertId") ?: return
            val currentUser = userRepository.getCurrentUser() ?: return

            alertRepository.getAlertsFlow(currentUser.clientId).first().find { it.ID == alertId }?.let { alert ->
                userPreferences.setNotificationsEnabled(true)
                // Aquí podrías emitir un evento para que el ViewModel lo capture
                Log.d(TAG, "New alert handled: ${alert.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling new alert: ${e.message}")
        }
    }

    private suspend fun handleNewMessage(intent: Intent) {
        try {
            val messageId = intent.getStringExtra("messageId") ?: return
            val currentUser = userRepository.getCurrentUser() ?: return

            userRepository.getMessages(currentUser.id).fold(
                onSuccess = { messages ->
                    messages.find { it.ID_USER == messageId }?.let { message ->
                        userPreferences.setNotificationsEnabled(true)
                        // Aquí podrías emitir un evento para que el ViewModel lo capture
                        Log.d(TAG, "New message handled: ${message.subject}")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Error fetching messages: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling new message: ${e.message}")
        }
    }

    private suspend fun handlePanelUpdate(intent: Intent) {
        try {
            val panelId = intent.getStringExtra("panelId") ?: return
            val updateType = intent.getStringExtra("updateType") ?: return
            val currentUser = userRepository.getCurrentUser() ?: return

            userPreferences.setNotificationsEnabled(true)
            // Aquí podrías emitir un evento para que el ViewModel lo capture
            Log.d(TAG, "Panel update handled for panel: $panelId, type: $updateType")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling panel update: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "NotificationReceiver"
    }
}