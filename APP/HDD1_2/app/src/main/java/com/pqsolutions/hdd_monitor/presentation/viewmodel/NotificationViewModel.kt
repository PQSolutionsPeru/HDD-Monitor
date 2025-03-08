package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.NotificationRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.domain.NotificationUseCase
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NotificationViewModel"

// Definir el estado UI para notificaciones
data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasUnreadNotifications: Boolean = false,
    val pendingCount: Int = 0  // Propiedad añadida
)

enum class NotificationType {
    EVENT, RELAY
}

enum class NotificationStatus {
    PROGRAMADO, ACEPTADO, FINALIZADO;

    companion object {
        fun fromString(status: String): NotificationStatus {
            return when (status.uppercase()) {
                "PROGRAMADO" -> PROGRAMADO
                "ACEPTADO" -> ACEPTADO
                "FINALIZADO" -> FINALIZADO
                else -> PROGRAMADO
            }
        }
    }
}

data class NotificationItem(
    val documentName: String,
    val clientDocName: String,
    val title: String,
    val text: String,
    val date_time: String,
    val timestamp: Long,
    val status: NotificationStatus = NotificationStatus.PROGRAMADO,
    val notificationType: NotificationType,
    val panelDocName: String? = null,
    val panelName: String? = null,
    val relayName: String? = null,
    val eventId: String? = null,
    val eventType: String? = null,
    val isRead: Boolean = false
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationUseCase: NotificationUseCase,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // Realizar limpieza inicial
                val currentUser = userRepository.getCurrentUser()
                if (currentUser?.role == UserRole.USER) {
                    notificationUseCase.performInitialCleanup(currentUser.clientDocName)
                        .onSuccess { Log.d(TAG, "Limpieza inicial completada") }
                        .onFailure { e -> Log.e(TAG, "Error en limpieza inicial", e) }
                }
                // Cargar notificaciones
                loadNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Error en inicialización", e)
            }
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            // Primero actualizamos el estado a cargando
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    val clientDocName = currentUser.clientDocName

                    // Log para debug
                    Log.d(TAG, "Cargando notificaciones para cliente: $clientDocName")

                    val notificationsFlow = when (currentUser.role) {
                        UserRole.ADMIN -> notificationUseCase.getAllNotificationsFlow()
                        else -> notificationUseCase.getNotificationsFlow(clientDocName)
                    }

                    // Recolectar notificaciones
                    notificationsFlow.collect { notifications ->
                        // Log para debug
                        Log.d(TAG, "Recibidas ${notifications.size} notificaciones del flujo")

                        if (notifications.isEmpty()) {
                            Log.d(TAG, "La lista de notificaciones está vacía")
                            _uiState.value = _uiState.value.copy(
                                notifications = emptyList(),
                                isLoading = false,
                                error = null,
                                hasUnreadNotifications = false,
                                pendingCount = 0
                            )
                            return@collect
                        }

                        // Convertir y ordenar notificaciones
                        val notificationItems = notifications
                            .filter {
                                val isValid = it.isValid()
                                if (!isValid) {
                                    Log.w(TAG, "Notificación inválida: ${it.documentName}")
                                }
                                isValid
                            }
                            .map { notification ->
                                try {
                                    // Mapeo a NotificationItem
                                    NotificationItem(
                                        documentName = notification.documentName,
                                        clientDocName = notification.clientDocName,
                                        title = if (notification.isEventNotification())
                                            "Evento ${notification.eventType}"
                                        else "Actualización de Panel",
                                        text = notification.message,
                                        date_time = notification.date_time,
                                        timestamp = notification.timestamp,
                                        status = notification.status?.let {
                                            NotificationStatus.fromString(it)
                                        } ?: NotificationStatus.PROGRAMADO,
                                        notificationType = if (notification.isEventNotification())
                                            NotificationType.EVENT
                                        else NotificationType.RELAY,
                                        panelDocName = notification.panelDocName,
                                        panelName = notification.panelName,
                                        relayName = notification.relayName,
                                        eventId = notification.eventId,
                                        eventType = notification.eventType,
                                        isRead = notification.isRead
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error al mapear notificación: ${notification.documentName}", e)
                                    null
                                }
                            }
                            .filterNotNull()
                            // IMPORTANTE: Ordenar explícitamente por timestamp
                            .sortedByDescending { it.timestamp }

                        // Log para debug del resultado del mapeo
                        Log.d(TAG, "Después de mapeo y filtrado: ${notificationItems.size} notificaciones válidas")

                        // Contar notificaciones no leídas
                        val unreadCount = notificationItems.count { !it.isRead }

                        // Actualizar estado UI
                        _uiState.value = _uiState.value.copy(
                            notifications = notificationItems,
                            isLoading = false,
                            error = null,
                            hasUnreadNotifications = unreadCount > 0,
                            pendingCount = unreadCount
                        )

                        Log.d(TAG, "Estado actualizado: ${notificationItems.size} notificaciones, $unreadCount no leídas")
                    }
                } else {
                    Log.w(TAG, "No se encontró usuario actual")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Usuario no encontrado"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar notificaciones", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar notificaciones: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    // Ejecutar limpieza al refrescar
                    if (currentUser.role == UserRole.USER) {
                        notificationUseCase.keepOnlyLastN(currentUser.clientDocName)
                            .onFailure { e -> Log.e(TAG, "Error en limpieza", e) }
                    }
                    loadNotifications()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Usuario no encontrado"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al refrescar", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al refrescar: ${e.message}"
                )
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    val isAdmin = currentUser.role == UserRole.ADMIN
                    notificationUseCase.markAllNotificationsAsRead(
                        clientDocName = currentUser.clientDocName,
                        isAdmin = isAdmin
                    )
                    // Actualizar estado local
                    val updatedNotifications = _uiState.value.notifications.map {
                        it.copy(isRead = true)
                    }
                    _uiState.value = _uiState.value.copy(
                        notifications = updatedNotifications,
                        hasUnreadNotifications = false,
                        pendingCount = 0 // Reset contador a cero
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al marcar notificaciones como leídas", e)
            }
        }
    }

    fun onNotificationClick(
        notification: NotificationItem,
        onNavigateToEvent: (String) -> Unit,
        onNavigateToPanel: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Marcar como leída
                val result = notificationUseCase.markNotificationAsRead(
                    clientDocName = notification.clientDocName,
                    notificationDocName = notification.documentName
                )

                // Actualizar estado local
                if (result.isSuccess) {
                    val updatedNotifications = _uiState.value.notifications.map {
                        if (it.documentName == notification.documentName) {
                            it.copy(isRead = true)
                        } else {
                            it
                        }
                    }

                    // Recalcular pendingCount
                    val newPendingCount = updatedNotifications.count { !it.isRead }

                    _uiState.value = _uiState.value.copy(
                        notifications = updatedNotifications,
                        hasUnreadNotifications = newPendingCount > 0,
                        pendingCount = newPendingCount
                    )
                }

                // Navegar según tipo
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
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar clic en notificación", e)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}