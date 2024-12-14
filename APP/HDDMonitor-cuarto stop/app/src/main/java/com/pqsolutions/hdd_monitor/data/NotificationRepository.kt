package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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
    }

    // Flow principal de notificaciones para un cliente específico
    fun getNotificationsFlow(clientDocName: String): Flow<List<Notification>> = callbackFlow {
        val notificationsRef = firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .orderBy("date_time", Query.Direction.DESCENDING)

        val listenerRegistration = notificationsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error getting notifications", error)
                close(error)
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                val notifications = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Notification::class.java)?.copy(
                            documentName = doc.id,
                            clientDocName = clientDocName
                        ).also { notification ->
                            Log.d(TAG, "Loaded notification: ${notification?.toLogString()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting notification document", e)
                        null
                    }
                }
                trySend(notifications)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }

    // Flow para todas las notificaciones (para administradores)
    fun getNotificationsFlow(): Flow<List<Notification>> = callbackFlow {
        val listenerRegistration = firestore.collectionGroup("notifications")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting all notifications", error)
                    close(error)
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    val notifications = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            // Extraer clientDocName del path
                            val clientDocName = doc.reference.path
                                .split("/")
                                .find { segment -> segment.startsWith("client_") }

                            clientDocName?.let { clientDoc ->
                                // Convertir el documento a un mapa
                                val notificationMap = doc.data?.toMutableMap() ?: mutableMapOf()

                                // Asegurar que los IDs legacy se manejen correctamente
                                val documentName = when {
                                    doc.id.startsWith("notification_") -> doc.id // Legacy ID
                                    doc.id.startsWith(DocumentPrefixes.NOTIFICATION) -> doc.id // Nuevo formato
                                    else -> "${DocumentPrefixes.NOTIFICATION}${doc.id}" // Agregar prefijo si falta
                                }

                                // Agregar campos necesarios al mapa
                                notificationMap["documentName"] = documentName
                                notificationMap["clientDocName"] = clientDoc

                                // Manejar el panelDocName si existe
                                notificationMap["panelDocName"]?.let { panelId ->
                                    if (!panelId.toString().startsWith(DocumentPrefixes.PANEL)) {
                                        notificationMap["panelDocName"] = "${DocumentPrefixes.PANEL}$panelId"
                                    }
                                }

                                // Convertir el mapa a objeto Notification
                                Notification.fromMap(notificationMap).also { notification ->
                                    Log.d(TAG, "Loaded notification: ${notification.toLogString()}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting notification document", e)
                            null
                        }
                    }.sortedByDescending { it.getTimestamp() }

                    trySend(notifications)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    // Actualizar token FCM
    suspend fun updateFcmToken(userDocName: String, newToken: String): Result<Unit> = runCatching {
        val user = userRepository.getCurrentUser()
        if (user == null) {
            throw IllegalStateException("Usuario no encontrado")
        }

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

    // Crear nueva notificación
    suspend fun createNotification(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        message: String
    ): Result<Unit> = runCatching {
        val notificationDocName = IdManager.generateNotificationDocumentName(clientDocName)

        val notificationData = hashMapOf(
            "panelDocName" to panelDocName,
            "relayName" to relayName,
            "message" to message,
            "date_time" to java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            )
        )

        firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .document(notificationDocName)
            .set(notificationData)
            .await()

        Log.d(TAG, "Notification created: $notificationDocName")
    }

    // Eliminar notificación
    suspend fun deleteNotification(
        clientDocName: String,
        notificationDocName: String
    ): Result<Unit> = runCatching {
        firestore.document("$BASE_PATH/$clientDocName/notifications/$notificationDocName")
            .delete()
            .await()

        Log.d(TAG, "Notification deleted: $notificationDocName")
    }

    // Eliminar notificaciones antiguas
    suspend fun deleteOldNotifications(clientDocName: String, olderThanDays: Int = 30): Result<Unit> = runCatching {
        val cutoffDate = java.time.LocalDateTime.now().minusDays(olderThanDays.toLong())
        val cutoffDateString = cutoffDate.format(
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
        )

        val oldNotifications = firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .whereLessThan("date_time", cutoffDateString)
            .get()
            .await()

        val batch = firestore.batch()
        oldNotifications.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()

        Log.d(TAG, "Deleted ${oldNotifications.size()} old notifications")
    }
}