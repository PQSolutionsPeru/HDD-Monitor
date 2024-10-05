package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
        observeNotificationChanges()
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { currentUser ->
                alertRepository.getAlertsFlow(currentUser.clientId).collect { alerts ->
                    val notifications = alerts.map { it.toNotification() }
                    _uiState.update { currentState ->
                        currentState.copy(
                            notifications = notifications,
                            hasNewNotifications = notifications.any { !it.isRead }
                        )
                    }
                }
            }
        }
    }

    private fun observeNotificationChanges() {
        viewModelScope.launch {
            userPreferences.notificationsEnabledFlow.collect { enabled ->
                if (enabled) {
                    loadNotifications()
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { currentUser ->
                _uiState.value.notifications.forEach { notification ->
                    alertRepository.updateAlertStatus(currentUser.clientId, notification.id, "LEIDO")
                }
                _uiState.update { currentState ->
                    currentState.copy(
                        notifications = currentState.notifications.map { it.copy(isRead = true) },
                        hasNewNotifications = false
                    )
                }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { currentUser ->
                alertRepository.updateAlertStatus(currentUser.clientId, notificationId, "LEIDO")
                _uiState.update { currentState ->
                    val updatedNotifications = currentState.notifications.map {
                        if (it.id == notificationId) it.copy(isRead = true) else it
                    }
                    currentState.copy(
                        notifications = updatedNotifications,
                        hasNewNotifications = updatedNotifications.any { !it.isRead }
                    )
                }
            }
        }
    }
}

data class NotificationUiState(
    val notifications: List<Notification> = emptyList(),
    val hasNewNotifications: Boolean = false
)

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
    timestamp = this.status,
    isRead = this.status != "PROGRAMADO"
)