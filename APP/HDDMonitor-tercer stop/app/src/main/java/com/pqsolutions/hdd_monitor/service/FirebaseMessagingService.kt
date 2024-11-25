package com.pqsolutions.hdd_monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
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
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.pqsolutions.hdd_monitor.presentation.managers.NotificationManager as AppNotificationManager

@AndroidEntryPoint
class MessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var firestore: FirebaseFirestore

    @Inject
    lateinit var notificationManager: AppNotificationManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processedMessageIds = mutableSetOf<String>()

    companion object {
        private const val TAG = "MessagingService"
        private const val CHANNEL_ID_RELAY = "relay_status"
        private const val CHANNEL_ID_EVENT = "event_status"
        private const val MAX_PROCESSED_IDS = 100
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MessagingService onCreate")
        createNotificationChannels()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM Message received - Data: ${remoteMessage.data}")

        // Generar un ID único para este mensaje
        val messageId = generateMessageId(remoteMessage)

        // Evitar procesar mensajes duplicados
        if (processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate message: $messageId")
            return
        }

        serviceScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser == null) {
                    Log.w(TAG, "No user logged in")
                    return@launch
                }

                val isAdmin = currentUser.role == UserRole.ADMIN
                val messageType = remoteMessage.data["type"]
                val clientDocName = remoteMessage.data["clientDocName"]

                // Verificar si el mensaje es relevante para el usuario actual
                if (!isAdmin && clientDocName != currentUser.clientDocName) {
                    Log.d(TAG, "Skipping notification - not for this user")
                    return@launch
                }

                when (messageType) {
                    "EVENT_UPDATE" -> handleEventUpdate(remoteMessage, isAdmin)
                    "RELAY_UPDATE" -> handleRelayUpdate(remoteMessage, isAdmin)
                }

                // Agregar el ID del mensaje a los procesados
                processedMessageIds.add(messageId)

                // Limpiar IDs antiguos si se supera el límite
                if (processedMessageIds.size > MAX_PROCESSED_IDS) {
                    processedMessageIds.clear()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing FCM message", e)
            }
        }
    }

    private fun generateMessageId(message: RemoteMessage): String {
        val data = message.data.toString()
        val notification = message.notification?.toString() ?: ""
        return "${data}:${notification}:${System.currentTimeMillis()}".hashCode().toString()
    }

    private suspend fun handleEventUpdate(message: RemoteMessage, isAdmin: Boolean) {
        // Actualizar estado de notificaciones a través del NotificationManager
        notificationManager.refresh()

        // Obtener detalles del cliente si es necesario
        val clientName = if (!isAdmin) {
            message.data["clientDocName"]?.let { getClientName(it) } ?: "Cliente"
        } else {
            "Dashboard HDD"
        }

        // Mostrar notificación
        showNotification(
            title = message.notification?.title ?: "$clientName - Actualización de Evento",
            message = message.notification?.body ?: "Se ha actualizado un evento",
            channelId = CHANNEL_ID_EVENT,
            isAdmin = isAdmin
        )
    }

    private suspend fun saveNotification(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        message: String
    ) {
        try {
            Log.d(TAG, """
            Saving notification:
            Client: $clientDocName
            Panel: $panelDocName
            Relay: $relayName
            Message: $message
        """.trimIndent())

            notificationRepository.createNotification(
                clientDocName = clientDocName,
                panelDocName = panelDocName,
                relayName = relayName,
                message = message
            )

            Log.d(TAG, "Notification saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification: ${e.message}")
        }
    }

    private suspend fun handleRelayUpdate(message: RemoteMessage, isAdmin: Boolean) {
        // Actualizar estado de notificaciones
        notificationManager.refresh()

        // Obtener información adicional si está disponible
        val clientDocName = message.data["clientDocName"] ?: ""
        val panelDocName = message.data["panelDocName"] ?: ""
        val relayName = message.data["relayName"] ?: ""
        val clientName = if (!isAdmin) getClientName(clientDocName) else "Dashboard HDD"

        showNotification(
            title = message.notification?.title ?: "$clientName - Estado de Panel",
            message = message.notification?.body ?: "Ha cambiado el estado de un relay",
            channelId = CHANNEL_ID_RELAY,
            isAdmin = isAdmin
        )

        // Guardar la notificación si hay suficiente información
        if (clientDocName.isNotEmpty() && panelDocName.isNotEmpty() && relayName.isNotEmpty()) {
            saveNotification(
                clientDocName = clientDocName,
                panelDocName = panelDocName,
                relayName = relayName,
                message = message.notification?.body ?: "Cambio de estado en relay $relayName"
            )
        }
    }

    private fun showNotification(
        title: String,
        message: String,
        channelId: String,
        isAdmin: Boolean
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("isAdminNotification", isAdmin)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = "${title}:${message}:${System.currentTimeMillis()}".hashCode()

        Log.d(TAG, """
            FCM Notification shown:
            ID: $notificationId
            Title: $title
            Message: $message
            Channel: $channelId
            IsAdmin: $isAdmin
        """.trimIndent())

        notificationManager.notify(notificationId, notification)
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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal para relays
            NotificationChannel(
                CHANNEL_ID_RELAY,
                "Estado de Relay",
                IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de cambios de estado en relays"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                notificationManager.createNotificationChannel(this)
            }

            // Canal para eventos
            NotificationChannel(
                CHANNEL_ID_EVENT,
                "Estado de Eventos",
                IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de cambios de estado en eventos"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Nuevo token FCM recibido")
        serviceScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    Log.d(TAG, "Actualizando token para usuario: ${currentUser.documentName}")
                    notificationRepository.updateFcmToken(currentUser.documentName, token)
                    Log.d(TAG, "Token FCM actualizado exitosamente")
                } else {
                    Log.w(TAG, "No hay usuario actual para actualizar token")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando token FCM", e)
            }
        }
    }
}