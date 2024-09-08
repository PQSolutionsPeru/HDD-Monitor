package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.UserPreferences
import com.pqsolutions.hdd_monitor.data.UserRole
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

    fun createAlert(alert: Alert) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.userDataFlow.firstOrNull()?.let { userData ->
                when (userData.role) {
                    UserRole.ADMIN -> {
                        val result = alertRepository.createAlertForClient(alert.ID_CLIENT, alert)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message
                        )
                        loadAlerts()
                    }
                    UserRole.USER -> {
                        val clientId = userData.clientId
                        if (clientId.isNotEmpty()) {
                            val result = alertRepository.createAlert(clientId, alert)
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
                        val result = alertRepository.updateAlertForClient(alert.ID_CLIENT, alert)
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

    fun deleteAlert(alertId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userPreferences.userDataFlow.firstOrNull()?.let { userData ->
                when (userData.role) {
                    UserRole.ADMIN -> {
                        val alert = _uiState.value.alerts.find { it.ID == alertId }
                        if (alert != null) {
                            val result = alertRepository.deleteAlertForClient(alert.ID_CLIENT, alertId)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = result.exceptionOrNull()?.message
                            )
                            loadAlerts()
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Alerta no encontrada"
                            )
                        }
                    }
                    UserRole.USER -> {
                        val clientId = userData.clientId
                        if (clientId.isNotEmpty()) {
                            val result = alertRepository.deleteAlert(clientId, alertId)
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
}

data class AlertUiState(
    val isLoading: Boolean = false,
    val alerts: List<Alert> = emptyList(),
    val error: String? = null
)