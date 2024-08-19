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
        remoteMessage.notification?.let { notification ->
            val alert = Alert(
                title = notification.title ?: "",
                description = notification.body ?: "",
                dateTime = System.currentTimeMillis().toString(),
                status = "NEW",
                priority = "HIGH"
            )
            showNotification(alert)
            saveAlert(alert)
        }
    }

    private fun showNotification(alert: Alert) {
        val channelId = "HDD_MONITOR_CHANNEL"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(alert.title)
            .setContentText(alert.description)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "HDD Monitor Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun saveAlert(alert: Alert) {
        CoroutineScope(Dispatchers.IO).launch {
            alertRepository.createAlert("default_client_id", alert)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Aquí puedes enviar el nuevo token a tu servidor si es necesario
    }
}