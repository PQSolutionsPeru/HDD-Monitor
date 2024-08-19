package com.pqsolutions.hdd_monitor.presentation.viewmodel

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
        loadPanels()
    }

    private fun loadPanels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            getPanelsUseCase(GetPanelsUseCase.Params("client_id")) // Replace with actual client ID
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
                .collect { panels ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        panels = panels
                    )
                    checkForAlerts(panels)
                }
        }
    }

    private fun checkForAlerts(panels: List<Panel>) {
        val newAlerts = mutableListOf<String>()
        panels.forEach { panel ->
            panel.relays.forEach { relay ->
                if (relay.status != "OK") {
                    newAlerts.add("Alerta: ${relay.name} en ${panel.name} está en estado ${relay.status}")
                }
            }
        }
        _uiState.value = _uiState.value.copy(alerts = newAlerts)
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val panels: List<Panel> = emptyList(),
    val error: String? = null,
    val alerts: List<String> = emptyList()
)