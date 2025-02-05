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
import com.pqsolutions.hdd_monitor.presentation.state.BleState
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
        Log.d(TAG, "DashboardViewModel initialized")
        loadPanels()
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
                        Log.d(TAG, "Received ${panels.size} panels for client: $clientDocName")

                        val validPanels = panels.filter { panel ->
                            val isValid = panel.documentName.startsWith(DocumentPrefixes.PANEL) &&
                                    panel.clientName.startsWith(DocumentPrefixes.CLIENT)
                            if (!isValid) {
                                Log.w(TAG, "Invalid panel document found: ${panel.documentName}")
                            }
                            isValid
                        }
                        Log.d(TAG, "Valid panels count: ${validPanels.size}")

                        val clientsMap = mutableMapOf<String, String>()
                        validPanels.map { it.clientName }.distinct().forEach { docName ->
                            clientRepository.getClient(docName).getOrNull()?.let { client ->
                                clientsMap[docName] = client.name
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