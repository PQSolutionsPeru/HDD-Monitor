package com.pqsolutions.hdd_monitor.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.presentation.MainActivity
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import com.pqsolutions.hdd_monitor.domain.model.UserRole

class EventNotificationScheduler(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "EventNotificationScheduler"
        private const val NOTIFICATION_HOURS_BEFORE = 3
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

        // Prefijo para identificar los trabajos programados para notificaciones de eventos
        private const val WORK_PREFIX = "event_reminder_"

        // Canal de notificación específico para recordatorios de eventos
        private const val CHANNEL_ID = "event_reminder_notifications"
    }

    /**
     * Programa una notificación para un evento específico
     */
    suspend fun scheduleEventReminder(
        eventId: String,
        clientDocName: String,
        eventDateTime: String,
        eventTitle: String,
        eventType: String,
        panelName: String?
    ) {
        try {
            // 1. Calcular cuándo debería mostrarse la notificación (3 horas antes)
            val eventTime = LocalDateTime.parse(eventDateTime, DATE_FORMATTER)
            val notificationTime = eventTime.minusHours(NOTIFICATION_HOURS_BEFORE.toLong())

            // 2. Si la notificación debería mostrarse en el pasado, ignorarla
            val now = LocalDateTime.now()
            if (notificationTime.isBefore(now)) {
                Log.d(TAG, "Evento $eventId ya pasó el tiempo de notificación, ignorando")
                return
            }

            // 3. Calcular el delay hasta la notificación
            val delay = Duration.between(now, notificationTime)
            val delayMillis = delay.toMillis()

            // 4. Configurar los datos para el trabajador
            val inputData = workDataOf(
                "eventId" to eventId,
                "clientDocName" to clientDocName,
                "eventTitle" to eventTitle,
                "eventType" to eventType,
                "eventDateTime" to eventDateTime,
                "panelName" to (panelName ?: "")
            )

            // 5. Crear la solicitud del trabajo programado
            val workRequest = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(eventId) // Usamos el eventId como tag para poder cancelarlo después
                .build()

            // 6. Programar el trabajo
            val workId = "${WORK_PREFIX}${eventId}"
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    workId,
                    ExistingWorkPolicy.REPLACE, // Reemplazar si ya existe
                    workRequest
                )

            Log.d(TAG, "Notificación programada para $eventId el $notificationTime (${delay.toHours()} horas)")
        } catch (e: Exception) {
            Log.e(TAG, "Error programando notificación para evento $eventId: ${e.message}", e)
        }
    }

    /**
     * Cancela una notificación programada para un evento
     */
    fun cancelEventReminder(eventId: String) {
        try {
            val workId = "${WORK_PREFIX}${eventId}"
            WorkManager.getInstance(context).cancelUniqueWork(workId)
            Log.d(TAG, "Notificación cancelada para evento $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando notificación para evento $eventId", e)
        }
    }

    /**
     * Programa notificaciones para todos los eventos pendientes
     */
    suspend fun scheduleAllPendingEvents() {
        try {
            val currentUser = userRepository.getCurrentUser() ?: return

            // Si es admin, programar para todos los eventos
            if (currentUser.role == UserRole.ADMIN) {
                val eventsSnapshot = firestore.collectionGroup("events")
                    .whereEqualTo("status", "PROGRAMADO")
                    .get()
                    .await()

                for (doc in eventsSnapshot.documents) {
                    processEventDocument(doc)
                }
                Log.d(TAG, "Se programaron ${eventsSnapshot.size()} eventos para administrador")
            } else {
                // Si es usuario normal, solo programar los de su cliente
                val clientDocName = currentUser.clientDocName
                if (clientDocName.isNotEmpty()) {
                    val eventsSnapshot = firestore.collection("hdd-monitor/accounts/clients/$clientDocName/events")
                        .whereEqualTo("status", "PROGRAMADO")
                        .get()
                        .await()

                    for (doc in eventsSnapshot.documents) {
                        processEventDocument(doc)
                    }
                    Log.d(TAG, "Se programaron ${eventsSnapshot.size()} eventos para usuario de $clientDocName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error programando notificaciones para eventos pendientes", e)
        }
    }

    private suspend fun processEventDocument(doc: DocumentSnapshot) {
        try {
            val eventId = doc.id
            val clientPath = doc.reference.path.split("/")
            val clientDocName = clientPath.getOrNull(clientPath.indexOf("clients") + 1) ?: return

            val dateTime = doc.getString("date_time") ?: return
            val title = doc.getString("title") ?: "Evento"
            val type = doc.getString("type") ?: "Evento"
            val panelName = doc.getString("panelName")

            scheduleEventReminder(
                eventId = eventId,
                clientDocName = clientDocName,
                eventDateTime = dateTime,
                eventTitle = title,
                eventType = type,
                panelName = panelName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando documento de evento: ${e.message}")
        }
    }

    /**
     * Crea el canal de notificación para recordatorios de eventos
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal específico para recordatorios de eventos
            NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de Eventos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones recordatorias de eventos próximos"
                enableLights(true)
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    /**
     * Worker que muestra la notificación de recordatorio de evento
     */
    class EventReminderWorker(
        private val context: Context,
        params: WorkerParameters,
        private val firestore: FirebaseFirestore,
        private val userRepository: UserRepository
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val eventId = inputData.getString("eventId") ?: return Result.failure()
            val clientDocName = inputData.getString("clientDocName") ?: return Result.failure()
            val eventTitle = inputData.getString("eventTitle") ?: "Evento"
            val eventType = inputData.getString("eventType") ?: "Evento"
            val eventDateTime = inputData.getString("eventDateTime") ?: return Result.failure()
            val panelName = inputData.getString("panelName")

            try {
                // 1. Verificar si el evento sigue en estado PROGRAMADO
                val eventDoc = firestore.document("hdd-monitor/accounts/clients/$clientDocName/events/$eventId")
                    .get()
                    .await()

                val status = eventDoc.getString("status")
                if (status != "PROGRAMADO") {
                    Log.d(TAG, "Evento $eventId ya no está en estado PROGRAMADO: $status")
                    return Result.success()
                }

                // 2. Verificar si el usuario actual debe recibir esta notificación
                val currentUser = userRepository.getCurrentUser()
                if (currentUser == null) {
                    Log.d(TAG, "No hay usuario logueado, cancelando notificación")
                    return Result.success()
                }

                val isAdmin = currentUser.role == UserRole.ADMIN
                val isUserOfClient = currentUser.clientDocName == clientDocName

                if (!isAdmin && !isUserOfClient) {
                    Log.d(TAG, "Usuario no es admin ni pertenece al cliente $clientDocName")
                    return Result.success()
                }

                // 3. Mostrar la notificación
                showEventReminderNotification(
                    eventId = eventId,
                    clientDocName = clientDocName,
                    eventTitle = eventTitle,
                    eventType = eventType,
                    eventDateTime = eventDateTime,
                    panelName = panelName
                )

                return Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error mostrando notificación para evento $eventId: ${e.message}", e)
                return Result.failure()
            }
        }

        private fun showEventReminderNotification(
            eventId: String,
            clientDocName: String,
            eventTitle: String,
            eventType: String,
            eventDateTime: String,
            panelName: String?
        ) {
            try {
                // 1. Crear intent para abrir el evento
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("eventId", eventId)
                    putExtra("clientDocName", clientDocName)
                    putExtra("type", "event")
                }

                val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent, pendingIntentFlag
                )

                // 2. Construir mensaje apropiado
                val title = "Próximo evento: $eventType"
                val message = buildString {
                    append("El evento \"$eventTitle\" ")
                    if (panelName?.isNotEmpty() == true) {
                        append("para el panel \"$panelName\" ")
                    }
                    append("está programado para $eventDateTime (en aproximadamente $NOTIFICATION_HOURS_BEFORE horas)")
                }

                // 3. Crear y mostrar la notificación
                val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notificationId = "reminder_$eventId".hashCode()
                notificationManager.notify(notificationId, notificationBuilder.build())

                Log.d(TAG, "Notificación de recordatorio mostrada para evento $eventId")
            } catch (e: Exception) {
                Log.e(TAG, "Error mostrando notificación de recordatorio", e)
            }
        }
    }
}