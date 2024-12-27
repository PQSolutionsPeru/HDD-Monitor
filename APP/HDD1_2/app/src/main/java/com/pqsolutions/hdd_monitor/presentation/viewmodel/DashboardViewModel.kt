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
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    init {
        //Log.d(TAG, "DashboardViewModel initialized")
        loadPanels()
    }

    fun loadPanels() {
        //Log.d(TAG, "loadPanels() called")
        panelsJob?.cancel()
        panelsJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val currentUser = userRepository.getCurrentUser()
                //Log.d(TAG, "Current user: ${currentUser?.documentName}")

                if (currentUser != null) {
                    val clientDocName = if (currentUser.role == UserRole.ADMIN) null else currentUser.clientDocName
                    ////Log.d(TAG, "Fetching panels for client document: $clientDocName")

                    panelRepository.getPanels(clientDocName).collect { panels ->
                        //Log.d(TAG, "Received ${panels.size} panels")

                        // Validar los nombres de documentos
                        val validPanels = panels.filter { panel ->
                            val isValid = panel.documentName.startsWith(DocumentPrefixes.PANEL) &&
                                    panel.clientName.startsWith(DocumentPrefixes.CLIENT)
                            if (!isValid) {
                                Log.w(TAG, "Invalid panel document found: ${panel.documentName}")
                            }
                            isValid
                        }

                        validPanels.forEach { panel ->
                            //Log.d(TAG, "Panel: ${panel.name} " +
                                    "(DocName: ${panel.documentName}, " +
                                    "ClientDoc: ${panel.clientName})"
                            //Log.d(TAG, "Relays: ${panel.relays}")
                        }

                        // Obtener los nombres de los clientes
                        val clientsMap = mutableMapOf<String, String>()
                        validPanels.map { it.clientName }.distinct().forEach { clientDocName ->
                            clientRepository.getClient(clientDocName).getOrNull()?.let { client ->
                                clientsMap[clientDocName] = client.name
                            }
                        }

                        updatePanels(validPanels, clientsMap)
                    }
                } else {
                    Log.e(TAG, "No authenticated user found")
                    handleNoAuthenticatedUser()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading panels", e)
                handleUnexpectedError(e)
            }
        }
    }

    private fun updatePanels(panels: List<Panel>, clientsMap: Map<String, String>) {
        //Log.d(TAG, "updatePanels called with ${panels.size} panels")
        _uiState.update { currentState ->
            //Log.d(TAG, "Current state before update: $currentState")
            val groupedPanels = panels.groupBy { clientsMap[it.clientName] ?: it.clientName }
            //Log.d(TAG, "Grouped panels by client names: ${groupedPanels.keys}")

            // Ya no necesitamos mapear los paneles para actualizar overallStatus
            // porque es una propiedad calculada en la clase Panel
            //Log.d(TAG, "Updated panels: ${panels.map { it.name to it.overallStatus }}")

            val newState = currentState.copy(
                isLoading = false,
                panels = panels,  // Usamos los paneles directamente
                groupedPanels = groupedPanels,
                clientNames = clientsMap,
                error = null,
                lastUpdate = System.currentTimeMillis()
            )
            //Log.d(TAG, "New state: $newState")
            newState
        }
        logPanelState("After updatePanels")
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

    fun refreshPanels() {
        //Log.d(TAG, "refreshPanels() called")
        loadPanels()
    }

    fun cancelCurrentJob() {
        //Log.d(TAG, "cancelCurrentJob() called")
        panelsJob?.cancel()
        panelsJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelCurrentJob()
        //Log.d(TAG, "ViewModel cleared")
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