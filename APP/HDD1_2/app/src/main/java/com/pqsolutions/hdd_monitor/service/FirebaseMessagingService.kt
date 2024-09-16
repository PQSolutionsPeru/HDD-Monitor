package com.pqsolutions.hdd_monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.presentation.MainActivity
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
        private const val CHANNEL_ID = "hdd_monitor_alerts"
        private const val CHANNEL_NAME = "HDD Monitor Alerts"
        private const val CHANNEL_DESCRIPTION = "Notifications for HDD Monitor alerts"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        remoteMessage.data.let { data ->
            when (data["type"]) {
                "ALERT" -> handleAlert(data)
                "MESSAGE" -> handleMessage(data)
                else -> Log.d("FirebaseMessagingService", "Unknown message type: ${data["type"]}")
            }
        }
    }

    private fun handleAlert(data: Map<String, String>) {
        val alert = Alert(
            ID = data["ID"] ?: "",
            ID_CLIENT = data["ID_CLIENT"] ?: "",
            title = data["title"] ?: "",
            text = data["text"] ?: "",
            status = data["status"] ?: "PROGRAMADO"
        )
        showNotification(alert.title, alert.text)
        saveAlert(alert)
    }

    private fun handleMessage(data: Map<String, String>) {
        val title = data["subject"] ?: "Nuevo mensaje"
        val content = data["content"] ?: ""
        showNotification(title, content)
    }

    private fun showNotification(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun saveAlert(alert: Alert) {
        CoroutineScope(Dispatchers.IO).launch {
            alertRepository.createAlert(listOf(alert.ID_CLIENT), alert)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FirebaseMessagingService", "Refreshed token: $token")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    userRepository.updateUserToken(currentUser.id, token)
                }
            } catch (e: Exception) {
                Log.e("FirebaseMessagingService", "Error updating token: ${e.message}")
            }
        }
    }
}