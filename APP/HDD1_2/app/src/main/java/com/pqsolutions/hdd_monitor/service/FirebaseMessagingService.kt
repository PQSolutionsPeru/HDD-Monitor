package com.pqsolutions.hdd_monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HddFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var alertRepository: AlertRepository

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        remoteMessage.data.let { data ->
            val alert = Alert(
                ID = data["ID"] ?: "",
                ID_CLIENT = data["ID_CLIENT"] ?: "",
                title = data["title"] ?: "",
                text = data["text"] ?: "",
                status = data["status"] ?: "NEW"
            )
            showNotification(alert)
            saveAlert(alert)
        }
    }

    private fun showNotification(alert: Alert) {
        val channelId = "HDD_MONITOR_CHANNEL"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "HDD Monitor Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(alert.ID.hashCode(), notificationBuilder.build())
    }

    private fun saveAlert(alert: Alert) {
        CoroutineScope(Dispatchers.IO).launch {
            alertRepository.createAlert(alert.ID_CLIENT, alert)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Aquí puedes enviar el nuevo token a tu servidor si es necesario
        CoroutineScope(Dispatchers.IO).launch {
            // Implementa la lógica para enviar el token al servidor
        }
    }
}