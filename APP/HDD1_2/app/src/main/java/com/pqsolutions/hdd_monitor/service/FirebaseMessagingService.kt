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
import com.pqsolutions.hdd_monitor.data.ClientRepository
import com.pqsolutions.hdd_monitor.data.NotificationRepository
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.presentation.MainActivity
import com.pqsolutions.hdd_monitor.util.StatusUpdateManager
import com.pqsolutions.hdd_monitor.util.StatusUpdate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class MessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var clientRepository: ClientRepository

    @Inject
    lateinit var panelRepository: PanelRepository

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var firestore: FirebaseFirestore

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FCM Service Created")
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New token: $token")
        // Aquí puedes enviar el token al servidor
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "=================== INICIO MENSAJE ===================")
        Log.d(TAG, "Mensaje recibido desde: ${remoteMessage.from}")
        Log.d(TAG, "Datos completos del mensaje: ${remoteMessage.data}")

        // Procesar notificación
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Notificación: ${notification.title} - ${notification.body}")
            Log.d(TAG, "Priority: ${remoteMessage.priority}")
            Log.d(TAG, "Original Priority: ${remoteMessage.originalPriority}")
        }
        Log.d(TAG, "=================== FIN MENSAJE ===================")

        // Procesar datos específicos
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            if (data.containsKey("type")) {
                when (data["type"]) {
                    "relay" -> processRelayMessage(data)
                    // Otros tipos de mensajes pueden procesarse aquí
                }
            }
        }

        // Dependiendo del tipo de mensaje, mostrar notificación
        if (remoteMessage.notification != null) {
            val notificationTitle = remoteMessage.notification?.title ?: "HDD Monitor"
            val notificationBody = remoteMessage.notification?.body ?: "Nueva alerta"
            sendNotification(notificationTitle, notificationBody)
        }
    }

    private fun processRelayMessage(data: Map<String, String>) {
        Log.d(TAG, "=================== INICIO RELAY ===================")
        Log.d(TAG, "Datos de relay recibidos: $data")

        // Extraer datos importantes
        val clientDocName = data["clientDocName"] ?: ""
        val panelDocName = data["panelDocName"] ?: ""
        val relayName = data["relayName"] ?: ""
        val oldStatus = data["oldStatus"] ?: ""
        val newStatus = data["newStatus"] ?: ""
        val timestamp = data["timestamp"]?.toLongOrNull() ?: Date().time

        Log.d(TAG, "Datos extraídos:\n" +
                "- clientDocName: $clientDocName\n" +
                "- panelDocName: $panelDocName\n" +
                "- relayName: $relayName\n" +
                "- oldStatus: $oldStatus\n" +
                "- newStatus: $newStatus")

        // NUEVO: Emitir actualización de estado para actualización inmediata del dashboard
        CoroutineScope(Dispatchers.IO).launch {
            val isEsp32 = relayName.equals("Sistema", ignoreCase = true)
            StatusUpdateManager.emitUpdate(
                StatusUpdate(
                    panelDocName = panelDocName,
                    newStatus = newStatus,
                    isEsp32 = isEsp32,
                    relayName = relayName
                )
            )
            Log.d(TAG, "Evento de actualización emitido: $panelDocName -> $newStatus (${if (isEsp32) "ESP32" else relayName})")
        }

        // Procesar mensajes de cambio de estado
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtener datos del cliente
                val clientResult = clientRepository.getClient(clientDocName)
                val clientName = clientResult.getOrNull()?.name ?: "Cliente desconocido"

                // Obtener datos del panel (CORREGIDO: usar Firestore directamente en lugar de método no existente)
                var panelName = "Panel desconocido"
                try {
                    val panelDoc = firestore
                        .document("hdd-monitor/accounts/clients/$clientDocName/panels/$panelDocName")
                        .get()
                        .await()

                    if (panelDoc.exists()) {
                        panelName = panelDoc.getString("name") ?: "Panel desconocido"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo datos del panel", e)
                }

                // Texto de notificación
                val notificationText = if (relayName.equals("Sistema", ignoreCase = true)) {
                    "$clientName - Cambio de Estado - El chip asignado al panel \"$panelName\" ha cambiado de $oldStatus a $newStatus"
                } else {
                    "$clientName - Cambio de Estado - El relay $relayName del panel \"$panelName\" ha cambiado de $oldStatus a $newStatus"
                }

                Log.d(TAG, "Preparando notificación: $notificationText")

                // Guardar en Firestore (si corresponde)
                // ... (código existente para guardar en Firestore)

                // Mostrar notificación al usuario
                sendNotification("HDD Monitor - Cambio de Estado", notificationText)
                Log.d(TAG, "Mostrando notificación: $notificationText")
                Log.d(TAG, "Notificación enviada exitosamente")

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando mensaje de relay", e)
            }
        }

        Log.d(TAG, "=================== FIN RELAY ===================")
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Para Android Oreo y superior, es necesario crear un canal de notificación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "HDD Monitor Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Mostrar notificación con ID único
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MessagingService"
    }
}