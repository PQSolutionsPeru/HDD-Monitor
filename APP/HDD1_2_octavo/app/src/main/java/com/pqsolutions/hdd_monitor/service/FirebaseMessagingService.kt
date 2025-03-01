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
        private const val CHANNEL_ID_RELAY = "relay_status"
        private const val CHANNEL_ID_EVENT = "event_notifications"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FCM Service Created")
        createNotificationChannels()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "=================== INICIO MENSAJE ===================")
        Log.d(TAG, "Mensaje recibido desde: ${remoteMessage.from}")
        Log.d(TAG, "Datos completos del mensaje: ${remoteMessage.data}")
        Log.d(TAG, "Notificación: ${remoteMessage.notification?.title} - ${remoteMessage.notification?.body}")
        Log.d(TAG, "Priority: ${remoteMessage.priority}")
        Log.d(TAG, "Original Priority: ${remoteMessage.originalPriority}")
        Log.d(TAG, "=================== FIN MENSAJE ===================")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verificar si hay usuario logueado antes de procesar la notificación
                val currentUser = userRepository.getCurrentUser()
                if (currentUser == null) {
                    Log.d(TAG, "No hay usuario logueado, ignorando notificación")
                    return@launch
                }

                when {
                    // Si contiene relayName, es una notificación de relay
                    remoteMessage.data.containsKey("relayName") -> {
                        handleRelayNotification(remoteMessage)
                    }
                    // Si contiene eventId, es una notificación de evento
                    remoteMessage.data.containsKey("eventId") -> {
                        handleEventNotification(remoteMessage)
                    }
                    // Caso por defecto
                    else -> {
                        showGenericNotification(remoteMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación", e)
            }
        }
    }

    private suspend fun handleRelayNotification(remoteMessage: RemoteMessage) {
        Log.d(TAG, "=================== INICIO RELAY ===================")
        Log.d(TAG, "Datos de relay recibidos: ${remoteMessage.data}")

        val clientDocName = remoteMessage.data["clientDocName"]
        val panelDocName = remoteMessage.data["panelDocName"]
        val relayName = remoteMessage.data["relayName"]
        val oldStatus = remoteMessage.data["oldStatus"]
        val newStatus = remoteMessage.data["newStatus"]

        Log.d(TAG, """
            Datos extraídos:
            - clientDocName: $clientDocName
            - panelDocName: $panelDocName
            - relayName: $relayName
            - oldStatus: $oldStatus
            - newStatus: $newStatus
        """.trimIndent())

        if (clientDocName == null || panelDocName == null || relayName == null ||
            oldStatus == null || newStatus == null) {
            Log.e(TAG, "Datos faltantes en la notificación de relay")
            return
        }

        val clientName = getClientName(clientDocName)
        val panelName = getPanelName(clientDocName, panelDocName)

        val message = "El relay $relayName del panel \"$panelName\" ha cambiado de $oldStatus a $newStatus"
        val title = "$clientName - Cambio de Estado"

        Log.d(TAG, "Preparando notificación: $title - $message")

        // Guardar la notificación
        saveNotification(clientDocName, panelDocName, relayName, message)

        // Mostrar la notificación
        showNotification(
            title = title,
            message = message,
            channelId = CHANNEL_ID_RELAY,
            intent = createMainIntent(clientDocName, panelDocName, "relay").apply {
                putExtra("relayName", relayName)
                putExtra("newStatus", newStatus)
                putExtra("oldStatus", oldStatus)
            }
        )
        Log.d(TAG, "=================== FIN RELAY ===================")
    }

    private suspend fun handleEventNotification(remoteMessage: RemoteMessage) {
        val clientDocName = remoteMessage.data["clientDocName"] ?: return
        val eventId = remoteMessage.data["eventId"] ?: return
        val newStatus = remoteMessage.data["newStatus"]
        val oldStatus = remoteMessage.data["oldStatus"]

        val title = remoteMessage.notification?.title ?: "Actualización de Evento"
        val message = remoteMessage.notification?.body ?: "Se ha actualizado un evento"

        // Mostrar la notificación
        showNotification(
            title = title,
            message = message,
            channelId = CHANNEL_ID_EVENT,
            intent = createMainIntent(
                clientDocName = clientDocName,
                itemId = eventId,
                type = "event"
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_type", "event")
                putExtra("event_id", eventId)
                putExtra("new_status", newStatus)
                putExtra("old_status", oldStatus)
            }
        )

        Log.d(TAG, "Notificación de evento mostrada: $title - $message")
    }

    private fun showGenericNotification(remoteMessage: RemoteMessage) {
        showNotification(
            title = remoteMessage.notification?.title ?: "Notificación",
            message = remoteMessage.notification?.body ?: "Se ha producido una actualización",
            channelId = CHANNEL_ID_EVENT,
            intent = createMainIntent(null, null, null)
        )
    }

    private suspend fun getClientName(clientDocName: String): String {
        return try {
            val clientDoc = firestore.collection("hdd-monitor/accounts/clients")
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
            val panelDoc = firestore.collection("hdd-monitor/accounts/clients/$clientDocName/panels")
                .document(panelDocName)
                .get()
                .await()
            panelDoc.getString("name") ?: "Panel"
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nombre del panel", e)
            "Panel"
        }
    }

    private fun createMainIntent(
        clientDocName: String?,
        itemId: String?,
        type: String?
    ): Intent {
        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            clientDocName?.let { putExtra("clientDocName", it) }
            itemId?.let { putExtra("${type}Id", it) }
            putExtra("notification", true)
            putExtra("notificationType", type)
        }
    }

    private fun showNotification(
        title: String,
        message: String,
        channelId: String,
        intent: Intent
    ) {
        Log.d(TAG, "Mostrando notificación: $title - $message")

        // Asegurar que el canal existe
        createNotificationChannels()

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setPriority(NotificationCompat.PRIORITY_MAX)  // Cambio a MAX
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            Log.d(TAG, "Notificación enviada exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal para notificaciones de relay
            NotificationChannel(
                CHANNEL_ID_RELAY,
                "Estado de Relay",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de cambios de estado en relays"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                notificationManager.createNotificationChannel(this)
            }

            // Canal para notificaciones de eventos
            NotificationChannel(
                CHANNEL_ID_EVENT,
                "Eventos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de actualizaciones de eventos"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                notificationManager.createNotificationChannel(this)
            }
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

    override fun onNewToken(token: String) {
        Log.d(TAG, "Nuevo token FCM recibido: $token")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    notificationRepository.updateFcmToken(currentUser.documentName, token)
                    Log.d(TAG, "Token FCM actualizado en Firestore para usuario: ${currentUser.documentName}")
                } else {
                    Log.e(TAG, "No se pudo actualizar el token FCM: usuario actual es null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando token FCM", e)
            }
        }
    }
}