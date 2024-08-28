package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertUiState())
    val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()

    init {
        loadAlerts()
    }

    fun loadAlerts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.clientIdFlow.filterNotNull().flatMapLatest { clientId ->
                alertRepository.getAlertsFlow(clientId)
            }.catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error al cargar las alertas"
                )
            }.collect { alerts ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    alerts = alerts,
                    error = null
                )
            }
        }
    }

    fun createAlert(alert: Alert) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.clientIdFlow.filterNotNull().first().let { clientId ->
                val result = alertRepository.createAlert(clientId, alert)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun updateAlert(alert: Alert) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.clientIdFlow.filterNotNull().first().let { clientId ->
                val result = alertRepository.updateAlert(clientId, alert)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun deleteAlert(alertId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.clientIdFlow.filterNotNull().first().let { clientId ->
                val result = alertRepository.deleteAlert(clientId, alertId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }
}

data class AlertUiState(
    val isLoading: Boolean = false,
    val alerts: List<Alert> = emptyList(),
    val error: String? = null
)