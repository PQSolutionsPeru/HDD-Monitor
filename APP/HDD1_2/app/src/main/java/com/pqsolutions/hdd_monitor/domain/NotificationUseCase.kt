package com.pqsolutions.hdd_monitor.domain

import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NotificationUseCase @Inject constructor(
    private val alertRepository: AlertRepository
) {
    fun getNotifications(clientId: String): Flow<List<Alert>> {
        return alertRepository.getAlertsFlow(clientId)
    }

    suspend fun handleNewNotification(alert: Alert) {
        // Aquí puedes agregar lógica adicional si es necesario
        alertRepository.createAlert(alert.ID_CLIENT, alert)
    }
}