package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.Notification
import com.pqsolutions.hdd_monitor.data.NotificationRepository
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val panelRepository: PanelRepository,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val notificationRepository: NotificationRepository,
    private val firestore: FirebaseFirestore
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _pendingEvents = MutableStateFlow<List<Event>>(emptyList())
    val pendingEvents: StateFlow<List<Event>> = _pendingEvents.asStateFlow()

    private val _pendingNotifications = MutableStateFlow<List<Notification>>(emptyList())
    val pendingNotifications: StateFlow<List<Notification>> = _pendingNotifications.asStateFlow()

    private val currentUserFlow = MutableStateFlow<UserData?>(null)
    private var initialized = false
    private var initializationJob: Job? = null

    init {
        Log.d(TAG, "DashboardViewModel initialized")
        initializeData()
    }

    fun initializeData() {
        if (initialized) return

        initializationJob?.cancel()
        initializationJob = viewModelScope.launch(SupervisorJob() + Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val currentUser = userRepository.getCurrentUser() ?: run {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "No se pudo obtener el usuario actual"
                    )}
                    return@launch
                }

                Log.d(TAG, "Usuario actual: ${currentUser.email}, role: ${currentUser.role}")
                currentUserFlow.emit(currentUser)

                // Usar coroutineScope para manejar cancelaciones correctamente
                coroutineScope {
                    // Lanzar todas las operaciones en paralelo
                    val panelsJob = async { collectPanels(currentUser) }
                    val eventsJob = async { collectEvents(currentUser) }
                    val notificationsJob = async { collectNotifications(currentUser) }

                    // Esperar a que todas las operaciones terminen
                    awaitAll(panelsJob, eventsJob, notificationsJob)
                }

                initialized = true
            } catch (e: CancellationException) {
                Log.d(TAG, "Flujo cancelado normalmente")
            } catch (e: Exception) {
                Log.e(TAG, "Error en initializeData", e)
                handleError(e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun collectPanels(currentUser: UserData) {
        try {
            panelRepository.getPanelsForUser(currentUser)
                .distinctUntilChanged()
                .catch { e ->
                    Log.e(TAG, "Error procesando paneles", e)
                    emit(emptyList())
                }
                .collect { panels ->
                    val groupedPanels = when (currentUser.role) {
                        UserRole.ADMIN -> {
                            panels.groupBy { it.clientDocName }
                                .mapValues { (clientId, clientPanels) ->
                                    try {
                                        val clientDoc = firestore
                                            .document("hdd-monitor/accounts/clients/$clientId")
                                            .get()
                                            .await()
                                        val clientName = clientDoc.getString("name") ?: clientId
                                        Pair(clientName, clientPanels)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error obteniendo nombre del cliente $clientId", e)
                                        Pair(clientId, clientPanels)
                                    }
                                }
                        }
                        UserRole.USER -> {
                            mapOf(currentUser.clientDocName to Pair(
                                currentUser.clientName.ifBlank { currentUser.clientDocName },
                                panels
                            ))
                        }
                    }

                    Log.d(TAG, "Paneles agrupados recibidos: ${groupedPanels.size} clientes")
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            panels = panels,
                            groupedPanels = groupedPanels,
                            lastUpdate = System.currentTimeMillis()
                        )
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting panels", e)
            _uiState.update { it.copy(
                isLoading = false,
                error = e.message ?: "Error desconocido al cargar paneles"
            )}
        }
    }

    private suspend fun collectEvents(currentUser: UserData) {
        try {
            val eventsFlow = if (currentUser.role == UserRole.ADMIN) {
                eventRepository.getAllEventsFlow()
            } else {
                eventRepository.getEventsFlow(currentUser.clientDocName)
            }

            eventsFlow
                .distinctUntilChanged()
                .collect { events ->
                    _pendingEvents.value = events.filter { it.status == "PROGRAMADO" }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting events", e)
        }
    }

    private suspend fun collectNotifications(currentUser: UserData) {
        try {
            notificationRepository.getNotificationsFlow(
                isAdmin = currentUser.role == UserRole.ADMIN,
                clientDocName = currentUser.clientDocName
            )
                .distinctUntilChanged()
                .collect { notifications ->
                    _pendingNotifications.value = notifications.filter { it.isRecent() }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting notifications", e)
        }
    }

    fun refreshAll() {
        Log.d(TAG, "refreshAll() called")
        initialized = false
        initializationJob?.cancel()
        initializeData()
    }

    override fun onCleared() {
        super.onCleared()
        initializationJob?.cancel()
        initialized = false
    }

    override fun handleError(throwable: Throwable) {
        Log.e(TAG, "Error in DashboardViewModel", throwable)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = throwable.message ?: "Error desconocido"
                )
            }
        }
    }

    data class DashboardUiState(
        val isLoading: Boolean = true,
        val panels: List<Panel> = emptyList(),
        val groupedPanels: Map<String, Pair<String, List<Panel>>> = emptyMap(),
        val error: String? = null,
        val lastUpdate: Long = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "DashboardViewModel"
    }
}