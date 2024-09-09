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
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.userDataFlow.collect { userData ->
                when (userData?.role) {
                    UserRole.ADMIN -> {
                        alertRepository.getAlertsFlowForAllClients()
                            .catch { e ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = e.message ?: "Error al cargar las alertas"
                                )
                            }
                            .collect { alerts ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    alerts = alerts,
                                    error = null
                                )
                            }
                    }
                    UserRole.USER -> {
                        val clientId = userData.clientId
                        if (clientId.isNotEmpty()) {
                            alertRepository.getAlertsFlow(clientId)
                                .catch { e ->
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        error = e.message ?: "Error al cargar las alertas"
                                    )
                                }
                                .collect { alerts ->
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        alerts = alerts,
                                        error = null
                                    )
                                }
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                alerts = emptyList(),
                                error = "ID de cliente no válido"
                            )
                        }
                    }
                    null -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            alerts = emptyList(),
                            error = "No se encontraron datos de usuario"
                        )
                    }
                }
            }
        }
    }

    fun createAlert(alert: Alert, selectedClients: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.userDataFlow.firstOrNull()?.let { userData ->
                when (userData.role) {
                    UserRole.ADMIN -> {
                        val result = alertRepository.createAlert(selectedClients, alert)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message
                        )
                        loadAlerts()
                    }
                    UserRole.USER -> {
                        val clientId = userData.clientId
                        if (clientId.isNotEmpty()) {
                            val result = alertRepository.createAlert(listOf(clientId), alert)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = result.exceptionOrNull()?.message
                            )
                            loadAlerts()
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "ID de cliente no válido"
                            )
                        }
                    }
                }
            } ?: run {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No se encontraron datos de usuario"
                )
            }
        }
    }

    fun updateAlert(alert: Alert) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.userDataFlow.firstOrNull()?.let { userData ->
                when (userData.role) {
                    UserRole.ADMIN -> {
                        val result = alertRepository.updateAlert(alert.ID_CLIENT, alert)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message
                        )
                        loadAlerts()
                    }
                    UserRole.USER -> {
                        val clientId = userData.clientId
                        if (clientId.isNotEmpty()) {
                            val result = alertRepository.updateAlert(clientId, alert)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = result.exceptionOrNull()?.message
                            )
                            loadAlerts()
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "ID de cliente no válido"
                            )
                        }
                    }
                }
            } ?: run {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No se encontraron datos de usuario"
                )
            }
        }
    }

    fun deleteAlert(clientId: String, alertId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = alertRepository.deleteAlert(clientId, alertId)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = result.exceptionOrNull()?.message
            )
            loadAlerts()
        }
    }

    fun confirmAlert(clientId: String, alertId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = alertRepository.updateAlertStatus(clientId, alertId, "ACEPTADO")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = result.exceptionOrNull()?.message
            )
            loadAlerts()
        }
    }

    fun loadClients() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val clients = alertRepository.getClients()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    clients = clients,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error al cargar los clientes"
                )
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