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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

    // Configuración para mostrar notificaciones visuales
    private val SHOW_VISUAL_NOTIFICATIONS = true
    private val SHOW_STATUS_NOTIFICATIONS = true

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FCM Service Created")

        // Crear canal de notificación para estados
        createStatusNotificationChannel()

        // Crear canal para notificaciones de relay
        createRelayNotificationChannel()
    }

    private fun createStatusNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal para notificaciones de estado
            NotificationChannel(
                "status_notifications",  // Identificador del canal
                "Estado del Panel",      // Nombre del canal
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones sobre cambios en el estado del panel"
                enableLights(true)
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    private fun createRelayNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal para notificaciones de relay
            NotificationChannel(
                "relay_notifications",
                "Estado del Relay",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones sobre cambios en el estado de los relays"
                enableLights(true)
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
        }
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
            when (data["type"]) {
                "relay" -> processRelayMessage(data)
                "status" -> processStatusMessage(data)
                else -> processRelayMessage(data) // Por defecto tratar como relay
            }
        }
    }

    private fun processStatusMessage(data: Map<String, String>) {
        val clientDocName = data["clientDocName"] ?: ""
        val panelDocName = data["panelDocName"] ?: ""
        val status = data["status"] ?: ""
        val timestamp = data["timestamp"]?.toLongOrNull() ?: Date().time

        Log.d(TAG, "Datos de estado recibidos: $data")
        Log.d(TAG, "Datos extraídos para mensaje de status:" +
                "\n- clientDocName: $clientDocName" +
                "\n- panelDocName: $panelDocName" +
                "\n- status: $status")

        // Solo emitir actualización de estado, no generar notificación visual
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Emitir actualización para UI
                StatusUpdateManager.emitEsp32StatusUpdate(
                    panelDocName = panelDocName,
                    newStatus = status
                )
                Log.d(TAG, "Evento de actualización de ESP32 emitido: $panelDocName -> $status")

                // 2. IMPORTANTE: Actualizar el estado en la base de datos local
                if (clientDocName.isNotEmpty() && panelDocName.isNotEmpty()) {
                    try {
                        // Actualizar directamente el estado en Firestore
                        // Esto asegura que PanelRepository lea el valor correcto
                        val esp32Id = firestore
                            .document("hdd-monitor/accounts/clients/$clientDocName/panels/$panelDocName")
                            .get()
                            .await()
                            .getString("esp32_id") ?: ""

                        if (esp32Id.isNotEmpty()) {
                            firestore.document("hdd-monitor/esp32/registered/$esp32Id")
                                .update("status", status)
                                .await()
                            Log.d(TAG, "Estado de ESP32 $esp32Id actualizado a $status en Firestore")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error actualizando estado en Firestore", e)
                    }
                }

                // 3. NUEVO: Si tiene SHOW_STATUS_NOTIFICATIONS activado
                if (SHOW_STATUS_NOTIFICATIONS && (status == "ONLINE" || status == "OFFLINE")) {
                    showStatusNotification(clientDocName, panelDocName, status)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando mensaje de status", e)
            }
        }
    }

    // Método específico para mostrar notificaciones de estado
    private fun showStatusNotification(clientDocName: String, panelDocName: String, status: String) {
        // Determinar el título y mensaje según el estado
        val isOnline = status == "ONLINE"

        // Ejecutar en hilo de IO para acceder a Firestore
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Obtener información del panel
                val panelResult = kotlin.runCatching {
                    firestore.document("hdd-monitor/accounts/clients/$clientDocName/panels/$panelDocName")
                        .get()
                        .await()
                }

                // 2. Obtener información del cliente
                val clientResult = kotlin.runCatching {
                    firestore.document("hdd-monitor/accounts/clients/$clientDocName")
                        .get()
                        .await()
                }

                // 3. Extraer nombres
                val panelName = panelResult.getOrNull()?.getString("name") ?: panelDocName
                val clientName = clientResult.getOrNull()?.getString("name") ?: clientDocName

                // 4. Crear el mensaje de notificación
                val title = if (isOnline) "Panel Nuevamente ONLINE" else "Alerta: Panel OFFLINE"
                val message = if (isOnline)
                    "El panel \"$panelName\" del cliente $clientName está nuevamente ONLINE"
                else
                    "El panel \"$panelName\" del cliente $clientName está OFFLINE"

                // 5. Mostrar la notificación (en el hilo principal)
                withContext(Dispatchers.Main) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    // Intent para abrir la app
                    val intent = Intent(this@MessagingService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("clientDocName", clientDocName)
                        putExtra("panelDocName", panelDocName)
                        putExtra("status", status)
                        putExtra("type", "status")
                    }

                    val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        this@MessagingService, 0, intent, pendingIntentFlag
                    )

                    // Crear la notificación
                    val channelId = "status_notifications"
                    val notificationBuilder = NotificationCompat.Builder(this@MessagingService, channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

                    // Generar un ID único basado en el panelId y estado
                    // Esto evita duplicados y asegura que cada cambio de estado reemplace la notificación anterior
                    val notificationId = "${panelDocName}_${status}".hashCode()

                    // Mostrar la notificación
                    notificationManager.notify(notificationId, notificationBuilder.build())
                    Log.d(TAG, "Notificación de estado mostrada: $status para panel $panelName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error mostrando notificación de estado", e)
            }
        }
    }

    private fun processRelayMessage(data: Map<String, String>) {
        Log.d(TAG, "=================== INICIO RELAY ===================")
        Log.d(TAG, "Datos de relay recibidos: $data")

        // Extraer datos importantes
        val clientDocName = data["clientDocName"] ?: ""
        val panelDocName = data["panelDocName"] ?: ""
        val type = data["type"] ?: ""

        // Si es un mensaje de estado explícito
        if (type == "status") {
            val status = data["status"] ?: ""
            processStatusMessage(data)
            Log.d(TAG, "=================== FIN RELAY ===================")
            return
        }

        // Es un mensaje de relay normal
        val relayName = data["relayName"] ?: ""
        val oldStatus = data["oldStatus"] ?: ""
        val newStatus = data["newStatus"] ?: ""
        val timestamp = data["timestamp"]?.toLongOrNull() ?: Date().time

        Log.d(TAG, "Datos extraídos para mensaje de relay:" +
                "\n- clientDocName: $clientDocName" +
                "\n- panelDocName: $panelDocName" +
                "\n- relayName: $relayName" +
                "\n- oldStatus: $oldStatus" +
                "\n- newStatus: $newStatus")

        // Varias operaciones en un coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Emitir actualización para UI
                val isEsp32 = relayName.equals("Sistema", ignoreCase = true)
                if (isEsp32) {
                    StatusUpdateManager.emitEsp32StatusUpdate(
                        panelDocName = panelDocName,
                        newStatus = newStatus
                    )
                } else {
                    StatusUpdateManager.emitRelayStatusUpdate(
                        panelDocName = panelDocName,
                        relayName = relayName,
                        newStatus = newStatus
                    )
                }
                Log.d(TAG, "Evento de actualización emitido: $panelDocName -> $newStatus (${if (isEsp32) "ESP32" else relayName})")

                // 2. Mostrar notificación visual si está habilitado
                if (SHOW_VISUAL_NOTIFICATIONS && !isEsp32) {
                    // Solo mostrar para relays normales, no para el Sistema
                    showRelayNotification(clientDocName, panelDocName, relayName, oldStatus, newStatus)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando mensaje de relay", e)
            }
        }

        Log.d(TAG, "=================== FIN RELAY ===================")
    }

    // NUEVO: Método para mostrar notificaciones específicas de relay
    private fun showRelayNotification(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        oldStatus: String,
        newStatus: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Obtener información del panel
                val panelResult = kotlin.runCatching {
                    firestore.document("hdd-monitor/accounts/clients/$clientDocName/panels/$panelDocName")
                        .get()
                        .await()
                }

                // 2. Obtener información del cliente
                val clientResult = kotlin.runCatching {
                    firestore.document("hdd-monitor/accounts/clients/$clientDocName")
                        .get()
                        .await()
                }

                // 3. Extraer nombres
                val panelName = panelResult.getOrNull()?.getString("name") ?: panelDocName
                val clientName = clientResult.getOrNull()?.getString("name") ?: clientDocName

                // 4. Crear el mensaje de notificación
                val title = "$clientName - Cambio de Estado"
                val message = "El relay $relayName del panel \"$panelName\" ha cambiado de $oldStatus a $newStatus"

                // 5. Mostrar la notificación (en el hilo principal)
                withContext(Dispatchers.Main) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    // Intent para abrir la app
                    val intent = Intent(this@MessagingService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("clientDocName", clientDocName)
                        putExtra("panelDocName", panelDocName)
                        putExtra("relayName", relayName)
                        putExtra("newStatus", newStatus)
                        putExtra("type", "relay")
                    }

                    val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        this@MessagingService, 0, intent, pendingIntentFlag
                    )

                    // Crear la notificación
                    val channelId = "relay_notifications"
                    val notificationBuilder = NotificationCompat.Builder(this@MessagingService, channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

                    // Generar un ID único basado en el relay y su estado
                    // Esto evita duplicados y asegura que cada cambio de estado reemplace la notificación anterior
                    val notificationId = "${panelDocName}_${relayName}_${newStatus}".hashCode()

                    // Mostrar la notificación
                    notificationManager.notify(notificationId, notificationBuilder.build())
                    Log.d(TAG, "Notificación de relay mostrada: $relayName -> $newStatus para panel $panelName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error mostrando notificación de relay", e)
            }
        }
    }

    companion object {
        private const val TAG = "MessagingService"
    }
}