package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.UserPreferences
import com.pqsolutions.hdd_monitor.data.UserRole
import com.pqsolutions.hdd_monitor.data.Client
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
        loadClients()
    }

    fun loadAlerts() {
        viewModelScope.launch {
            userPreferences.userDataFlow
                .filterNotNull()
                .flatMapLatest { userData ->
                    when (userData.role) {
                        UserRole.ADMIN -> alertRepository.getAllAlertsFlow()
                        UserRole.USER -> {
                            if (userData.clientId.isNotEmpty()) {
                                alertRepository.getAlertsFlow(userData.clientId)
                            } else {
                                flow { emit(emptyList()) }
                            }
                        }
                        else -> flow { emit(emptyList()) }
                    }
                }
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Error al cargar las alertas"
                        )
                    }
                }
                .collect { alerts ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            alerts = alerts,
                            error = null
                        )
                    }
                }
        }
    }

    fun createAlert(alert: Alert, selectedClients: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userPreferences.userDataFlow.filterNotNull().first().let { userData ->
                try {
                    when (userData.role) {
                        UserRole.ADMIN -> alertRepository.createAlert(selectedClients, alert)
                        UserRole.USER -> {
                            if (userData.clientId.isNotEmpty()) {
                                alertRepository.createAlert(listOf(userData.clientId), alert)
                            } else {
                                throw Exception("ID de cliente no válido")
                            }
                        }
                        else -> throw Exception("Rol de usuario no válido")
                    }
                    loadAlerts()
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Error al crear la alerta"
                        )
                    }
                }
            }
        }
    }

    fun updateAlert(alert: Alert) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userPreferences.userDataFlow.filterNotNull().first().let { userData ->
                try {
                    when (userData.role) {
                        UserRole.ADMIN -> alertRepository.updateAlert(alert.ID_CLIENT, alert)
                        UserRole.USER -> {
                            if (userData.clientId.isNotEmpty()) {
                                alertRepository.updateAlert(userData.clientId, alert)
                            } else {
                                throw Exception("ID de cliente no válido")
                            }
                        }
                        else -> throw Exception("Rol de usuario no válido")
                    }
                    loadAlerts()
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Error al actualizar la alerta"
                        )
                    }
                }
            }
        }
    }

    fun deleteAlert(clientId: String, alertId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                alertRepository.deleteAlert(clientId, alertId)
                loadAlerts()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al eliminar la alerta"
                    )
                }
            }
        }
    }

    fun updateAlertStatus(clientId: String, alertId: String, newStatus: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                alertRepository.updateAlertStatus(clientId, alertId, newStatus)
                loadAlerts()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al actualizar el estado de la alerta"
                    )
                }
            }
        }
    }

    fun loadClients() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val clients = alertRepository.getClients()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        clients = clients,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al cargar los clientes"
                    )
                }
            }
        }
    }
}

data class AlertUiState(
    val isLoading: Boolean = false,
    val alerts: List<Alert> = emptyList(),
    val clients: List<Client> = emptyList(),
    val error: String? = null
)