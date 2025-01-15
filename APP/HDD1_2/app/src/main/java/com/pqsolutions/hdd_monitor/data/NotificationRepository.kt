package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.util.Constants
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "NotificationRepository"
        private const val BASE_PATH = "hdd-monitor/accounts/clients"
        private const val DATE_FORMAT = "dd/MM/yyyy, HH:mm"  // Formato unificado
        private const val MAX_NOTIFICATIONS = 20
        private const val HOURS_TO_KEEP = 24L
    }

    // Flow principal de notificaciones para un cliente específico (usuarios)
    fun getNotificationsFlow(clientDocName: String): Flow<List<Notification>> = callbackFlow {
        if (clientDocName.isBlank()) {
            Log.w(TAG, "Intento de obtener notificaciones con clientDocName vacío")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val collectionPath = "$BASE_PATH/$clientDocName/notifications"
        Log.d(TAG, "Consultando notificaciones en: $collectionPath")

        val notificationsRef = firestore.collection(collectionPath)
            .orderBy("date_time", Query.Direction.DESCENDING)
            .limit(MAX_NOTIFICATIONS.toLong())

        val listenerRegistration = notificationsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error getting notifications", error)
                close(error)
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                try {
                    Log.d(TAG, "Documentos encontrados: ${querySnapshot.documents.size}")
                    querySnapshot.documents.forEach { doc ->
                        Log.d(TAG, "Documento: ${doc.id}")
                        Log.d(TAG, "Fecha: ${doc.data?.get("date_time")}")
                    }

                    val notifications = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: emptyMap()
                            Log.d(TAG, "Procesando documento: ${doc.id} con datos: $data")

                            val notificationMap = data.toMutableMap().apply {
                                this["documentName"] = doc.id
                                this["clientDocName"] = clientDocName

                                // Generar timestamp si no existe
                                if (!containsKey("timestamp")) {
                                    val dateStr = this["date_time"] as? String ?: ""
                                    val lastUpdate = (this["lastUpdate"] as? com.google.firebase.Timestamp)?.toDate()?.time
                                    this["timestamp"] = lastUpdate ?: try {
                                        LocalDateTime.parse(
                                            dateStr,
                                            DateTimeFormatter.ofPattern(DATE_FORMAT)
                                        ).atZone(Constants.TimeZone.PERU_ZONE)
                                            .toInstant()
                                            .toEpochMilli()
                                    } catch (e: Exception) {
                                        System.currentTimeMillis()
                                    }
                                }
                            }

                            Notification.fromMap(notificationMap).also { notification ->
                                Log.d(TAG, "Procesada notificación: ${notification.toLogString()}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error procesando documento ${doc.id}", e)
                            null
                        }
                    }

                    Log.d(TAG, "Total de notificaciones válidas procesadas: ${notifications.size}")
                    trySend(notifications)

                    launch {
                        try {
                            cleanupOldNotifications(clientDocName)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error en cleanup de notificaciones", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando snapshot", e)
                    close(e)
                }
            } ?: run {
                Log.w(TAG, "Snapshot nulo recibido")
                trySend(emptyList())
            }
        }

        awaitClose {
            Log.d(TAG, "Cerrando listener de notificaciones")
            listenerRegistration.remove()
        }
    }

    // Flow para todas las notificaciones (para administradores)
    fun getNotificationsFlow(): Flow<List<Notification>> = callbackFlow {
        Log.d(TAG, "Iniciando consulta de todas las notificaciones")

        val listenerRegistration = firestore.collectionGroup("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(MAX_NOTIFICATIONS.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting all notifications", error)
                    close(error)
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    try {
                        Log.d(TAG, "Documentos encontrados: ${querySnapshot.documents.size}")
                        querySnapshot.documents.forEach { doc ->
                            Log.d(TAG, "Documento: ${doc.id}")
                            Log.d(TAG, "Fecha: ${doc.data?.get("date_time")}")
                        }

                        val notifications = querySnapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: emptyMap()
                                val notificationMap = data.toMutableMap().apply {
                                    this["documentName"] = doc.id
                                    val clientDocName = doc.reference.path
                                        .split("/")
                                        .let { parts ->
                                            parts.getOrNull(parts.indexOf("clients") + 1) ?: ""
                                        }
                                    this["clientDocName"] = clientDocName

                                    // Generar timestamp si no existe usando lastUpdate o date_time
                                    if (!containsKey("timestamp")) {
                                        val dateStr = this["date_time"] as? String ?: ""
                                        val lastUpdate = (this["lastUpdate"] as? com.google.firebase.Timestamp)?.toDate()?.time
                                        this["timestamp"] = lastUpdate ?: try {
                                            LocalDateTime.parse(
                                                dateStr,
                                                DateTimeFormatter.ofPattern(DATE_FORMAT)
                                            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        } catch (e: Exception) {
                                            System.currentTimeMillis()
                                        }
                                    }
                                }

                                Notification.fromMap(notificationMap)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error procesando documento ${doc.id}", e)
                                null
                            }
                        }

                        trySend(notifications)

                        launch {
                            try {
                                cleanupAllClientsOldNotifications()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error en cleanup de notificaciones", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando snapshot", e)
                        close(e)
                    }
                } ?: run {
                    Log.w(TAG, "Snapshot nulo recibido")
                    trySend(emptyList())
                }
            }

        awaitClose {
            Log.d(TAG, "Cerrando listener de notificaciones globales")
            listenerRegistration.remove()
        }
    }

    private suspend fun cleanupOldNotifications(clientDocName: String) {
        try {
            val cutoffTime = System.currentTimeMillis() - (HOURS_TO_KEEP * 60 * 60 * 1000)

            // Eliminar notificaciones más antiguas que HOURS_TO_KEEP
            deleteNotificationsOlderThan(clientDocName, cutoffTime)
                .onFailure { e ->
                    Log.e(TAG, "Error limpiando notificaciones antiguas", e)
                }

            // Mantener solo las últimas MAX_NOTIFICATIONS
            keepOnlyLastN(clientDocName, MAX_NOTIFICATIONS)
                .onFailure { e ->
                    Log.e(TAG, "Error manteniendo últimas notificaciones", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error en cleanup de notificaciones", e)
        }
    }

    private suspend fun cleanupAllClientsOldNotifications() {
        try {
            val clients = firestore.collection(BASE_PATH)
                .get()
                .await()

            clients.documents.forEach { clientDoc ->
                cleanupOldNotifications(clientDoc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en cleanup de todos los clientes", e)
        }
    }

    suspend fun deleteNotificationsOlderThan(clientDocName: String, timestamp: Long): Result<Unit> = runCatching {
        val batch = firestore.batch()
        val notifications = firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .whereLessThan("timestamp", timestamp)
            .get()
            .await()

        notifications.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        batch.commit().await()
        Log.d(TAG, "Deleted ${notifications.size()} old notifications for client: $clientDocName")
    }

    suspend fun keepOnlyLastN(clientDocName: String, n: Int): Result<Unit> = runCatching {
        val notifications = firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()

        if (notifications.size() > n) {
            val batch = firestore.batch()
            notifications.documents
                .drop(n)
                .forEach { doc ->
                    batch.delete(doc.reference)
                }
            batch.commit().await()
            Log.d(TAG, "Kept only last $n notifications for client: $clientDocName")
        }
    }

    suspend fun markNotificationAsRead(
        clientDocName: String,
        notificationDocName: String
    ): Result<Unit> = runCatching {
        firestore.document("$BASE_PATH/$clientDocName/notifications/$notificationDocName")
            .update("isRead", true)
            .await()
        Log.d(TAG, "Notification marked as read: $notificationDocName")
    }

    suspend fun markAllNotificationsAsRead(clientDocName: String, isAdmin: Boolean = false): Result<Unit> = runCatching {
        if (isAdmin) {
            // Para admins, marcar todas las notificaciones como leídas pero mantener el estado separado
            val batch = firestore.batch()
            val notifications = firestore.collectionGroup("notifications")
                .whereEqualTo("isRead", false)
                .get()
                .await()

            notifications.documents.forEach { doc ->
                batch.update(doc.reference, mapOf(
                    "isRead" to true,
                    "readByAdmin" to true
                ))
            }
            batch.commit().await()
        } else {
            // Para usuarios, solo marcar las de su cliente
            val batch = firestore.batch()
            val notifications = firestore.collection("$BASE_PATH/$clientDocName/notifications")
                .whereEqualTo("isRead", false)
                .get()
                .await()

            notifications.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()
        }
    }

    suspend fun updateFcmToken(userDocName: String, newToken: String): Result<Unit> = runCatching {
        val user = userRepository.getCurrentUser() ?:
        throw IllegalStateException("Usuario no encontrado")

        val collectionPath = when (user.role) {
            UserRole.ADMIN -> "hdd-monitor/accounts/admins"
            UserRole.USER -> "$BASE_PATH/${user.clientDocName}/users"
        }

        firestore.collection(collectionPath)
            .document(userDocName)
            .update("fcmToken", newToken)
            .await()

        Log.d(TAG, "Token FCM actualizado: $userDocName")
    }

    suspend fun createNotification(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        message: String
    ): Result<Unit> = runCatching {
        val notificationDocName = IdManager.generateNotificationDocumentName(clientDocName)
        val now = LocalDateTime.now(Constants.TimeZone.PERU_ZONE)

        val notificationData = hashMapOf(
            "panelDocName" to panelDocName,
            "relayName" to relayName,
            "message" to message,
            "date_time" to now.format(DateTimeFormatter.ofPattern(DATE_FORMAT)),
            "timestamp" to now.atZone(Constants.TimeZone.PERU_ZONE)
                .toInstant()
                .toEpochMilli(),
            "isRead" to false
        )

        firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .document(notificationDocName)
            .set(notificationData)
            .await()

        Log.d(TAG, "Notification created: $notificationDocName")
    }

    suspend fun deleteNotification(
        clientDocName: String,
        notificationDocName: String
    ): Result<Unit> = runCatching {
        firestore.document("$BASE_PATH/$clientDocName/notifications/$notificationDocName")
            .delete()
            .await()

        Log.d(TAG, "Notification deleted: $notificationDocName")
    }
}