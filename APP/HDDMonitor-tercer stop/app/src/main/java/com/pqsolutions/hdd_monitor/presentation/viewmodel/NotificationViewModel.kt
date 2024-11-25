package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.presentation.state.CombinedNotificationState
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val panelDocName: String? = null,
    val isRead: Boolean = false
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

    private val _combinedNotificationState = MutableStateFlow(CombinedNotificationState())
    val combinedNotificationState: StateFlow<CombinedNotificationState> = _combinedNotificationState.asStateFlow()

    private val mutex = Mutex()
    private val notificationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Error en NotificationViewModel", throwable)
            _uiState.update {
                it.copy(
                    error = throwable.message,
                    isLoading = false
                )
            }
        }
    )

    private var notificationJob: Job? = null

    init {
        initializeNotifications()
    }

    private fun initializeNotifications() {
        notificationJob?.cancel()
        notificationJob = notificationScope.launch {
            mutex.withLock {
                try {
                    _uiState.update { it.copy(isLoading = true) }

                    val currentUser = userRepository.getCurrentUser() ?: run {
                        _uiState.update {
                            it.copy(
                                error = "Usuario no encontrado",
                                isLoading = false
                            )
                        }
                        return@withLock
                    }

                    Log.d(TAG, "Initializing notifications for user: ${currentUser.documentName}")

                    combine(
                        eventRepository.getEventsFlow(currentUser.clientDocName)
                            .distinctUntilChanged()
                            .debounce(300),
                        eventRepository.getPanelStatusFlow(currentUser.clientDocName)
                            .distinctUntilChanged()
                            .debounce(300)
                    ) { events, panels ->
                        processNotificationUpdate(events, panels)
                    }
                        .flowOn(Dispatchers.Default)
                        .catch { e ->
                            Log.e(TAG, "Error processing notifications", e)
                            _uiState.update {
                                it.copy(
                                    error = e.message,
                                    isLoading = false
                                )
                            }
                        }
                        .collect { (notifications, combinedState) ->
                            mutex.withLock {
                                _uiState.value = _uiState.value.copy(
                                    notifications = notifications,
                                    hasNewNotifications = combinedState.hasUnread,
                                    pendingCount = combinedState.eventsCount,
                                    error = null,
                                    isLoading = false,
                                    lastUpdate = System.currentTimeMillis()
                                )
                                _combinedNotificationState.value = combinedState
                            }
                            Log.d(TAG, "Notification state updated: ${notifications.size} notifications")
                        }
                }
                catch (e: Exception) {
                    Log.e(TAG, "Error in initializeNotifications", e)
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun processNotificationUpdate(
        events: List<Event>,
        panels: List<Panel>
    ): Pair<List<NotificationItem>, CombinedNotificationState> {
        val notifications = events
            .asSequence()
            .map { it.toNotificationItem() }
            .filter { it.isValid() }
            .toList()

        val unreadEvents = events.count { event ->
            event.status == "PROGRAMADO" && !(event.isRead ?: false)
        }

        val panelAlerts = panels.count { panel ->
            panel.relays.any { it.status != "OK" }
        }

        return notifications to CombinedNotificationState(
            totalCount = unreadEvents + panelAlerts,
            hasUnread = unreadEvents > 0 || panelAlerts > 0,
            eventsCount = unreadEvents,
            panelAlertsCount = panelAlerts,
            lastUpdate = System.currentTimeMillis()
        )
    }

    fun refresh() {
        viewModelScope.launch {
            notificationJob?.cancel()
            initializeNotifications()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch(Dispatchers.IO + notificationScope.coroutineContext[CoroutineExceptionHandler]!!) {
            mutex.withLock {
                try {
                    val currentUser = userRepository.getCurrentUser() ?: return@withLock
                    val notification = _uiState.value.notifications.find { it.documentName == notificationId }

                    notification?.let {
                        eventRepository.updateEvent(
                            clientDocName = currentUser.clientDocName,
                            event = Event(
                                documentName = it.documentName,
                                clientDocName = it.clientDocName,
                                title = it.title,
                                text = it.text,
                                date_time = it.date_time,
                                status = it.status.toString(),
                                isRead = true
                            )
                        )
                        Log.d(TAG, "Notification marked as read: $notificationId")
                        refresh()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking notification as read", e)
                    throw e
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch(Dispatchers.IO + notificationScope.coroutineContext[CoroutineExceptionHandler]!!) {
            mutex.withLock {
                try {
                    val currentUser = userRepository.getCurrentUser() ?: return@withLock

                    val unreadNotifications = _uiState.value.notifications
                        .filter { !it.isRead }
                        .map { notification ->
                            async {
                                eventRepository.updateEvent(
                                    clientDocName = currentUser.clientDocName,
                                    event = Event(
                                        documentName = notification.documentName,
                                        clientDocName = notification.clientDocName,
                                        title = notification.title,
                                        text = notification.text,
                                        date_time = notification.date_time,
                                        status = notification.status.toString(),
                                        isRead = true
                                    )
                                )
                            }
                        }

                    unreadNotifications.awaitAll()
                    refresh()
                    Log.d(TAG, "All notifications marked as read")
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking all notifications as read", e)
                    throw e
                }
            }
        }
    }

    private fun Event.toNotificationItem() = NotificationItem(
        documentName = this.documentName,
        clientDocName = this.clientDocName,
        title = this.title,
        text = this.text,
        date_time = this.date_time,
        status = NotificationStatus.fromString(this.status),
        panelDocName = this.panelDocName,
        isRead = this.isRead ?: false
    )

    override fun onCleared() {
        super.onCleared()
        notificationJob?.cancel()
        notificationScope.cancel()
        Log.d(TAG, "ViewModel cleared")
    }
}