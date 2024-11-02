package com.pqsolutions.hdd_monitor.domain

import com.pqsolutions.hdd_monitor.data.Notification
import com.pqsolutions.hdd_monitor.data.NotificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    // Obtener flujo de notificaciones para un cliente específico
    fun getNotificationsFlow(clientDocName: String): Flow<List<Notification>> =
        notificationRepository.getNotificationsFlow(clientDocName)

    // Obtener flujo de todas las notificaciones (para administradores)
    fun getAllNotificationsFlow(): Flow<List<Notification>> =
        notificationRepository.getNotificationsFlow()

    // Crear una nueva notificación
    suspend fun createNotification(
        clientDocName: String,
        panelDocName: String,
        relayName: String,
        message: String
    ): Result<Unit> =
        notificationRepository.createNotification(
            clientDocName = clientDocName,
            panelDocName = panelDocName,
            relayName = relayName,
            message = message
        )

    // Eliminar una notificación
    suspend fun deleteNotification(
        clientDocName: String,
        notificationDocName: String
    ): Result<Unit> =
        notificationRepository.deleteNotification(clientDocName, notificationDocName)

    // Eliminar notificaciones antiguas
    suspend fun deleteOldNotifications(
        clientDocName: String,
        olderThanDays: Int = 30
    ): Result<Unit> =
        notificationRepository.deleteOldNotifications(clientDocName, olderThanDays)
}