package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.data.ClientRepository
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.esp32.ESP32Device
import com.pqsolutions.hdd_monitor.presentation.state.BleState
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import com.pqsolutions.hdd_monitor.util.StatusUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val panelRepository: PanelRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var panelsJob: Job? = null
    private var refreshJob: Job? = null
    private val refreshIntervalMs = 10000L // 10 segundos entre actualizaciones

    init {
        Log.d(TAG, "DashboardViewModel initialized")
        loadPanels()
        listenForStatusUpdates() // Nueva función para escuchar actualizaciones
    }

    // Nueva función para escuchar actualizaciones de estado
    private fun listenForStatusUpdates() {
        viewModelScope.launch {
            StatusUpdateManager.statusUpdates.collect { update ->
                Log.d(TAG, "Recibida actualización de estado: ${update.panelDocName} -> ${update.newStatus}")

                if (update.isEsp32) {
                    // Actualizar estado ESP32
                    updatePanelESP32Status(update.panelDocName, update.newStatus)
                } else {
                    // Actualizar estado de relay
                    updatePanelRelayStatus(update.panelDocName, update.relayName, update.newStatus)
                }
            }
        }
    }

    // Nuevas funciones para actualizar estado de paneles y relays
    private fun updatePanelESP32Status(panelDocName: String, newStatus: String) {
        _uiState.update { currentState ->
            val updatedPanels = currentState.panels.map { panel ->
                if (panel.documentName == panelDocName) {
                    Log.d(TAG, "Actualizando estado ESP32 del panel ${panel.name} a $newStatus")
                    panel.copy(esp32Status = newStatus)
                } else {
                    panel
                }
            }

            val updatedGroupedPanels = updatedPanels.groupBy {
                currentState.clientNames[it.clientName] ?: it.clientName
            }

            currentState.copy(
                panels = updatedPanels,
                groupedPanels = updatedGroupedPanels,
                lastUpdate = System.currentTimeMillis()
            )
        }
    }

    private fun updatePanelRelayStatus(panelDocName: String, relayName: String, newStatus: String) {
        _uiState.update { currentState ->
            val updatedPanels = currentState.panels.map { panel ->
                if (panel.documentName == panelDocName) {
                    val updatedRelays = panel.relays.map { relay ->
                        if (relay.name == relayName) {
                            Log.d(TAG, "Actualizando relay ${relay.name} del panel ${panel.name} a $newStatus")
                            relay.copy(status = newStatus)
                        } else {
                            relay
                        }
                    }
                    panel.copy(relays = updatedRelays)
                } else {
                    panel
                }
            }

            val updatedGroupedPanels = updatedPanels.groupBy {
                currentState.clientNames[it.clientName] ?: it.clientName
            }

            currentState.copy(
                panels = updatedPanels,
                groupedPanels = updatedGroupedPanels,
                lastUpdate = System.currentTimeMillis()
            )
        }
    }

    fun loadPanels() {
        Log.d(TAG, "loadPanels() called")
        panelsJob?.cancel()
        panelsJob = viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                Log.d(TAG, "Current user: ${currentUser?.documentName}, Role: ${currentUser?.role}")

                if (currentUser != null) {
                    _uiState.update { it.copy(isLoading = true, error = null) }

                    val clientDocName = when (currentUser.role) {
                        UserRole.USER -> {
                            Log.d(TAG, "User role detected, using client: ${currentUser.clientDocName}")
                            currentUser.clientDocName
                        }
                        UserRole.ADMIN -> {
                            Log.d(TAG, "Admin role detected, fetching all panels")
                            null
                        }
                    }

                    panelRepository.getPanels(clientDocName).collect { panels ->
                        Log.d(TAG, "Received ${panels.size} panels")
                        val validPanels = panels.filter { panel ->
                            panel.documentName.startsWith(DocumentPrefixes.PANEL) &&
                                    panel.clientName.startsWith(DocumentPrefixes.CLIENT)
                        }

                        val clientsMap = mutableMapOf<String, String>()
                        validPanels.map { it.clientName }.distinct().forEach { docName ->
                            clientRepository.getClient(docName).getOrNull()?.let { client ->
                                clientsMap[docName] = client.name
                            }
                        }

                        _uiState.update { currentState ->
                            val uniquePanels = validPanels.distinctBy { it.documentName }
                            val groupedPanels = uniquePanels.groupBy { clientsMap[it.clientName] ?: it.clientName }

                            currentState.copy(
                                isLoading = false,
                                panels = uniquePanels,
                                groupedPanels = groupedPanels,
                                clientNames = clientsMap,
                                error = null,
                                lastUpdate = System.currentTimeMillis()
                            )
                        }
                    }
                } else {
                    Log.e(TAG, "No authenticated user found")
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "Error cargando paneles"
                    ) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading panels", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                ) }
            }
        }
    }

    fun startPeriodicRefresh() {
        Log.d(TAG, "Iniciando actualización periódica")
        stopPeriodicRefresh() // Cancelar trabajo anterior si existe

        refreshJob = viewModelScope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "Actualizando paneles periódicamente")
                    refreshPanels()
                    delay(refreshIntervalMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en actualización periódica", e)
                    delay(refreshIntervalMs * 2) // Si hay error, esperar el doble antes de reintentar
                }
            }
        }
    }

    fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        Log.d(TAG, "Actualización periódica detenida")
    }

    fun refreshPanels() {
        Log.d(TAG, "refreshPanels() called")
        panelsJob?.cancel()
        panelsJob = viewModelScope.launch {
            try {
                // Actualizar solo el estado de los ESP32 si ya tenemos paneles cargados
                // Esto es más eficiente que volver a cargar todo
                if (_uiState.value.panels.isNotEmpty()) {
                    Log.d(TAG, "Actualizando estado ESP32 de paneles existentes")
                    val currentPanels = _uiState.value.panels.toMutableList()

                    // Actualizar estado ESP32 para cada panel existente
                    currentPanels.indices.forEach { index ->
                        val panel = currentPanels[index]
                        if (panel.esp32_id.isNotEmpty()) {
                            try {
                                // Consultar el estado actual del ESP32 en Firestore
                                val esp32Doc = firestore
                                    .collection("hdd-monitor/esp32/registered")
                                    .document(panel.esp32_id)
                                    .get()
                                    .await()

                                if (esp32Doc.exists()) {
                                    // Obtener el estado actual
                                    val currentStatus = esp32Doc.getString("status") ?: ESP32Device.STATUS_OFFLINE

                                    // Verificar última actualización para determinar si está realmente OFFLINE
                                    val lastUpdate = esp32Doc.getTimestamp("lastUpdate")
                                    val currentTime = com.google.firebase.Timestamp.now()
                                    val fiveMinutesAgo = com.google.firebase.Timestamp(
                                        currentTime.seconds - (5 * 60), // 5 minutos en segundos
                                        currentTime.nanoseconds
                                    )

                                    // Actualizar el estado del ESP32 en el panel
                                    val oldStatus = panel.esp32Status
                                    val newStatus = if (lastUpdate == null || lastUpdate.compareTo(fiveMinutesAgo) < 0) {
                                        ESP32Device.STATUS_OFFLINE
                                    } else if (currentStatus == ESP32Device.STATUS_RUNNING ||
                                        currentStatus == ESP32Device.STATUS_ONLINE ||
                                        currentStatus == ESP32Device.STATUS_CONFIGURED) {
                                        ESP32Device.STATUS_ONLINE
                                    } else {
                                        currentStatus
                                    }

                                    // MODIFICADO: Siempre actualizar el estado, sin comparar con el anterior
                                    Log.d(TAG, "ESP32 ${panel.esp32_id} estado actualizado: ${panel.esp32Status} -> $newStatus")
                                    currentPanels[index] = panel.copy(esp32Status = newStatus)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error actualizando estado ESP32", e)
                            }
                        }
                    }

                    // Actualizar el estado UI con los paneles actualizados
                    val uniquePanels = currentPanels.distinctBy { it.documentName }
                    val groupedPanels = uniquePanels.groupBy {
                        _uiState.value.clientNames[it.clientName] ?: it.clientName
                    }

                    _uiState.update { currentState ->
                        currentState.copy(
                            panels = uniquePanels,
                            groupedPanels = groupedPanels,
                            lastUpdate = System.currentTimeMillis()
                        )
                    }
                } else {
                    // Si no hay paneles, cargar todo desde cero
                    loadPanels()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en refreshPanels", e)
            }
        }
    }

    private fun updatePanels(panels: List<Panel>, clientsMap: Map<String, String>) {
        Log.d(TAG, "Updating panels - Count: ${panels.size}")
        _uiState.update { currentState ->
            val uniquePanels = panels.distinctBy { it.documentName }
            val groupedPanels = uniquePanels.groupBy { clientsMap[it.clientName] ?: it.clientName }

            currentState.copy(
                isLoading = false,
                panels = uniquePanels,
                groupedPanels = groupedPanels,
                clientNames = clientsMap,
                error = null,
                lastUpdate = System.currentTimeMillis()
            )
        }
    }

    private fun determineOverallPanelStatus(panel: Panel): String {
        //Log.d(TAG, "Determining overall status for panel: ${panel.name}")
        val discRelays = panel.relays.filter { it.status == "DISC" }
        val status = when {
            discRelays.isNotEmpty() -> discRelays.joinToString(", ") { it.name }
            else -> "OK"
        }
        //Log.d(TAG, "Overall status for panel ${panel.name}: $status")
        return status
    }

    private fun handleNoAuthenticatedUser() {
        Log.e(TAG, "No authenticated user found")
        _uiState.update { it.copy(
            isLoading = false,
            error = "No se encontró usuario autenticado"
        ) }
    }

    private fun handleUnexpectedError(e: Exception) {
        Log.e(TAG, "Unexpected error: ${e.message}", e)
        _uiState.update { it.copy(
            isLoading = false,
            error = e.message ?: "Error desconocido"
        ) }
    }

    private fun logPanelState(context: String) {
        //Log.d(TAG, "$context - Panels state:")
        _uiState.value.panels.forEach { panel ->
            //Log.d(TAG, "Panel ${panel.name} " +
            "(DocName: ${panel.documentName}, " +
                    "ClientDoc: ${panel.clientName}) " +
                    "Relays: ${panel.relays.size}"
            panel.relays.forEach { relay ->
                //Log.d(TAG, "  Relay: ${relay.name}, " +
                "Status: ${relay.status}, " +
                        "DateTime: ${relay.date_time}"
            }
        }
    }

    fun cancelCurrentJob() {
        //Log.d(TAG, "cancelCurrentJob() called")
        panelsJob?.cancel()
        panelsJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelCurrentJob()
        stopPeriodicRefresh()
        Log.d(TAG, "ViewModel cleared")
    }

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    data class DashboardUiState(
        val isLoading: Boolean = true,
        val panels: List<Panel> = emptyList(),
        val groupedPanels: Map<String, List<Panel>> = emptyMap(),
        val clientNames: Map<String, String> = emptyMap(),
        val error: String? = null,
        val lastUpdate: Long = System.currentTimeMillis()
    ) {
        override fun toString(): String {
            return "DashboardUiState(" +
                    "isLoading=$isLoading, " +
                    "panels=${panels.size}, " +
                    "groupedPanels=${groupedPanels.keys}, " +
                    "clientNames=${clientNames.size}, " +
                    "error=$error, " +
                    "lastUpdate=$lastUpdate" +
                    ")"
        }
    }
}