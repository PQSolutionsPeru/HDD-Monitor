package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
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

sealed class NotificationStatus {
    object PROGRAMADO : NotificationStatus()
    object ACEPTADO : NotificationStatus()

    override fun toString(): String = when (this) {
        is PROGRAMADO -> "PROGRAMADO"
        is ACEPTADO -> "ACEPTADO"
    }

    companion object {
        fun fromString(status: String): NotificationStatus = when (status.uppercase()) {
            "PROGRAMADO" -> PROGRAMADO
            "ACEPTADO" -> ACEPTADO
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
    val status: NotificationStatus,
    val panelDocName: String? = null
) {
    fun isValid(): Boolean {
        return documentName.startsWith(DocumentPrefixes.NOTIFICATION) &&
                clientDocName.startsWith(DocumentPrefixes.CLIENT) &&
                title.isNotBlank() &&
                text.isNotBlank() &&
                date_time.isNotBlank() &&
                (panelDocName?.startsWith(DocumentPrefixes.PANEL) ?: true)
    }
}

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
    private val eventRepository: EventRepository,
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
                if (currentUser != null) {
                    eventRepository.getEventsFlow(currentUser.clientDocName)
                        .catch { e ->
                            Log.e(TAG, "Error collecting events", e)
                            _uiState.update { it.copy(
                                error = e.message,
                                isLoading = false
                            ) }
                        }
                        .collect { events ->
                            val notifications = events
                                .map { it.toNotificationItem() }
                                .filter { it.isValid() }

                            val pendingCount = notifications.count {
                                it.status == NotificationStatus.PROGRAMADO
                            }
                            Log.d(TAG, "Received ${notifications.size} notifications, $pendingCount pending")
                            _uiState.update { currentState ->
                                currentState.copy(
                                    notifications = notifications,
                                    hasNewNotifications = pendingCount > 0,
                                    pendingCount = pendingCount,
                                    error = null,
                                    isLoading = false,
                                    lastUpdate = System.currentTimeMillis()
                                )
                            }
                        }
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Usuario no encontrado",
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing notifications", e)
                _uiState.update {
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
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
                    _uiState.update { currentState ->
                        currentState.copy(
                            hasNewNotifications = false,
                            pendingCount = 0
                        )
                    }
                    Log.d(TAG, "All notifications marked as read for user: ${currentUser.documentName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking notifications as read", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}

private fun Event.toNotificationItem() = NotificationItem(
    documentName = this.documentName,
    clientDocName = this.clientDocName,
    title = this.title,
    text = this.text,
    date_time = this.date_time,
    status = NotificationStatus.fromString(this.status),
    panelDocName = this.panelDocName
)