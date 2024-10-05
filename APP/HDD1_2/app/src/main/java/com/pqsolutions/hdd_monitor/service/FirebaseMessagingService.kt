package com.pqsolutions.hdd_monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
        private const val TAG = "HddFirebaseMessaging"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received: ${remoteMessage.data}")
        remoteMessage.data.let { data ->
            when (data["type"]) {
                "ALERT" -> handleAlert(data)
                "MESSAGE" -> handleMessage(data)
                "PANEL_UPDATE" -> handlePanelUpdate(data)
                else -> Log.d(TAG, "Unknown message type: ${data["type"]}")
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
        sendBroadcast(Intent("com.pqsolutions.hdd_monitor.NEW_ALERT"))
    }

    private fun handleMessage(data: Map<String, String>) {
        val title = data["subject"] ?: "Nuevo mensaje"
        val content = data["content"] ?: ""
        showNotification(title, content)
        sendBroadcast(Intent("com.pqsolutions.hdd_monitor.NEW_MESSAGE"))
    }

    private fun handlePanelUpdate(data: Map<String, String>) {
        val panelId = data["panelId"] ?: ""
        val panelName = data["panelName"] ?: "Panel desconocido"
        val relayName = data["relayName"] ?: "Relay desconocido"
        val relayStatus = data["relayStatus"] ?: "Estado desconocido"
        val title = "Actualización de Panel"
        val content = "Panel: $panelName, Relay: $relayName, Estado: $relayStatus"
        showNotification(title, content)

        val intent = Intent("com.pqsolutions.hdd_monitor.PANEL_UPDATE").apply {
            putExtra("panelId", panelId)
            putExtra("panelName", panelName)
            putExtra("relayName", relayName)
            putExtra("relayStatus", relayStatus)
        }
        sendBroadcast(intent)
    }

    private fun showNotification(title: String, content: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationId = System.currentTimeMillis().toInt()

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@HddFirebaseMessagingService,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(notificationId, notificationBuilder.build())
            } else {
                Log.w(TAG, "Notification permission not granted")
            }
        }
    }

    private fun createNotificationChannel() {
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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun saveAlert(alert: Alert) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                alertRepository.createAlert(listOf(alert.ID_CLIENT), alert)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving alert: ${e.message}")
            }
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