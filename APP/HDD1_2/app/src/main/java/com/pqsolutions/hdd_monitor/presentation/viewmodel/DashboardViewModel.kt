package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.data.UserRole
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
                _uiState.update { it.copy(isLoading = true, error = null) }
                val currentUser = userRepository.getCurrentUser()
                Log.d(TAG, "Current user: $currentUser")
                if (currentUser != null) {
                    val clientId = if (currentUser.role == UserRole.ADMIN) null else currentUser.clientId
                    Log.d(TAG, "Fetching panels for clientId: $clientId")
                    panelRepository.getPanels(clientId).collect { panels ->
                        Log.d(TAG, "Received ${panels.size} panels")
                        panels.forEach { panel ->
                            Log.d(TAG, "Panel: ${panel.name} (ID: ${panel.ID}, ClientId: ${panel.ID_CLIENT})")
                            Log.d(TAG, "Relays: ${panel.relays}")
                        }
                        updatePanels(panels)
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

    private fun updatePanels(panels: List<Panel>) {
        Log.d(TAG, "updatePanels called with ${panels.size} panels")
        _uiState.update { currentState ->
            Log.d(TAG, "Current state before update: $currentState")
            val groupedPanels = panels.groupBy { it.ID_CLIENT }
            Log.d(TAG, "Grouped panels: ${groupedPanels.keys}")

            val updatedPanels = panels.map { panel ->
                val newStatus = determineOverallPanelStatus(panel)
                Log.d(TAG, "Panel ${panel.name} new status: $newStatus")
                panel.copy(overallStatus = newStatus)
            }

            Log.d(TAG, "Updated panels: ${updatedPanels.map { it.name to it.overallStatus }}")

            val newState = currentState.copy(
                isLoading = false,
                panels = updatedPanels,
                groupedPanels = groupedPanels,
                error = null
            )
            Log.d(TAG, "New state: $newState")
            newState
        }
        logPanelState("After updatePanels")
    }

    private fun determineOverallPanelStatus(panel: Panel): String {
        Log.d(TAG, "Determining overall status for panel: ${panel.name}")
        val discRelays = panel.relays.filter { it.status == "DISC" }
        val status = when {
            discRelays.isNotEmpty() -> discRelays.joinToString(", ") { it.name }
            else -> "OK"
        }
        Log.d(TAG, "Overall status for panel ${panel.name}: $status")
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
        Log.d(TAG, "$context - Panels state:")
        _uiState.value.panels.forEach { panel ->
            Log.d(TAG, "Panel ${panel.name} (ID: ${panel.ID}, ClientId: ${panel.ID_CLIENT}) relays:")
            panel.relays.forEach { relay ->
                Log.d(TAG, "  Relay: ${relay.name}, Status: ${relay.status}, DateTime: ${relay.date_time}")
            }
        }
    }

    fun refreshPanels() {
        Log.d(TAG, "refreshPanels() called")
        loadPanels()
    }

    fun cancelCurrentJob() {
        Log.d(TAG, "cancelCurrentJob() called")
        panelsJob?.cancel()
        panelsJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelCurrentJob()
        Log.d(TAG, "ViewModel cleared")
    }

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    data class DashboardUiState(
        val isLoading: Boolean = true,
        val panels: List<Panel> = emptyList(),
        val groupedPanels: Map<String, List<Panel>> = emptyMap(),
        val error: String? = null
    ) {
        override fun toString(): String {
            return "DashboardUiState(isLoading=$isLoading, panels=${panels.size}, groupedPanels=${groupedPanels.keys}, error=$error)"
        }
    }
}