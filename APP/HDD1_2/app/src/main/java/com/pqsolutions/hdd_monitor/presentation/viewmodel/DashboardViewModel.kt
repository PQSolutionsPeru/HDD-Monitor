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
import com.pqsolutions.hdd_monitor.domain.GetPanelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val panelRepository: PanelRepository,
    private val userRepository: UserRepository,
    private val getPanelsUseCase: GetPanelsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var panelsJob: Job? = null

    private val panelUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.pqsolutions.hdd_monitor.PANEL_UPDATE") {
                val panelId = intent.getStringExtra("panelId") ?: return
                val relayName = intent.getStringExtra("relayName") ?: return
                val relayStatus = intent.getStringExtra("relayStatus") ?: return
                updatePanelState(panelId, relayName, relayStatus)
            }
        }
    }

    init {
        Log.d(TAG, "ViewModel inicializado")
        registerPanelUpdateReceiver()
        loadPanels()
        startPeriodicRefresh()
    }

    private fun registerPanelUpdateReceiver() {
        context.registerReceiver(
            panelUpdateReceiver,
            IntentFilter("com.pqsolutions.hdd_monitor.PANEL_UPDATE")
        )
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(60000) // 1 minute
                loadPanels()
            }
        }
    }

    fun loadPanels() {
        Log.d(TAG, "Cargando paneles")
        panelsJob?.cancel()
        panelsJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val currentUser = userRepository.getCurrentUser()
                Log.d(TAG, "Usuario actual: $currentUser")
                if (currentUser != null) {
                    val clientId = if (currentUser.role == UserRole.ADMIN) null else currentUser.clientId
                    Log.d(TAG, "Obteniendo paneles para ${if (clientId == null) "todos los clientes" else "clientId: $clientId"}")
                    getPanelsUseCase(GetPanelsUseCase.Params(clientId))
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
                Log.d(TAG, "La carga de paneles fue cancelada")
            } catch (e: Exception) {
                handleUnexpectedError(e)
            }
        }
    }

    private fun handlePanelLoadError(error: Throwable) {
        Log.e(TAG, "Error al cargar paneles: ${error.message}", error)
        _uiState.update { it.copy(
            isLoading = false,
            error = error.message ?: "Ocurrió un error desconocido"
        ) }
    }

    private fun handlePanelsLoaded(panels: List<Panel>) {
        Log.d(TAG, "Paneles cargados: ${panels.size}")
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
        Log.e(TAG, "No se encontró usuario autenticado")
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
                "Panel ${panel.name} (Cliente: ${panel.clientId}): ${relay.name} estado ${relay.status}"
            }
        }
        Log.d(TAG, "Alertas encontradas: ${alerts.size}")
        _uiState.update { it.copy(alerts = alerts) }
    }

    private fun updatePanelState(panelId: String, relayName: String, relayStatus: String) {
        val currentPanels = _uiState.value.panels.toMutableList()
        val panelIndex = currentPanels.indexOfFirst { it.ID == panelId }
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
        Log.d(TAG, "Actualizando paneles")
        loadPanels()
    }

    fun selectPanel(panelId: String) {
        val selectedPanel = _uiState.value.panels.find { it.ID == panelId }
        _uiState.update { it.copy(selectedPanel = selectedPanel) }
    }

    fun deselectPanel() {
        _uiState.update { it.copy(selectedPanel = null) }
    }

    fun cancelCurrentJob() {
        Log.d(TAG, "Cancelando trabajo actual")
        panelsJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel limpiado")
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
    val selectedPanel: Panel? = null,
    val error: String? = null,
    val alerts: List<String> = emptyList()
)