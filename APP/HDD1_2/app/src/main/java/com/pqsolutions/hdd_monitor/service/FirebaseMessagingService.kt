package com.pqsolutions.hdd_monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.NotificationRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var firestore: FirebaseFirestore

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    companion object {
        private const val TAG = "HddMessaging"
        private const val CHANNEL_ID = "relay_status"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Mensaje recibido")

        // Obtener datos del mensaje
        val title = remoteMessage.notification?.title ?: "Alerta de Panel"
        val message = remoteMessage.notification?.body ?: "Se ha producido un cambio en el sistema"
        val clientDocName = remoteMessage.data["clientDocName"]

        // Si tenemos el clientDocName, obtenemos el nombre real del cliente
        if (clientDocName != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val clientName = getClientName(clientDocName)
                    showNotification("$clientName - $title", message)

                    // Guardar en Firestore si es necesario
                    remoteMessage.data["panelDocName"]?.let { panelDocName ->
                        remoteMessage.data["relayName"]?.let { relayName ->
                            saveNotification(clientDocName, panelDocName, relayName, message)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo nombre del cliente", e)
                    showNotification(title, message)
                }
            }
        } else {
            showNotification(title, message)
        }
    }

    private suspend fun getClientName(clientDocName: String): String {
        return try {
            val clientDoc = firestore.collection("hdd-monitor")
                .document("accounts")
                .collection("clients")
                .document(clientDocName)
                .get()
                .await()

            clientDoc.getString("name") ?: clientDocName
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nombre del cliente", e)
            clientDocName
        }
    }

    private fun saveNotification(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        message: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationRepository.createNotification(
                    clientDocName = clientDocName,
                    panelDocName = panelDocName,
                    relayName = relayName,
                    message = message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando notificación: ${e.message}")
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(notificationSound)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Estado de Relay",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de cambios de estado en relays"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Nuevo token FCM recibido")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    notificationRepository.updateFcmToken(currentUser.documentName, token)
                    Log.d(TAG, "Token FCM actualizado en Firestore")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando token FCM", e)
            }
        }
    }
}