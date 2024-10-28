package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.presentation.state.EventDialogEvent
import com.pqsolutions.hdd_monitor.presentation.state.EventFilter
import com.pqsolutions.hdd_monitor.presentation.state.EventOperation
import com.pqsolutions.hdd_monitor.presentation.state.EventSortOption
import com.pqsolutions.hdd_monitor.presentation.state.EventUIEvent
import com.pqsolutions.hdd_monitor.presentation.state.EventViewState
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import com.pqsolutions.hdd_monitor.util.toUserFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val panelRepository: PanelRepository
) : ViewModel() {

    companion object {
        private const val TAG = "EventViewModel"
    }

    private val _state = MutableStateFlow(EventViewState.initial())
    val state: StateFlow<EventViewState> = _state.asStateFlow()

    private val _uiEvent = Channel<EventUIEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private var currentFilter: EventFilter = EventFilter.All
    private var currentSort = EventSortOption.DEFAULT

    init {
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                val currentUser = userRepository.getCurrentUser()

                if (currentUser != null) {
                    val eventsFlow = if (currentUser.role == UserRole.ADMIN) {
                        eventRepository.getAllEventsFlow()
                    } else {
                        eventRepository.getEventsFlow(currentUser.clientDocName)
                    }

                    eventsFlow
                        .catch { e ->
                            Log.e(TAG, "Error loading events", e)
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error = e.message ?: "Error desconocido"
                                )
                            }
                        }
                        .collect { events ->
                            Log.d(TAG, "Loaded ${events.size} events")
                            val validEvents = events.filter { event ->
                                val isValid = event.documentName.startsWith(DocumentPrefixes.EVENT) &&
                                        event.clientDocName.startsWith(DocumentPrefixes.CLIENT) &&
                                        (event.panelDocName?.startsWith(DocumentPrefixes.PANEL) ?: true)
                                if (!isValid) {
                                    Log.w(TAG, "Invalid event document found: ${event.documentName}")
                                }
                                isValid
                            }
                            val filteredAndSortedEvents = applyFilterAndSort(validEvents)
                            _state.update {
                                it.copy(
                                    events = filteredAndSortedEvents,
                                    isLoading = false,
                                    error = null,
                                    isRefreshing = false,
                                    lastUpdate = System.currentTimeMillis()
                                )
                            }
                        }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "No se encontró usuario actual"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadEvents", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.toUserFriendlyMessage()
                    )
                }
            }
        }
    }

    private fun loadPanelsForClient(clientDocName: String) {
        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        selectedClientForPanels = clientDocName,
                        availablePanels = emptyList()
                    )
                }

                panelRepository
                    .getPanels(clientDocName)
                    .collect { panels ->
                        _state.update {
                            it.copy(availablePanels = panels)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading panels", e)
                _uiEvent.send(EventUIEvent.ShowSnackbar(e.toUserFriendlyMessage()))
            }
        }
    }

    fun showCreateDialog() {
        _state.update {
            it.copy(
                showDialog = true,
                selectedEvent = null,
                currentDate = null,
                currentTime = null,
                selectedClients = emptyList(),
                availablePanels = emptyList(),
                selectedClientForPanels = null,
                newEventTitle = "",
                newEventDescription = ""
            )
        }
    }

    fun showEditDialog(event: Event) {
        if (!event.isProgramado) {
            viewModelScope.launch {
                _uiEvent.send(EventUIEvent.ShowSnackbar("Solo se pueden editar eventos en estado PROGRAMADO"))
            }
            return
        }

        Log.d(TAG, "Opening edit dialog for event: ${event.toLogString()}")
        viewModelScope.launch {
            _state.update {
                it.copy(
                    showDialog = true,
                    selectedEvent = event,
                    currentDate = event.dateTime?.toLocalDate(),
                    currentTime = event.dateTime?.toLocalTime(),
                    selectedClientForPanels = event.clientDocName
                )
            }
            loadPanelsForClient(event.clientDocName)
        }
    }

    fun onDialogEvent(event: EventDialogEvent) {
        when (event) {
            is EventDialogEvent.TitleChanged -> {
                _state.update { currentState ->
                    if (currentState.selectedEvent != null) {
                        // Modo edición
                        currentState.copy(
                            selectedEvent = currentState.selectedEvent.copy(title = event.title)
                        )
                    } else {
                        // Modo creación
                        currentState.copy(newEventTitle = event.title)
                    }
                }
            }
            is EventDialogEvent.DescriptionChanged -> {
                _state.update { currentState ->
                    if (currentState.selectedEvent != null) {
                        // Modo edición
                        currentState.copy(
                            selectedEvent = currentState.selectedEvent.copy(text = event.description)
                        )
                    } else {
                        // Modo creación
                        currentState.copy(newEventDescription = event.description)
                    }
                }
            }
            is EventDialogEvent.DateSelected -> {
                _state.update {
                    it.copy(currentDate = event.date)
                }
            }
            is EventDialogEvent.TimeSelected -> {
                _state.update {
                    it.copy(currentTime = event.time)
                }
            }
            is EventDialogEvent.MultipleClientsSelected -> {
                _state.update {
                    it.copy(selectedClients = event.clientDocNames)
                }
                if (event.clientDocNames.size == 1) {
                    loadPanelsForClient(event.clientDocNames.first())
                } else {
                    _state.update {
                        it.copy(
                            availablePanels = emptyList(),
                            selectedClientForPanels = null
                        )
                    }
                }
            }
            is EventDialogEvent.PanelSelected -> {
                _state.update {
                    if (it.selectedEvent != null) {
                        it.copy(
                            selectedEvent = it.selectedEvent.copy(panelDocName = event.panelDocName)
                        )
                    } else {
                        it.copy(selectedPanelDocName = event.panelDocName)
                    }
                }
            }
            is EventDialogEvent.Confirm -> {
                Log.d(TAG, "Evento de confirmación recibido")
                handleConfirmDialog()
            }
            is EventDialogEvent.Dismiss -> clearDialogState()
            EventDialogEvent.ShowDatePicker,
            EventDialogEvent.ShowTimePicker -> {
                // Manejado por la UI
            }

            is EventDialogEvent.ClientSelected -> TODO()
        }
    }

    private fun handleConfirmDialog() {
        Log.d(TAG, "Iniciando handleConfirmDialog")
        val currentState = _state.value
        if (currentState.hasDateTime) {
            viewModelScope.launch {
                try {
                    val dateTime = LocalDateTime.of(
                        currentState.currentDate!!,
                        currentState.currentTime!!
                    )

                    if (currentState.selectedEvent != null) {
                        // Manejo de evento existente (edición)
                        val updatedEvent = currentState.selectedEvent.update(
                            title = currentState.selectedEvent.title,
                            text = currentState.selectedEvent.text,
                            dateTime = dateTime,
                            panelDocName = currentState.selectedEvent.panelDocName
                        )

                        if (updatedEvent == null) {
                            Log.d(TAG, "No se puede actualizar: evento no programado o nulo")
                            _uiEvent.send(EventUIEvent.ShowSnackbar("No se puede actualizar un evento ya aceptado"))
                            return@launch
                        }

                        if (!updatedEvent.isValid()) {
                            Log.d(TAG, "Evento actualizado no es válido")
                            _uiEvent.send(EventUIEvent.ShowSnackbar("Evento inválido. Verifique todos los campos"))
                            return@launch
                        }

                        updateEvent(updatedEvent)
                    } else {
                        // Creación de nuevo evento
                        val clientDocName = currentState.selectedClients.firstOrNull() ?: run {
                            _uiEvent.send(EventUIEvent.ShowSnackbar("Debe seleccionar un cliente"))
                            return@launch
                        }

                        val newEvent = Event.createNew(
                            clientDocName = clientDocName,
                            panelDocName = currentState.selectedPanelDocName,
                            panelName = currentState.selectedPanelName,
                            title = currentState.newEventTitle,
                            text = currentState.newEventDescription,
                            dateTime = dateTime
                        )

                        if (!newEvent.isValid()) {
                            _uiEvent.send(EventUIEvent.ShowSnackbar("Evento inválido. Verifique todos los campos"))
                            return@launch
                        }

                        createEvent(newEvent, currentState.selectedClients)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en handleConfirmDialog", e)
                    _uiEvent.send(EventUIEvent.ShowSnackbar(e.message ?: "Error al procesar el evento"))
                }
            }
        } else {
            viewModelScope.launch {
                _uiEvent.send(EventUIEvent.ShowSnackbar("Fecha y hora son requeridas"))
            }
        }
    }

    private fun clearDialogState() {
        _state.update {
            it.copy(
                showDialog = false,
                selectedEvent = null,
                currentDate = null,
                currentTime = null,
                selectedClients = emptyList(),
                availablePanels = emptyList(),
                selectedClientForPanels = null,
                newEventTitle = "",
                newEventDescription = "",
                selectedPanelDocName = null
            )
        }
    }

    private fun applyFilterAndSort(events: List<Event>): List<Event> {
        return events
            .filter { event ->
                when (currentFilter) {
                    is EventFilter.All -> true
                    is EventFilter.Programmed -> event.isProgramado
                    is EventFilter.Accepted -> event.isAceptado
                    is EventFilter.ByClient -> event.clientDocName == (currentFilter as EventFilter.ByClient).clientDocName
                }
            }
            .sortedWith { a, b ->
                when (currentSort.field) {
                    EventSortOption.SortField.DATE -> {
                        val dateComparison = (a.dateTime ?: LocalDateTime.MIN)
                            .compareTo(b.dateTime ?: LocalDateTime.MIN)
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            dateComparison * -1 else dateComparison
                    }
                    EventSortOption.SortField.STATUS -> {
                        val comparison = a.status.compareTo(b.status)
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            comparison * -1 else comparison
                    }
                    EventSortOption.SortField.TITLE -> {
                        val comparison = a.title.compareTo(b.title)
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            comparison * -1 else comparison
                    }
                }
            }
    }

    fun createEvent(event: Event, selectedClients: List<String>) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(currentOperation = EventOperation.Loading) }
                Log.d(TAG, "Creating event for clients: $selectedClients")

                if (!event.isValid()) {
                    throw IllegalArgumentException("Evento inválido")
                }

                // Validar nombres de documentos de clientes
                val validClients = selectedClients.all { it.startsWith(DocumentPrefixes.CLIENT) }
                if (!validClients) {
                    throw IllegalArgumentException("Nombres de documentos de clientes inválidos")
                }

                eventRepository.createEvent(selectedClients, event)
                    .onSuccess {
                        _state.update {
                            it.copy(
                                currentOperation = EventOperation.Success("Evento creado exitosamente"),
                                showDialog = false
                            )
                        }
                        _uiEvent.send(EventUIEvent.ShowSnackbar("Evento creado exitosamente"))
                        clearDialogState()
                        loadEvents()
                    }
                    .onFailure { error ->
                        handleError(error)
                    }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch {
            try {
                if (!event.isProgramado) {
                    throw IllegalStateException("Solo se pueden actualizar eventos en estado PROGRAMADO")
                }

                if (!event.isValid()) {
                    throw IllegalArgumentException("Evento inválido")
                }

                // Validar nombres de documentos
                if (!event.documentName.startsWith(DocumentPrefixes.EVENT) ||
                    !event.clientDocName.startsWith(DocumentPrefixes.CLIENT) ||
                    (event.panelDocName != null && !event.panelDocName.startsWith(DocumentPrefixes.PANEL))) {
                    throw IllegalArgumentException("Nombres de documentos inválidos")
                }

                _state.update { it.copy(currentOperation = EventOperation.Loading) }
                Log.d(TAG, "Updating event: ${event.toLogString()}")

                eventRepository.updateEvent(event.clientDocName, event)
                    .onSuccess {
                        _state.update {
                            it.copy(
                                currentOperation = EventOperation.Success("Evento actualizado exitosamente"),
                                showDialog = false
                            )
                        }
                        _uiEvent.send(EventUIEvent.ShowSnackbar("Evento actualizado exitosamente"))
                        clearDialogState()
                        loadEvents()
                    }
                    .onFailure { error ->
                        handleError(error)
                    }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun updateEventStatus(clientDocName: String, eventDocName: String, newStatus: String) {
        viewModelScope.launch {
            try {
                // Validar nombres de documentos
                if (!clientDocName.startsWith(DocumentPrefixes.CLIENT) ||
                    !eventDocName.startsWith(DocumentPrefixes.EVENT)) {
                    throw IllegalArgumentException("Nombres de documentos inválidos")
                }

                _state.update { it.copy(currentOperation = EventOperation.Loading) }
                val currentUser = userRepository.getCurrentUser()

                eventRepository.updateEventStatus(
                    clientDocName,
                    eventDocName,
                    newStatus,
                    currentUser?.documentName
                ).onSuccess {
                    _state.update {
                        it.copy(currentOperation = EventOperation.Success("Estado actualizado exitosamente"))
                    }
                    _uiEvent.send(EventUIEvent.ShowSnackbar("Estado actualizado exitosamente"))
                    loadEvents()
                }.onFailure { error ->
                    handleError(error)
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun deleteEvent(clientDocName: String, eventDocName: String) {
        viewModelScope.launch {
            try {
                // Validar nombres de documentos
                if (!clientDocName.startsWith(DocumentPrefixes.CLIENT) ||
                    !eventDocName.startsWith(DocumentPrefixes.EVENT)) {
                    throw IllegalArgumentException("Nombres de documentos inválidos")
                }

                _state.update { it.copy(currentOperation = EventOperation.Loading) }

                eventRepository.deleteEvent(clientDocName, eventDocName)
                    .onSuccess {
                        _state.update {
                            it.copy(
                                currentOperation = EventOperation.Success("Evento eliminado exitosamente")
                            )
                        }
                        _uiEvent.send(EventUIEvent.ShowSnackbar("Evento eliminado exitosamente"))
                        loadEvents()
                    }
                    .onFailure { error ->
                        handleError(error)
                    }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun setFilter(filter: EventFilter) {
        currentFilter = filter
        loadEvents()
    }

    fun setSortOption(sortOption: EventSortOption) {
        currentSort = sortOption
        loadEvents()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadEvents()
    }

    fun loadClients() {
        viewModelScope.launch {
            try {
                val clients = eventRepository.getClients()
                // Validar nombres de documentos de clientes
                val validClients = clients.filter { it.documentName.startsWith(DocumentPrefixes.CLIENT) }
                _state.update { it.copy(clients = validClients) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading clients", e)
                _uiEvent.send(EventUIEvent.ShowSnackbar(e.toUserFriendlyMessage()))
            }
        }
    }

    private fun handleError(error: Throwable) {
        Log.e(TAG, "Error in operation", error)
        val errorMessage = error.toUserFriendlyMessage()
        _state.update {
            it.copy(
                currentOperation = EventOperation.Error(errorMessage),
                showDialog = false
            )
        }
        viewModelScope.launch {
            _uiEvent.send(EventUIEvent.ShowSnackbar(errorMessage))
        }
    }
}