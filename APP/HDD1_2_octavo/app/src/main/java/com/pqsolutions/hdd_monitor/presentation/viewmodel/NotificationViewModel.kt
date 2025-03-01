package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Notification
import com.pqsolutions.hdd_monitor.data.NotificationRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NotificationViewModel"

enum class NotificationType {
    RELAY,
    EVENT
}

sealed class NotificationStatus {
    object PROGRAMADO : NotificationStatus()
    object ACEPTADO : NotificationStatus()
    object FINALIZADO : NotificationStatus()

    override fun toString(): String = when (this) {
        is PROGRAMADO -> "PROGRAMADO"
        is ACEPTADO -> "ACEPTADO"
        is FINALIZADO -> "FINALIZADO"
    }

    companion object {
        fun fromString(status: String): NotificationStatus = when (status.uppercase()) {
            "PROGRAMADO" -> PROGRAMADO
            "ACEPTADO" -> ACEPTADO
            "FINALIZADO" -> FINALIZADO
            else -> PROGRAMADO
        }
    }
}

data class NotificationItem(
    val documentName: String = "",
    val clientDocName: String = "",
    val title: String = "",
    val text: String = "",
    val date_time: String = "",
    val status: NotificationStatus = NotificationStatus.PROGRAMADO,
    val panelDocName: String? = null,
    val panelName: String? = null,
    val relayName: String? = null,
    val eventId: String? = null,
    val eventType: String? = null,
    val notificationType: NotificationType,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val hasNewNotifications: Boolean = false,
    val pendingCount: Int = 0,
    val error: String? = null,
    val isLoading: Boolean = false,
    val lastUpdate: Long = System.currentTimeMillis()
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        initializeNotifications()
    }

    private fun initializeNotifications() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val currentUser = userRepository.getCurrentUser()

                if (currentUser == null) {
                    _uiState.update { it.copy(
                        error = "Usuario no encontrado",
                        isLoading = false
                    ) }
                    return@launch
                }

                Log.d(TAG, "Inicializando notificaciones para usuario: ${currentUser.role}")

                val notificationFlow = if (currentUser.role == UserRole.ADMIN) {
                    Log.d(TAG, "Usando flujo global de notificaciones para admin")
                    notificationRepository.getNotificationsFlow()
                } else {
                    Log.d(TAG, "Usando flujo de cliente: ${currentUser.clientDocName}")
                    notificationRepository.getNotificationsFlow(currentUser.clientDocName)
                }

                // Usar un único collect para evitar múltiples subscripciones
                try {
                    notificationFlow.collect { notifications ->
                        processNotifications(notifications)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting notifications", e)
                    _uiState.update { it.copy(
                        error = e.message,
                        isLoading = false
                    ) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing notifications", e)
                _uiState.update { it.copy(
                    error = e.message,
                    isLoading = false
                ) }
            }
        }
    }

    private fun processNotifications(notifications: List<Notification>) {
        val parsedNotifications = notifications.map { notification ->
            NotificationItem(
                documentName = notification.documentName,
                clientDocName = notification.clientDocName,
                title = notification.message,
                text = notification.message,
                date_time = notification.date_time,
                status = NotificationStatus.fromString(notification.status ?: ""),
                panelDocName = notification.panelDocName.takeIf { it.isNotEmpty() },
                panelName = notification.panelName,
                relayName = notification.relayName.takeIf { it.isNotEmpty() },
                eventId = notification.eventId,
                eventType = notification.eventType,
                notificationType = if (notification.isEventNotification())
                    NotificationType.EVENT
                else
                    NotificationType.RELAY,
                isRead = notification.isRead,
                timestamp = notification.timestamp
            )
        }

        val unreadCount = parsedNotifications.count { !it.isRead }

        _uiState.update { currentState ->
            currentState.copy(
                notifications = parsedNotifications,
                hasNewNotifications = unreadCount > 0,
                pendingCount = unreadCount,
                isLoading = false,
                lastUpdate = System.currentTimeMillis()
            )
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        initializeNotifications()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    notificationRepository.markAllNotificationsAsRead(
                        currentUser.clientDocName,
                        currentUser.role == UserRole.ADMIN
                    ).onSuccess {
                        _uiState.update { currentState ->
                            val updatedNotifications = currentState.notifications.map { notification ->
                                notification.copy(isRead = true)
                            }
                            currentState.copy(
                                notifications = updatedNotifications,
                                hasNewNotifications = false,
                                pendingCount = 0,
                                lastUpdate = System.currentTimeMillis()
                            )
                        }
                        Log.d(TAG, "All notifications marked as read for user: ${currentUser.documentName}")
                    }.onFailure { e ->
                        Log.e(TAG, "Error marking notifications as read", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking notifications as read", e)
            }
        }
    }

    fun onNotificationClick(
        notification: NotificationItem,
        onNavigateToEvent: (String) -> Unit,
        onNavigateToPanel: (String) -> Unit
    ) {
        when (notification.notificationType) {
            NotificationType.EVENT -> {
                notification.eventId?.let { eventId ->
                    onNavigateToEvent(eventId)
                }
            }
            NotificationType.RELAY -> {
                notification.panelDocName?.let { panelId ->
                    onNavigateToPanel(panelId)
                }
            }
        }

        // Marcar como leída
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    notificationRepository.markNotificationAsRead(
                        notification.clientDocName,
                        notification.documentName
                    ).onSuccess {
                        // Actualizar estado local
                        _uiState.update { currentState ->
                            val updatedNotifications = currentState.notifications.map {
                                if (it.documentName == notification.documentName) {
                                    it.copy(isRead = true)
                                } else it
                            }
                            val unreadCount = updatedNotifications.count { !it.isRead }
                            currentState.copy(
                                notifications = updatedNotifications,
                                hasNewNotifications = unreadCount > 0,
                                pendingCount = unreadCount
                            )
                        }
                    }.onFailure { e ->
                        Log.e(TAG, "Error marking notification as read", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking notification as read", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}