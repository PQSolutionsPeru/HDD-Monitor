package com.pqsolutions.hdd_monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pqsolutions.hdd_monitor.R
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
            status = data["status"] ?: "NEW"
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
        val channelId = "hdd_monitor_channel"
        val notificationId = System.currentTimeMillis().toInt()

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "HDD Monitor Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de HDD Monitor"
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun saveAlert(alert: Alert) {
        CoroutineScope(Dispatchers.IO).launch {
            alertRepository.createAlert(alert.ID_CLIENT, alert)
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