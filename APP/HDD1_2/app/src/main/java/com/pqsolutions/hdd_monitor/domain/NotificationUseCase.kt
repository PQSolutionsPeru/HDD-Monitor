package com.pqsolutions.hdd_monitor.domain

import com.pqsolutions.hdd_monitor.data.Notification
import com.pqsolutions.hdd_monitor.data.NotificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    fun getNotificationsFlow(clientDocName: String): Flow<List<Notification>> =
        notificationRepository.getNotificationsFlow(clientDocName)

    suspend fun markNotificationAsRead(
        clientDocName: String,
        notificationDocName: String
    ): Result<Unit> =
        notificationRepository.markNotificationAsRead(clientDocName, notificationDocName)

    fun getUnreadNotificationsFlow(clientDocName: String): Flow<List<Notification>> =
        notificationRepository.getUnreadNotificationsFlow(clientDocName)

    fun getUnreadNotificationCount(clientDocName: String): Flow<Int> =
        notificationRepository.getUnreadNotificationCount(clientDocName)
}