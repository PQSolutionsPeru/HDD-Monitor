package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.presentation.managers.EventDialogManager
import com.pqsolutions.hdd_monitor.presentation.managers.EventFilterManager
import com.pqsolutions.hdd_monitor.presentation.managers.EventManager
import com.pqsolutions.hdd_monitor.presentation.managers.EventTypeManager
import com.pqsolutions.hdd_monitor.presentation.state.EventDialogEvent
import com.pqsolutions.hdd_monitor.presentation.state.EventFilter
import com.pqsolutions.hdd_monitor.presentation.state.EventSortOption
import com.pqsolutions.hdd_monitor.presentation.state.EventUIEvent
import com.pqsolutions.hdd_monitor.presentation.state.EventViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val panelRepository: PanelRepository,
    private val firestore: FirebaseFirestore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "EventViewModel"
        private const val USER_CACHE_DURATION = 30_000L // 30 segundos
        private const val REFRESH_INTERVAL = 60_000L    // 1 minuto
    }

    private val supervisor = SupervisorJob()
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _state = MutableStateFlow(EventViewState.initial())
    val state: StateFlow<EventViewState> = _state.asStateFlow()

    private val _uiEvent = Channel<EventUIEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private var currentFilter: EventFilter = EventFilter.All
    private var currentSort = EventSortOption.DEFAULT

    private var currentUser: UserData? = null
    private var lastUserFetch = 0L
    private var refreshJob: Job? = null
    private var loadEventsJob: Job? = null

    // Managers
    private val eventTypeManager = EventTypeManager(
        firestore = firestore,
        viewModelScope = viewModelScope,
        onError = { message -> sendSnackbar(message) },
        onMessage = { message -> sendSnackbar(message) }
    )

    private val eventManager = EventManager(
        eventRepository = eventRepository,
        panelRepository = panelRepository,
        viewModelScope = viewModelScope,
        onError = { message -> sendSnackbar(message) },
        onMessage = { message -> sendSnackbar(message) }
    )

    private val filterManager = EventFilterManager()

    private val dialogManager = EventDialogManager(
        panelRepository = panelRepository,
        eventManager = eventManager,
        viewModelScope = viewModelScope,
        onError = { message -> sendSnackbar(message) },
        _state = _state,
        loadEvents = { loadEvents(false) }
    )

    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch(supervisor) {
            try {
                loadInitialData()
                startPeriodicRefresh()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing ViewModel", e)
                sendSnackbar("Error iniciando la aplicación")
            }
        }
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(supervisor) {
            while (true) {
                delay(REFRESH_INTERVAL)
                refreshSilently()
            }
        }
    }

    private suspend fun loadInitialData() = withContext(ioDispatcher) {
        getCurrentUser()?.let { user ->
            loadEventTypes()
            if (user.role == UserRole.ADMIN) {
                loadUsers()
                loadClients()
            }
            loadEvents()
        }
    }

    private suspend fun getCurrentUser(): UserData? = withContext(ioDispatcher) {
        val currentTime = System.currentTimeMillis()
        if (currentUser != null && currentTime - lastUserFetch < USER_CACHE_DURATION) {
            currentUser
        } else {
            userRepository.getCurrentUser()?.also {
                currentUser = it
                lastUserFetch = currentTime
            }
        }
    }

    private suspend fun refreshSilently() {
        try {
            withContext(ioDispatcher) {
                getCurrentUser()?.let { user ->
                    loadEventTypes()
                    if (user.role == UserRole.ADMIN) {
                        loadUsers()
                        loadClients()
                    }
                    loadEvents(showLoading = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in silent refresh", e)
        }
    }

    private fun loadEventTypes() {
        viewModelScope.launch(supervisor + ioDispatcher) {
            try {
                val types = eventTypeManager.loadEventTypes()
                _state.value = _state.value.copy(eventTypes = types)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading events types", e)
            }
        }
    }

    private fun loadUsers() {
        viewModelScope.launch(supervisor + ioDispatcher) {
            try {
                userRepository.getAllUsers()
                    .onSuccess { usersList ->
                        _state.value = _state.value.copy(users = usersList)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Error loading users", error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadUsers", e)
            }
        }
    }

    fun loadEvents(showLoading: Boolean = true) {
        loadEventsJob?.cancel()
        loadEventsJob = viewModelScope.launch(supervisor + ioDispatcher) {
            try {
                _state.value = _state.value.copy(isLoading = true)

                val user = getCurrentUser() ?: run {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "No se encontró usuario actual"
                    )
                    return@launch
                }

                // Usar el Flow existente según el rol del usuario
                val eventsFlow = if (user.role == UserRole.ADMIN) {
                    eventRepository.getAllEventsFlow()
                } else {
                    eventRepository.getEventsFlow(user.clientDocName ?: "")
                }

                eventsFlow.collect { events ->
                    // Ordenar eventos por fecha descendente
                    val sortedEvents = events.sortedByDescending { event ->
                        event.date_time // Ya es string en tu BD
                    }

                    _state.value = _state.value.copy(
                        events = sortedEvents,
                        isLoading = false,
                        error = null,
                        lastUpdate = System.currentTimeMillis()
                    )
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    fun loadClients() {
        viewModelScope.launch(supervisor + ioDispatcher) {
            try {
                eventRepository.getClients()
                    .onSuccess { clients ->
                        _state.value = _state.value.copy(clients = clients)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Error loading clients", error)
                        sendSnackbar(error.message ?: "Error al cargar clientes")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading clients", e)
                sendSnackbar(e.message ?: "Error al cargar clientes")
            }
        }
    }

    private fun loadPanelsForClient(clientDocName: String) {
        viewModelScope.launch(supervisor + ioDispatcher) {
            try {
                _state.value = _state.value.copy(
                    selectedClientForPanels = clientDocName,
                    availablePanels = emptyList()
                )

                panelRepository.getPanels(clientDocName)
                    .distinctUntilChanged()
                    .flowOn(ioDispatcher)
                    .collect { panels ->
                        _state.value = _state.value.copy(availablePanels = panels)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading panels", e)
                sendSnackbar(e.message ?: "Error al cargar paneles")
            }
        }
    }

    fun showCreateDialog() {
        dialogManager.showCreateDialog()
    }

    fun showEditDialog(event: Event) {
        viewModelScope.launch(supervisor) {
            try {
                val user = getCurrentUser() ?: return@launch
                val isAdmin = user.role == UserRole.ADMIN

                if (event.isFinalizado && isAdmin) {
                    updateEventStatus(
                        event.clientDocName,
                        event.documentName,
                        EventStatus.STATUS_PROGRAMADO
                    )
                    sendSnackbar("Evento reabierto exitosamente")
                    loadEvents(showLoading = false)
                    return@launch
                }

                if (!event.isProgramado && !isAdmin) {
                    sendSnackbar("Solo se pueden editar eventos en estado PROGRAMADO")
                    return@launch
                }

                Log.d(TAG, "Opening edit dialog for events: ${event.toLogString()}")
                _state.value = _state.value.copy(
                    showDialog = true,
                    selectedEvent = event,
                    currentDate = event.dateTime?.toLocalDate(),
                    currentTime = event.dateTime?.toLocalTime(),
                    selectedClientForPanels = event.clientDocName,
                    selectedPanelDocName = event.panelDocName,
                    selectedPanelName = event.panelName,
                    selectedEventType = event.type,
                    newEventTitle = event.title,
                    newEventDescription = event.text
                )
                loadPanelsForClient(event.clientDocName)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing edit dialog", e)
                sendSnackbar("Error al abrir el diálogo de edición")
            }
        }
    }

    fun deleteEvent(clientDocName: String, eventDocName: String) {
        viewModelScope.launch(supervisor + ioDispatcher) {
            try {
                eventManager.deleteEvent(
                    clientDocName = clientDocName,
                    eventDocName = eventDocName,
                    onSuccess = { loadEvents(showLoading = false) }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting events", e)
                sendSnackbar("Error al eliminar el evento")
            }
        }
    }

    fun updateEventStatus(clientDocName: String, eventDocName: String, newStatus: String) {
        viewModelScope.launch(supervisor + ioDispatcher) {
            try {
                val user = getCurrentUser()
                if (user == null) {
                    sendSnackbar("Usuario no encontrado")
                    return@launch
                }

                val event = _state.value.events.find {
                    it.clientDocName == clientDocName && it.documentName == eventDocName
                }
                if (event == null) {
                    Log.w(TAG, "Evento no encontrado: $eventDocName")
                    sendSnackbar("Evento no encontrado")
                    return@launch
                }

                val isAdmin = user.role == UserRole.ADMIN
                if (!EventStatus.isValidTransition(event.status, newStatus, isAdmin)) {
                    Log.w(TAG, "Transición inválida: ${event.status} -> $newStatus (isAdmin: $isAdmin)")
                    sendSnackbar("Transición de estado no permitida")
                    return@launch
                }

                eventManager.updateEventStatus(
                    clientDocName = clientDocName,
                    eventDocName = eventDocName,
                    newStatus = newStatus,
                    acceptorDocName = user.documentName,
                    isAdmin = isAdmin,
                    onSuccess = {
                        loadEvents(showLoading = false)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error updating events status", e)
                sendSnackbar(e.message ?: "Error al actualizar estado")
            }
        }
    }

    fun setFilter(filter: EventFilter) {
        if (currentFilter != filter) {
            currentFilter = filter
            loadEvents(showLoading = false)
        }
    }

    fun setSortOption(sortOption: EventSortOption) {
        if (currentSort != sortOption) {
            currentSort = sortOption
            loadEvents(showLoading = false)
        }
    }

    fun filterByType(eventType: String) {
        val newFilter = EventFilter.ByType(eventType)
        if (currentFilter != newFilter) {
            currentFilter = newFilter
            loadEvents(showLoading = false)
        }
    }

    fun refresh() {
        viewModelScope.launch(supervisor) {
            _state.value = _state.value.copy(isRefreshing = true)
            try {
                getCurrentUser()?.let { user ->
                    loadEventTypes()
                    if (user.role == UserRole.ADMIN) {
                        loadUsers()
                        loadClients()
                    }
                    loadEvents(showLoading = false)
                }
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    fun onDialogEvent(event: EventDialogEvent) {
        viewModelScope.launch(supervisor) {
            try {
                val user = getCurrentUser() ?: return@launch

                when (event) {
                    is EventDialogEvent.CreateNewEventType -> {
                        eventTypeManager.createNewEventType(
                            newType = event.type,
                            currentUser = user,
                            onSuccess = { loadEventTypes() }
                        )
                    }
                    is EventDialogEvent.DeleteEventType -> {
                        eventTypeManager.deleteEventType(
                            eventType = event.type,
                            currentUser = user,
                            onSuccess = { loadEventTypes() }
                        )
                    }
                    else -> {
                        dialogManager.handleDialogEvent(event, user)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling dialog events: ${event::class.simpleName}", e)
                sendSnackbar(e.message ?: "Error procesando el evento")
            }
        }
    }

    fun loadEventsForAgenda() {
        loadEventsJob?.cancel()
        loadEventsJob = viewModelScope.launch(supervisor + ioDispatcher) {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)

                val user = getCurrentUser() ?: run {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "No se encontró usuario actual"
                    )
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    eventManager.loadEvents(
                        isAdmin = user.role == UserRole.ADMIN,
                        clientDocName = user.clientDocName,
                        onSuccess = { events ->
                            // Filtrar eventos para mostrar solo los relevantes para la agenda
                            val agendaEvents = events.filter { event ->
                                // Aquí puedes definir tus criterios de filtrado para la agenda
                                !event.isFinalizado && event.isProgramado
                            }

                            _state.value = _state.value.copy(
                                events = agendaEvents,
                                isLoading = false,
                                error = null,
                                isRefreshing = false,
                                lastUpdate = System.currentTimeMillis()
                            )
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    private fun sendSnackbar(message: String) {
        viewModelScope.launch(supervisor) {
            _uiEvent.send(EventUIEvent.ShowSnackbar(message))
        }
    }

    override fun onCleared() {
        supervisor.cancel()
        refreshJob?.cancel()
        loadEventsJob?.cancel()
        super.onCleared()
    }
}