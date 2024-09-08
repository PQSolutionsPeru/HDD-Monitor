package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.data.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val panelRepository: PanelRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        Log.d("DashboardViewModel", "ViewModel initialized")
        loadPanels()
    }

    fun loadPanels() {
        Log.d("DashboardViewModel", "Loading panels")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val currentUser = userRepository.getCurrentUser()
                Log.d("DashboardViewModel", "Current user: $currentUser")
                if (currentUser != null) {
                    val clientId = if (currentUser.role == UserRole.ADMIN) null else currentUser.clientId
                    Log.d("DashboardViewModel", "Fetching panels for clientId: $clientId")
                    panelRepository.getPanelsFlow(clientId)
                        .catch { error ->
                            Log.e("DashboardViewModel", "Error loading panels: ${error.message}", error)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = error.message ?: "Unknown error occurred"
                            )
                        }
                        .collect { panels ->
                            Log.d("DashboardViewModel", "Panels loaded: ${panels.size}")
                            panels.forEach { panel ->
                                Log.d("DashboardViewModel", "Panel ${panel.name} (ID: ${panel.ID}, ClientId: ${panel.clientId}) relays: ${panel.relays}")
                            }
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                panels = panels,
                                error = null
                            )
                            checkForAlerts(panels)
                        }
                } else {
                    Log.e("DashboardViewModel", "No authenticated user found")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se encontró usuario autenticado"
                    )
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    private fun checkForAlerts(panels: List<Panel>) {
        val alerts = panels.flatMap { panel ->
            panel.relays.filter { it.status != "OK" }.map { relay ->
                "Panel ${panel.name}: ${relay.name} estado ${relay.status}"
            }
        }
        _uiState.value = _uiState.value.copy(alerts = alerts)
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val panels: List<Panel> = emptyList(),
    val error: String? = null,
    val alerts: List<String> = emptyList()
)