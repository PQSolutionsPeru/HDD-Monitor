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

private const val TAG = "NotificationHistoryVM"

data class NotificationHistoryUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdate: Long = System.currentTimeMillis()
)

@HiltViewModel
class NotificationHistoryViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    data class NotificationHistoryUiState(
        val notifications: List<NotificationItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val lastUpdate: Long = System.currentTimeMillis()
    )

    private val _uiState = MutableStateFlow(NotificationHistoryUiState())
    val uiState: StateFlow<NotificationHistoryUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    val notificationsFlow = when (currentUser.role) {
                        UserRole.ADMIN -> notificationRepository.getNotificationsFlow()
                        else -> notificationRepository.getNotificationsFlow(currentUser.clientDocName)
                    }

                    notificationsFlow
                        .catch { e ->
                            Log.e(TAG, "Error loading notifications", e)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = e.message ?: "Error al cargar notificaciones"
                                )
                            }
                        }
                        .collect { notifications ->
                            // Convertir Notification a NotificationItem
                            val notificationItems = notifications.map { notification ->
                                NotificationItem(
                                    documentName = notification.documentName,
                                    clientDocName = notification.clientDocName,
                                    title = if (notification.isEventNotification())
                                        "Evento ${notification.eventType}"
                                    else
                                        "Actualización de Panel",
                                    text = notification.message,
                                    date_time = notification.date_time,
                                    status = notification.status?.let {
                                        NotificationStatus.fromString(it)
                                    } ?: NotificationStatus.PROGRAMADO,
                                    panelDocName = notification.panelDocName,
                                    panelName = notification.panelName,
                                    relayName = notification.relayName,
                                    eventId = notification.eventId,
                                    eventType = notification.eventType,
                                    notificationType = if (notification.isEventNotification())
                                        NotificationType.EVENT
                                    else NotificationType.RELAY,
                                    isRead = notification.isRead,
                                    timestamp = notification.timestamp
                                ).takeIf { item ->
                                    item.documentName.startsWith(DocumentPrefixes.NOTIFICATION) &&
                                            item.clientDocName.startsWith(DocumentPrefixes.CLIENT) &&
                                            item.text.isNotBlank() &&
                                            (item.panelDocName?.startsWith(DocumentPrefixes.PANEL) ?: true)
                                }
                            }.filterNotNull()
                                .sortedByDescending { it.timestamp }

                            _uiState.update {
                                it.copy(
                                    notifications = notificationItems,
                                    isLoading = false,
                                    error = null,
                                    lastUpdate = System.currentTimeMillis()
                                )
                            }

                            Log.d(TAG, "Notificaciones cargadas: ${notificationItems.size}")
                            Log.d(TAG, "Eventos: ${notificationItems.count { it.notificationType == NotificationType.EVENT }}")
                            Log.d(TAG, "Relay: ${notificationItems.count { it.notificationType == NotificationType.RELAY }}")
                        }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No se encontró usuario actual"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadNotifications", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error desconocido al cargar notificaciones"
                    )
                }
            }
        }
    }

    fun refreshNotifications() {
        Log.d(TAG, "Refreshing notifications")
        loadNotifications()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}