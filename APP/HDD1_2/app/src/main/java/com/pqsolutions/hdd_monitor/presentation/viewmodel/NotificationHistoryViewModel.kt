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
                            // Verificar que todas las notificaciones tengan nombres de documento válidos
                            val validNotifications = notifications.all { notification ->
                                notification.documentName.startsWith(DocumentPrefixes.NOTIFICATION) &&
                                        notification.documentName.contains(notification.clientDocName)
                            }

                            if (!validNotifications) {
                                Log.w(TAG, "Se encontraron notificaciones con nombres de documento inválidos")
                            }

                            // Ordenar las notificaciones por fecha, las más recientes primero
                            val sortedNotifications = notifications
                                .sortedByDescending { it.date_time }
                                .distinctBy { "${it.clientDocName}_${it.documentName}" }

                            _uiState.update {
                                it.copy(
                                    notifications = sortedNotifications,
                                    isLoading = false,
                                    error = null,
                                    lastUpdate = System.currentTimeMillis()
                                )
                            }
                            Log.d(TAG, "Notifications loaded: ${sortedNotifications.size}")
                            sortedNotifications.forEach { notification ->
                                Log.d(
                                    TAG, "Notification loaded - " +
                                            "Doc: ${notification.documentName}, " +
                                            "Client: ${notification.clientDocName}, " +
                                            "Panel: ${notification.panelDocName}"
                                )
                            }
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