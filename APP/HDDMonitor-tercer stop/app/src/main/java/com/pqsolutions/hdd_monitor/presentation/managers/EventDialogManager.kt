package com.pqsolutions.hdd_monitor.presentation.managers

import android.util.Log
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.presentation.state.EventDialogEvent
import com.pqsolutions.hdd_monitor.presentation.state.EventViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class EventDialogManager(
    private val panelRepository: PanelRepository,
    private val eventManager: EventManager,
    private val viewModelScope: CoroutineScope,
    private val onError: suspend (String) -> Unit,
    private val _state: MutableStateFlow<EventViewState>,
    private val loadEvents: () -> Unit
) {
    companion object {
        private const val TAG = "EventDialogManager"
    }

    fun showCreateDialog() {
        _state.value = _state.value.copy(
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

    fun showEditDialog(event: Event) {
        if (!event.isProgramado) {
            viewModelScope.launch {
                onError("Solo se pueden editar eventos en estado PROGRAMADO")
            }
            return
        }

        Log.d(TAG, "Opening edit dialog for events: ${event.toLogString()}")
        viewModelScope.launch {
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
        }
    }

    fun handleDialogEvent(event: EventDialogEvent, currentUser: UserData?) {
        when (event) {
            is EventDialogEvent.TitleChanged -> {
                handleTitleChange(event.title)
            }
            is EventDialogEvent.DescriptionChanged -> {
                handleDescriptionChange(event.description)
            }
            is EventDialogEvent.DateSelected -> {
                _state.value = _state.value.copy(currentDate = event.date)
            }
            is EventDialogEvent.TimeSelected -> {
                _state.value = _state.value.copy(currentTime = event.time)
            }
            is EventDialogEvent.EventTypeSelected -> {
                handleEventTypeSelection(event.eventType)
            }
            is EventDialogEvent.MultipleClientsSelected -> {
                handleClientSelection(event.clientDocNames)
            }
            is EventDialogEvent.PanelSelected -> {
                handlePanelSelection(event.panelDocName)
            }
            is EventDialogEvent.CreateNewEventType -> {
                // No deberíamos manejar esto aquí, debería delegarse al ViewModel
                throw NotImplementedError("CreateNewEventType debe manejarse en el ViewModel")
            }
            is EventDialogEvent.DeleteEventType -> {
                // No deberíamos manejar esto aquí, debería delegarse al ViewModel
                throw NotImplementedError("DeleteEventType debe manejarse en el ViewModel")
            }
            is EventDialogEvent.Confirm -> {
                handleConfirmDialog(currentUser)
            }
            is EventDialogEvent.Dismiss -> {
                clearDialogState()
            }
            else -> {
                Log.d(TAG, "Evento no manejado: $event")
            }
        }
    }

    private fun handleTitleChange(title: String) {
        _state.value = _state.value.let { currentState ->
            if (currentState.selectedEvent != null) {
                currentState.copy(
                    selectedEvent = currentState.selectedEvent.copy(title = title)
                )
            } else {
                currentState.copy(newEventTitle = title)
            }
        }
    }

    private fun handleDescriptionChange(description: String) {
        _state.value = _state.value.let { currentState ->
            if (currentState.selectedEvent != null) {
                currentState.copy(
                    selectedEvent = currentState.selectedEvent.copy(text = description)
                )
            } else {
                currentState.copy(newEventDescription = description)
            }
        }
    }

    private fun handleEventTypeSelection(eventType: String) {
        Log.d(TAG, "Tipo de evento seleccionado: $eventType")
        _state.value = _state.value.let { currentState ->
            if (currentState.selectedEvent != null) {
                currentState.copy(
                    selectedEvent = currentState.selectedEvent.copy(type = eventType),
                    selectedEventType = eventType
                )
            } else {
                currentState.copy(selectedEventType = eventType)
            }
        }
    }

    private fun handleClientSelection(clientDocNames: List<String>) {
        _state.value = _state.value.copy(selectedClients = clientDocNames)
        if (clientDocNames.size == 1) {
            loadPanelsForClient(clientDocNames.first())
        } else {
            _state.value = _state.value.copy(
                availablePanels = emptyList(),
                selectedClientForPanels = null,
                selectedPanelDocName = null,
                selectedPanelName = null
            )
        }
    }

    private fun handlePanelSelection(panelDocName: String?) {
        _state.value = _state.value.let { currentState ->
            val selectedPanel = currentState.availablePanels.find { panel ->
                panel.documentName == panelDocName
            }
            if (currentState.selectedEvent != null) {
                currentState.copy(
                    selectedEvent = currentState.selectedEvent.copy(
                        panelDocName = panelDocName,
                        panelName = selectedPanel?.name
                    ),
                    selectedPanelDocName = panelDocName,
                    selectedPanelName = selectedPanel?.name
                )
            } else {
                currentState.copy(
                    selectedPanelDocName = panelDocName,
                    selectedPanelName = selectedPanel?.name
                )
            }
        }
    }

    private fun loadPanelsForClient(clientDocName: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    selectedClientForPanels = clientDocName,
                    availablePanels = emptyList()
                )

                panelRepository
                    .getPanels(clientDocName)
                    .collect { panels ->
                        _state.value = _state.value.copy(availablePanels = panels)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading panels", e)
                onError(e.message ?: "Error al cargar paneles")
            }
        }
    }

    fun createOrUpdateEvent(event: Event) {
        viewModelScope.launch {
            try {
                if (event.documentName.isEmpty()) {
                    // Nuevo evento
                    eventManager.createEvent(
                        event = event,
                        selectedClients = _state.value.selectedClients,
                        onSuccess = {
                            _state.value = _state.value.copy(showDialog = false)
                            loadEvents()
                        }
                    )
                } else {
                    // Actualizar evento existente
                    eventManager.updateEvent(
                        event = event,
                        onSuccess = {
                            _state.value = _state.value.copy(showDialog = false)
                            loadEvents()
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en createOrUpdateEvent", e)
                onError(e.message ?: "Error al procesar el evento")
            }
        }
    }

    private fun clearDialogState() {
        _state.value = _state.value.copy(
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

    private fun handleConfirmDialog(currentUser: UserData?) {
        Log.d(TAG, "Iniciando handleConfirmDialog")
        val currentState = _state.value
        if (currentState.hasDateTime) {
            viewModelScope.launch {
                try {
                    val dateTime = LocalDateTime.of(
                        currentState.currentDate!!,
                        currentState.currentTime!!
                    )

                    if (currentUser == null) {
                        throw IllegalStateException("No hay usuario autenticado")
                    }

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
                            onError("No se puede actualizar un evento ya aceptado")
                            return@launch
                        }

                        if (!updatedEvent.isValid()) {
                            Log.d(TAG, "Evento actualizado no es válido")
                            onError("Evento inválido. Verifique todos los campos")
                            return@launch
                        }

                        Log.d(TAG, "Actualizando evento con tipo: ${updatedEvent.type}")
                        createOrUpdateEvent(updatedEvent)
                    } else {
                        // Creación de nuevo evento
                        val clientDocName = currentState.selectedClients.firstOrNull() ?: run {
                            onError("Debe seleccionar un cliente")
                            return@launch
                        }

                        val eventType = currentState.selectedEventType ?: run {
                            onError("Debe seleccionar un tipo de evento")
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
                            onError("Evento inválido. Verifique todos los campos")
                            return@launch
                        }

                        Log.d(TAG, "Creando nuevo evento con tipo: ${newEvent.type}")
                        createOrUpdateEvent(newEvent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en handleConfirmDialog", e)
                    onError(e.message ?: "Error al procesar el evento")
                }
            }
        } else {
            viewModelScope.launch {
                onError("Fecha y hora son requeridas")
            }
        }
    }

    fun validateDialog(currentState: EventViewState): Boolean {
        val hasValidTitle = currentState.newEventTitle.isNotBlank() ||
                currentState.selectedEvent?.title?.isNotBlank() == true
        val hasValidDescription = currentState.newEventDescription.isNotBlank() ||
                currentState.selectedEvent?.text?.isNotBlank() == true
        val hasDateTime = currentState.currentDate != null && currentState.currentTime != null
        val hasEventType = currentState.selectedEventType != null
        val isEditing = currentState.selectedEvent != null
        val hasSelectedClient = currentState.selectedClients.isNotEmpty() || isEditing

        return hasValidTitle &&
                hasValidDescription &&
                hasDateTime &&
                hasEventType &&
                (isEditing || hasSelectedClient)
    }
}