package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.domain.GetPanelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getPanelsUseCase: GetPanelsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        Log.d("DashboardViewModel", "ViewModel initialized")
    }

    fun loadPanels() {
        Log.d("DashboardViewModel", "Loading panels")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            getPanelsUseCase(GetPanelsUseCase.Params("client_id")) // Replace with actual client ID
                .catch { error ->
                    Log.e("DashboardViewModel", "Error loading panels: ${error.message}", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
                .collect { panels ->
                    Log.d("DashboardViewModel", "Panels loaded: ${panels.size}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        panels = panels
                    )
                    checkForAlerts(panels)
                }
        }
    }

    private fun checkForAlerts(panels: List<Panel>) {
        Log.d("DashboardViewModel", "Checking for alerts")
        val newAlerts = mutableListOf<String>()
        panels.forEach { panel ->
            panel.relays.forEach { relay ->
                if (relay.status != "OK") {
                    val alertMessage = "Alerta: ${relay.name} en ${panel.name} está en estado ${relay.status}"
                    Log.d("DashboardViewModel", "New alert: $alertMessage")
                    newAlerts.add(alertMessage)
                }
            }
        }
        _uiState.value = _uiState.value.copy(alerts = newAlerts)
        Log.d("DashboardViewModel", "Total alerts: ${newAlerts.size}")
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val panels: List<Panel> = emptyList(),
    val error: String? = null,
    val alerts: List<String> = emptyList()
)