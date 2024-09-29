package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.data.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val panelRepository: PanelRepository,
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var panelsJob: Job? = null

    private val panelUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.pqsolutions.hdd_monitor.PANEL_UPDATE") {
                val panelName = intent.getStringExtra("panelName") ?: return
                val relayName = intent.getStringExtra("relayName") ?: return
                val relayStatus = intent.getStringExtra("relayStatus") ?: return
                updatePanelState(panelName, relayName, relayStatus)
            }
        }
    }

    init {
        Log.d(TAG, "ViewModel initialized")
        registerPanelUpdateReceiver()
        startPanelMonitoring()
    }

    private fun registerPanelUpdateReceiver() {
        context.registerReceiver(
            panelUpdateReceiver,
            IntentFilter("com.pqsolutions.hdd_monitor.PANEL_UPDATE")
        )
    }

    private fun startPanelMonitoring() {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { user ->
                val clientId = if (user.role == UserRole.ADMIN) null else user.clientId
                panelRepository.getPanelsFlow(clientId)
                    .catch { error ->
                        handlePanelLoadError(error)
                    }
                    .collect { panels ->
                        handlePanelsLoaded(panels)
                    }
            } ?: handleNoAuthenticatedUser()
        }
    }

    fun loadPanels() {
        Log.d(TAG, "Loading panels")
        panelsJob?.cancel()
        panelsJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val currentUser = userRepository.getCurrentUser()
                Log.d(TAG, "Current user: $currentUser")
                if (currentUser != null) {
                    val clientId = if (currentUser.role == UserRole.ADMIN) null else currentUser.clientId
                    Log.d(TAG, "Fetching panels for clientId: $clientId")
                    panelRepository.getPanelsFlow(clientId)
                        .catch { error ->
                            handlePanelLoadError(error)
                        }
                        .collect { panels ->
                            handlePanelsLoaded(panels)
                        }
                } else {
                    handleNoAuthenticatedUser()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Panel loading was cancelled")
                // No need to update UI state for cancellation
            } catch (e: Exception) {
                handleUnexpectedError(e)
            }
        }
    }

    private fun handlePanelLoadError(error: Throwable) {
        Log.e(TAG, "Error loading panels: ${error.message}", error)
        _uiState.update { it.copy(
            isLoading = false,
            error = error.message ?: "Unknown error occurred"
        ) }
    }

    private fun handlePanelsLoaded(panels: List<Panel>) {
        Log.d(TAG, "Panels loaded: ${panels.size}")
        panels.forEach { panel ->
            Log.d(TAG, "Panel ${panel.name} (ID: ${panel.ID}, ClientId: ${panel.clientId}) relays: ${panel.relays}")
        }
        _uiState.update { it.copy(
            isLoading = false,
            panels = panels,
            error = null
        ) }
        checkForAlerts(panels)
    }

    private fun handleNoAuthenticatedUser() {
        Log.e(TAG, "No authenticated user found")
        _uiState.update { it.copy(
            isLoading = false,
            error = "No se encontró usuario autenticado"
        ) }
    }

    private fun handleUnexpectedError(e: Exception) {
        Log.e(TAG, "Error: ${e.message}", e)
        _uiState.update { it.copy(
            isLoading = false,
            error = e.message ?: "Error desconocido"
        ) }
    }

    private fun checkForAlerts(panels: List<Panel>) {
        val alerts = panels.flatMap { panel ->
            panel.relays.filter { it.status != "OK" }.map { relay ->
                "Panel ${panel.name}: ${relay.name} estado ${relay.status}"
            }
        }
        Log.d(TAG, "Alerts found: ${alerts.size}")
        _uiState.update { it.copy(alerts = alerts) }
    }

    private fun updatePanelState(panelName: String, relayName: String, relayStatus: String) {
        val currentPanels = _uiState.value.panels.toMutableList()
        val panelIndex = currentPanels.indexOfFirst { it.name == panelName }
        if (panelIndex != -1) {
            val panel = currentPanels[panelIndex]
            val updatedRelays = panel.relays.map {
                if (it.name == relayName) it.copy(status = relayStatus) else it
            }
            currentPanels[panelIndex] = panel.copy(relays = updatedRelays)
            _uiState.update { it.copy(panels = currentPanels) }
            checkForAlerts(currentPanels)
        }
    }

    fun refreshPanels() {
        Log.d(TAG, "Refreshing panels")
        loadPanels()
    }

    fun cancelCurrentJob() {
        Log.d(TAG, "Cancelling current job")
        panelsJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
        panelsJob?.cancel()
        context.unregisterReceiver(panelUpdateReceiver)
    }

    companion object {
        private const val TAG = "DashboardViewModel"
    }
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val panels: List<Panel> = emptyList(),
    val error: String? = null,
    val alerts: List<String> = emptyList()
)