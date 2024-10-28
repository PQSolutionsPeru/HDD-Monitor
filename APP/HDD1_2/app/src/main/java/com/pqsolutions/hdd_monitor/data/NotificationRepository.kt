package com.pqsolutions.hdd_monitor.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pqsolutions.hdd_monitor.data.util.IdManager
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.util.Constants
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
            .orderBy("date_time", Query.Direction.DESCENDING)
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
                                .find { it.startsWith(Constants.DocumentPrefixes.CLIENT) }

                            clientDocName?.let { clientDoc ->
                                doc.toObject(Notification::class.java)?.copy(
                                    documentName = doc.id,
                                    clientDocName = clientDoc
                                ).also { notification ->
                                    Log.d(TAG, "Loaded notification: ${notification?.toLogString()}")
                                }
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
            ),
            "isRead" to false
        )

        firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .document(notificationDocName)
            .set(notificationData)
            .await()

        Log.d(TAG, "Notification created: $notificationDocName")
    }

    // Marcar notificación como leída
    suspend fun markNotificationAsRead(
        clientDocName: String,
        notificationDocName: String
    ): Result<Unit> = runCatching {
        val notificationRef = firestore
            .document("$BASE_PATH/$clientDocName/notifications/$notificationDocName")

        val snapshot = notificationRef.get().await()
        if (!snapshot.exists()) {
            throw IllegalStateException("Notification not found: $notificationDocName")
        }

        notificationRef.update("isRead", true).await()
        Log.d(TAG, "Notification marked as read: $notificationDocName")
    }

    // Obtener flujo de notificaciones no leídas
    fun getUnreadNotificationsFlow(clientDocName: String): Flow<List<Notification>> = callbackFlow {
        val notificationsRef = firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .whereEqualTo("isRead", false)
            .orderBy("date_time", Query.Direction.DESCENDING)

        val listenerRegistration = notificationsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error getting unread notifications", error)
                close(error)
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                val notifications = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Notification::class.java)?.copy(
                            documentName = doc.id,
                            clientDocName = clientDocName
                        )
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

    // Obtener conteo de notificaciones no leídas
    fun getUnreadNotificationCount(clientDocName: String): Flow<Int> = callbackFlow {
        val notificationsRef = firestore.collection("$BASE_PATH/$clientDocName/notifications")
            .whereEqualTo("isRead", false)

        val listenerRegistration = notificationsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error getting unread notification count", error)
                close(error)
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                trySend(querySnapshot.size())
            }
        }

        awaitClose { listenerRegistration.remove() }
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