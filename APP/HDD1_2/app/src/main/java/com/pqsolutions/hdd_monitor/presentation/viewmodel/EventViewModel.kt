package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventUiState())
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    init {
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val clientId = userPreferences.getClientId() ?: ""
            if (clientId.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    events = emptyList(),
                    error = "No client ID found. Please log in again."
                )
            } else {
                eventRepository.getEventsFlow(clientId).collect { events ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        events = events,
                        error = null
                    )
                }
            }
        }
    }

    fun createEvent(event: Event) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val clientId = userPreferences.getClientId() ?: ""
            if (clientId.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No client ID found. Please log in again."
                )
            } else {
                val result = eventRepository.createEvent(clientId, event)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val clientId = userPreferences.getClientId() ?: ""
            if (clientId.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No client ID found. Please log in again."
                )
            } else {
                val result = eventRepository.updateEvent(clientId, event)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val clientId = userPreferences.getClientId() ?: ""
            if (clientId.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No client ID found. Please log in again."
                )
            } else {
                val result = eventRepository.deleteEvent(clientId, eventId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }
}

data class EventUiState(
    val isLoading: Boolean = false,
    val events: List<Event> = emptyList(),
    val error: String? = null
)