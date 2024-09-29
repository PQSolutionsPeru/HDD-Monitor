package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val _hasNewNotifications = MutableStateFlow(false)
    val hasNewNotifications: StateFlow<Boolean> = _hasNewNotifications

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    init {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                alertRepository.getAlertsFlow(currentUser.clientId).collect { alerts ->
                    _notifications.value = alerts.map { it.toNotification() }
                    _hasNewNotifications.value = _notifications.value.any { !it.isRead }
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                _notifications.value.forEach { notification ->
                    alertRepository.updateAlertStatus(currentUser.clientId, notification.id, "LEIDO")
                }
                _notifications.value = _notifications.value.map { it.copy(isRead = true) }
                _hasNewNotifications.value = false
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                alertRepository.updateAlertStatus(currentUser.clientId, notificationId, "LEIDO")
                _notifications.value = _notifications.value.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
                _hasNewNotifications.value = _notifications.value.any { !it.isRead }
            }
        }
    }
}

data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean
)

private fun Alert.toNotification() = Notification(
    id = this.ID,
    title = this.title,
    message = this.text,
    timestamp = this.status, // Usando status como timestamp ya que Alert no tiene un campo específico para fecha
    isRead = this.status != "PROGRAMADO"
)