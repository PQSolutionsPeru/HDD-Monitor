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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var firestore: FirebaseFirestore

    companion object {
        private const val TAG = "MessagingService"
        private const val CHANNEL_ID = "relay_status"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Mensaje recibido: ${remoteMessage.data}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtener el nombre del cliente
                val clientDocName = remoteMessage.data["clientDocName"]
                val clientName = if (clientDocName != null) {
                    getClientName(clientDocName)
                } else "Cliente"

                // Obtener el nombre del panel
                val panelDocName = remoteMessage.data["panelDocName"]
                val panelName = if (panelDocName != null && clientDocName != null) {
                    getPanelName(clientDocName, panelDocName)
                } else "Panel"

                // Construir el mensaje
                val relayName = remoteMessage.data["relayName"] ?: "Relay"
                val oldStatus = remoteMessage.data["oldStatus"] ?: ""
                val newStatus = remoteMessage.data["newStatus"] ?: ""

                val message = if (oldStatus.isNotEmpty() && newStatus.isNotEmpty()) {
                    "El relay $relayName del panel $panelName ha cambiado de $oldStatus a $newStatus"
                } else {
                    remoteMessage.notification?.body ?: "Se ha producido un cambio en el sistema"
                }

                val title = "$clientName - Cambio de Estado"

                // Mostrar la notificación
                showNotification(title, message)

                // Guardar la notificación si es necesario
                if (clientDocName != null && panelDocName != null) {
                    saveNotification(
                        clientDocName = clientDocName,
                        panelDocName = panelDocName,
                        relayName = relayName,
                        message = message
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación", e)
                // Fallback a mensaje básico si hay error
                showNotification(
                    remoteMessage.notification?.title ?: "Alerta de Panel",
                    remoteMessage.notification?.body ?: "Se ha producido un cambio en el sistema"
                )
            }
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

            clientDoc.getString("name") ?: "Cliente"
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nombre del cliente", e)
            "Cliente"
        }
    }

    private suspend fun getPanelName(clientDocName: String, panelDocName: String): String {
        return try {
            val panelDoc = firestore.collection("hdd-monitor")
                .document("accounts")
                .collection("clients")
                .document(clientDocName)
                .collection("panels")
                .document(panelDocName)
                .get()
                .await()

            panelDoc.getString("name") ?: "Panel"
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nombre del panel", e)
            "Panel"
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