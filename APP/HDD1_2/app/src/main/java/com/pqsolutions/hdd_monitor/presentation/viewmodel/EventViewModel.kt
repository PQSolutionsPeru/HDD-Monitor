package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventWithMetadata
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.UserPreferences
import com.pqsolutions.hdd_monitor.data.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventUiState())
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    init {
        loadEvents()
        checkAdminStatus()
    }

    private fun checkAdminStatus() {
        viewModelScope.launch {
            userPreferences.userDataFlow
                .filterNotNull()
                .collect { userData ->
                    _isAdmin.value = userData.role == UserRole.ADMIN
                }
        }
    }

    fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.userDataFlow
                .filterNotNull()
                .flatMapLatest { userData ->
                    when {
                        userData.role == UserRole.ADMIN -> eventRepository.getAllEventsFlow()
                        userData.clientId.isNotEmpty() -> eventRepository.getEventsFlow(userData.clientId)
                        else -> flow { emit(emptyList()) }
                    }
                }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        events = emptyList(),
                        error = e.message ?: "Error loading events"
                    )
                }
                .collect { events ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        events = events,
                        error = if (events.isEmpty()) "No hay eventos registrados" else null
                    )
                }
        }
    }

    fun createEvent(event: Event, panelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.userDataFlow
                .filterNotNull()
                .first { it.clientId.isNotEmpty() }
                .let { userData ->
                    try {
                        eventRepository.createEvent(userData.clientId, panelId, event)
                        loadEvents() // Reload events after creating a new one
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Error creating event"
                        )
                    }
                }
        }
    }

    fun updateEvent(eventWithMetadata: EventWithMetadata) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                eventRepository.updateEvent(eventWithMetadata.clientId, eventWithMetadata.panelId, eventWithMetadata.eventId, eventWithMetadata.event)
                loadEvents() // Reload events after updating
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error updating event"
                )
            }
        }
    }

    fun deleteEvent(eventWithMetadata: EventWithMetadata) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                eventRepository.deleteEvent(eventWithMetadata.clientId, eventWithMetadata.panelId, eventWithMetadata.eventId)
                loadEvents() // Reload events after deleting
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error deleting event"
                )
            }
        }
    }
}

data class EventUiState(
    val isLoading: Boolean = false,
    val events: List<EventWithMetadata> = emptyList(),
    val error: String? = null
)