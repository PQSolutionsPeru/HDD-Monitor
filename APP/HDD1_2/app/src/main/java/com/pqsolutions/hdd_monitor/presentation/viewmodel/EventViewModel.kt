package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val panelRepository: PanelRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    companion object {
        private const val TAG = "EventViewModel"
        private const val EVENT_TYPES_PATH = "hdd-monitor/event_types"
    }

    private val _state = MutableStateFlow(EventViewState.initial())
    val state: StateFlow<EventViewState> = _state.asStateFlow()

    private val _uiEvent = Channel<EventUIEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private var currentFilter: EventFilter = EventFilter.All
    private var currentSort = EventSortOption.DEFAULT

    init {
        loadEvents()
        loadEventTypes()
        loadUsers()
    }

    fun loadEvents() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                val currentUser = userRepository.getCurrentUser()

                if (currentUser != null) {
                    if (currentUser.role == UserRole.ADMIN) {
                        loadUsers()
                    }

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

                            validEvents.filter { it.panelDocName != null }.forEach { event ->
                                startPanelObservation(event)
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

    private fun loadEventTypes() {
        viewModelScope.launch {
            try {
                val documentSnapshot = firestore.document(EVENT_TYPES_PATH).get().await()
                val typesString = documentSnapshot.getString("types")
                val types = typesString?.split(",")?.map { it.trim() }?.sorted() ?: emptyList()
                _state.update { it.copy(eventTypes = types) }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading event types", e)
                _uiEvent.send(EventUIEvent.ShowSnackbar("Error al cargar tipos de eventos"))
            }
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            try {
                val usersResult = userRepository.getAllUsers()
                usersResult.onSuccess { usersList ->
                    _state.update { it.copy(users = usersList) }
                }.onFailure { error ->
                    Log.e(TAG, "Error loading users", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadUsers", e)
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
                newEventDescription = "",
                selectedPanelDocName = null,
                selectedEventType = null
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
                    selectedClientForPanels = event.clientDocName,
                    selectedPanelDocName = event.panelDocName,
                    selectedPanelName = event.panelName,
                    selectedEventType = event.type,
                    newEventTitle = event.title,
                    newEventDescription = event.text
                )
            }
            loadPanelsForClient(event.clientDocName)
        }
    }

    fun createNewEventType(newType: String) {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser?.role != UserRole.ADMIN) {
                    _uiEvent.send(EventUIEvent.ShowSnackbar("Solo los administradores pueden crear nuevos tipos de eventos"))
                    return@launch
                }

                _state.update { it.copy(isCreatingNewType = true, newTypeError = null) }

                val docRef = firestore.document(EVENT_TYPES_PATH)

                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val currentTypes = snapshot.getString("types")?.split(",")?.map { it.trim() } ?: emptyList()

                    if (currentTypes.contains(newType)) {
                        throw IllegalArgumentException("Este tipo de evento ya existe")
                    }

                    val updatedTypes = (currentTypes + newType).sorted().joinToString(",")
                    transaction.update(docRef, "types", updatedTypes)
                }.await()

                loadEventTypes()
                _uiEvent.send(EventUIEvent.ShowSnackbar("Nuevo tipo de evento creado: $newType"))

            } catch (e: Exception) {
                Log.e(TAG, "Error creating new event type", e)
                _state.update { it.copy(newTypeError = e.message) }
                _uiEvent.send(EventUIEvent.ShowSnackbar(e.message ?: "Error al crear nuevo tipo de evento"))
            } finally {
                _state.update { it.copy(isCreatingNewType = false) }
            }
        }
    }

    fun onDialogEvent(event: EventDialogEvent) {
        when (event) {
            is EventDialogEvent.TitleChanged -> {
                _state.update { currentState ->
                    if (currentState.selectedEvent != null) {
                        currentState.copy(
                            selectedEvent = currentState.selectedEvent.copy(title = event.title)
                        )
                    } else {
                        currentState.copy(newEventTitle = event.title)
                    }
                }
            }
            is EventDialogEvent.DescriptionChanged -> {
                _state.update { currentState ->
                    if (currentState.selectedEvent != null) {
                        currentState.copy(
                            selectedEvent = currentState.selectedEvent.copy(text = event.description)
                        )
                    } else {
                        currentState.copy(newEventDescription = event.description)
                    }
                }
            }
            is EventDialogEvent.DateSelected -> {
                _state.update { it.copy(currentDate = event.date) }
            }
            is EventDialogEvent.TimeSelected -> {
                _state.update { it.copy(currentTime = event.time) }
            }
            is EventDialogEvent.EventTypeSelected -> {
                Log.d(TAG, "Tipo de evento seleccionado: ${event.eventType}")
                _state.update { currentState ->
                    if (currentState.selectedEvent != null) {
                        currentState.copy(
                            selectedEvent = currentState.selectedEvent.copy(type = event.eventType),
                            selectedEventType = event.eventType
                        )
                    } else {
                        currentState.copy(selectedEventType = event.eventType)
                    }
                }
            }
            is EventDialogEvent.MultipleClientsSelected -> {
                _state.update { it.copy(selectedClients = event.clientDocNames) }
                if (event.clientDocNames.size == 1) {
                    loadPanelsForClient(event.clientDocNames.first())
                } else {
                    _state.update {
                        it.copy(
                            availablePanels = emptyList(),
                            selectedClientForPanels = null,
                            selectedPanelDocName = null,
                            selectedPanelName = null
                        )
                    }
                }
            }
            is EventDialogEvent.PanelSelected -> {
                _state.update {
                    val selectedPanel = it.availablePanels.find { panel ->
                        panel.documentName == event.panelDocName
                    }
                    if (it.selectedEvent != null) {
                        it.copy(
                            selectedEvent = it.selectedEvent.copy(
                                panelDocName = event.panelDocName,
                                panelName = selectedPanel?.name
                            ),
                            selectedPanelDocName = event.panelDocName,
                            selectedPanelName = selectedPanel?.name
                        )
                    } else {
                        it.copy(
                            selectedPanelDocName = event.panelDocName,
                            selectedPanelName = selectedPanel?.name
                        )
                    }
                }
            }
            is EventDialogEvent.CreateNewEventType -> {
                createNewEventType(event.type)
            }
            is EventDialogEvent.Confirm -> handleConfirmDialog()
            is EventDialogEvent.Dismiss -> clearDialogState()
            else -> {} // ShowDatePicker, ShowTimePicker, etc. manejados por la UI
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

                    val currentUser = userRepository.getCurrentUser()
                        ?: throw IllegalStateException("No hay usuario autenticado")

                    if (currentState.selectedEvent != null) {
                        // Manejo de evento existente (edición)
                        val updatedEvent = currentState.selectedEvent.update(
                            title = currentState.selectedEvent.title,
                            text = currentState.selectedEvent.text,
                            dateTime = dateTime,
                            panelDocName = currentState.selectedEvent.panelDocName,
                            panelName = currentState.selectedEvent.panelName,
                            type = currentState.selectedEventType
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

                        Log.d(TAG, "Actualizando evento con tipo: ${updatedEvent.type}")
                        updateEvent(updatedEvent)
                    } else {
                        // Creación de nuevo evento
                        val clientDocName = currentState.selectedClients.firstOrNull() ?: run {
                            _uiEvent.send(EventUIEvent.ShowSnackbar("Debe seleccionar un cliente"))
                            return@launch
                        }

                        val eventType = currentState.selectedEventType ?: run {
                            _uiEvent.send(EventUIEvent.ShowSnackbar("Debe seleccionar un tipo de evento"))
                            return@launch
                        }

                        val newEvent = Event.createNew(
                            clientDocName = clientDocName,
                            panelDocName = currentState.selectedPanelDocName,
                            panelName = currentState.selectedPanelName,
                            title = currentState.newEventTitle,
                            text = currentState.newEventDescription,
                            dateTime = dateTime,
                            type = eventType,
                            createdByUserId = currentUser.documentName,
                            createdByUserRole = currentUser.role.toString()
                        )

                        if (!newEvent.isValid()) {
                            _uiEvent.send(EventUIEvent.ShowSnackbar("Evento inválido. Verifique todos los campos"))
                            return@launch
                        }

                        Log.d(TAG, "Creando nuevo evento con tipo: ${newEvent.type}")
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

    private fun startPanelObservation(event: Event) {
        if (event.panelDocName != null && event.isProgramado) {
            viewModelScope.launch {
                try {
                    panelRepository.observePanelUpdates(event.clientDocName, event.panelDocName)
                        .collect { panel ->
                            if (panel != null) {
                                // El panel existe y se actualizó
                                if (panel.name != event.panelName) {
                                    // El nombre del panel ha cambiado, actualizar el evento
                                    updateEventPanelInfo(event.copy(panelName = panel.name))
                                }
                            } else {
                                // El panel ya no existe
                                handleDeletedPanel(event)
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error observing panel updates", e)
                }
            }
        }
    }

    private fun updateEventPanelInfo(event: Event) {
        viewModelScope.launch {
            try {
                eventRepository.updateEvent(event.clientDocName, event)
                    .onSuccess {
                        _uiEvent.send(EventUIEvent.ShowSnackbar("Se actualizó el nombre del panel en el evento"))
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

    private fun handleDeletedPanel(event: Event) {
        viewModelScope.launch {
            try {
                val updatedEvent = event.copy(panelDocName = null, panelName = null)
                eventRepository.updateEvent(event.clientDocName, updatedEvent)
                    .onSuccess {
                        _uiEvent.send(EventUIEvent.ShowSnackbar("Panel eliminado: se actualizó el evento"))
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

    fun createEvent(event: Event, selectedClients: List<String>) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(currentOperation = EventOperation.Loading) }

                // Verificar si el panel existe antes de crear el evento
                if (event.panelDocName != null) {
                    val panelExists = verifyPanelExists(event.clientDocName, event.panelDocName)
                    if (!panelExists) {
                        throw IllegalStateException("El panel seleccionado ya no existe")
                    }
                }

                if (!event.isValid()) {
                    throw IllegalArgumentException("Evento inválido")
                }

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
                        startPanelObservation(event)
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

                if (event.panelDocName != null) {
                    val panelExists = verifyPanelExists(event.clientDocName, event.panelDocName)
                    if (!panelExists) {
                        throw IllegalStateException("El panel seleccionado ya no existe")
                    }
                }

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
                        startPanelObservation(event)
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
                if (!clientDocName.startsWith(DocumentPrefixes.CLIENT) ||
                    !eventDocName.startsWith(DocumentPrefixes.EVENT)) {
                    throw IllegalArgumentException("Nombres de documentos inválidos")
                }

                _state.update { it.copy(currentOperation = EventOperation.Loading) }
                val currentUser = userRepository.getCurrentUser() ?:
                throw IllegalStateException("No hay usuario autenticado")

                val isAdmin = currentUser.role == UserRole.ADMIN

                eventRepository.updateEventStatus(
                    clientDocName = clientDocName,
                    eventDocName = eventDocName,
                    newStatus = newStatus,
                    updatedByUserId = currentUser.documentName,  // Cambio aquí: se usaba documentName en vez de userDocName
                    isAdmin = isAdmin  // Agregamos este parámetro que faltaba
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

    private suspend fun verifyPanelExists(clientDocName: String, panelDocName: String): Boolean {
        return panelRepository.verifyPanelExists(clientDocName, panelDocName)
    }

    fun setFilter(filter: EventFilter) {
        currentFilter = filter
        loadEvents()
    }

    fun setSortOption(sortOption: EventSortOption) {
        currentSort = sortOption
        loadEvents()
    }

    fun filterByType(eventType: String) {
        currentFilter = EventFilter.ByType(eventType)
        loadEvents()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadEvents()
        loadEventTypes()
        loadUsers()
    }

    fun loadClients() {
        viewModelScope.launch {
            try {
                val clients = eventRepository.getClients()
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

    fun clearListeners() {
        viewModelScope.launch {
            try {
                // Cancelar el Job actual si existe
                viewModelScope.coroutineContext.cancelChildren()

                // Limpiar el estado
                _state.update {
                    EventViewState.initial()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing listeners", e)
            }
        }
    }

    private fun clearDialogState() {
        _state.update {
            it.copy(
                showDialog = false,
                showCreateTypeDialog = false,
                selectedEvent = null,
                currentDate = null,
                currentTime = null,
                selectedClients = emptyList(),
                availablePanels = emptyList(),
                selectedClientForPanels = null,
                newEventTitle = "",
                newEventDescription = "",
                selectedPanelDocName = null,
                selectedEventType = null,
                newTypeError = null
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
                    is EventFilter.ByType -> event.type == (currentFilter as EventFilter.ByType).eventType
                    is EventFilter.Completed -> event.isFinalizado // Agregar este caso
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
                    EventSortOption.SortField.TYPE -> {
                        val comparison = (a.type ?: "").compareTo(b.type ?: "")
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            comparison * -1 else comparison
                    }
                    EventSortOption.SortField.LAST_UPDATE -> { // Agregar este caso
                        val comparison = a.lastUpdate.compareTo(b.lastUpdate)
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            comparison * -1 else comparison
                    }
                }
            }
    }

    override fun onCleared() {
        clearListeners()
        super.onCleared()
    }
}