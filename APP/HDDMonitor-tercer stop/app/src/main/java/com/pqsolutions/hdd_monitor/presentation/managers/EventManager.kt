package com.pqsolutions.hdd_monitor.presentation.managers

import android.util.Log
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import com.pqsolutions.hdd_monitor.util.Constants.DocumentPrefixes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventManager(
    private val eventRepository: EventRepository,
    private val panelRepository: PanelRepository,
    private val viewModelScope: CoroutineScope,
    private val onError: suspend (String) -> Unit,
    private val onMessage: suspend (String) -> Unit
) {
    companion object {
        private const val TAG = "EventManager"
    }

    fun loadEvents(
        isAdmin: Boolean,
        clientDocName: String?,
        onSuccess: suspend (List<Event>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val eventsFlow = if (isAdmin) {
                    eventRepository.getAllEventsFlow()
                } else {
                    if (clientDocName == null) {
                        onError("No se encontró información del cliente")
                        return@launch
                    }
                    eventRepository.getEventsFlow(clientDocName)
                }

                eventsFlow.collect { events ->
                    val validEvents = events.filter { event ->
                        val documentNameValid = event.documentName.startsWith(DocumentPrefixes.EVENT)
                        val clientNameValid = event.clientDocName.startsWith(DocumentPrefixes.CLIENT)
                        val panelNameValid = event.panelDocName?.startsWith(DocumentPrefixes.PANEL) ?: true

                        val isValid = documentNameValid && clientNameValid && panelNameValid

                        if (!isValid) {
                            Log.w(TAG, """Invalid event document found:
            |Document: ${event.documentName}
            |Document prefix valid: $documentNameValid (expected: ${DocumentPrefixes.EVENT})
            |Client name valid: $clientNameValid
            |Panel name valid: $panelNameValid
            """.trimMargin())
                        }
                        isValid
                    }
                    onSuccess(validEvents)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading events", e)
                onError(e.message ?: "Error al cargar eventos")
            }
        }
    }

    fun createEvent(
        event: Event,
        selectedClients: List<String>,
        onSuccess: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (event.panelDocName != null) {
                        val panelExists = panelRepository.verifyPanelExists(event.clientDocName, event.panelDocName)
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
                            onSuccess()
                            onMessage("Evento creado exitosamente")
                        }
                        .onFailure { error ->
                            onError(error.message ?: "Error al crear el evento")
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating events", e)
                onError(e.message ?: "Error al crear el evento")
            }
        }
    }

    fun updateEvent(event: Event, onSuccess: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!event.isProgramado) {
                        throw IllegalStateException("Solo se pueden actualizar eventos en estado PROGRAMADO")
                    }

                    if (!event.isValid()) {
                        throw IllegalArgumentException("Evento inválido")
                    }

                    if (event.panelDocName != null) {
                        val panelExists = panelRepository.verifyPanelExists(event.clientDocName, event.panelDocName)
                        if (!panelExists) {
                            throw IllegalStateException("El panel seleccionado ya no existe")
                        }
                    }

                    if (!event.documentName.startsWith(DocumentPrefixes.EVENT) ||
                        !event.clientDocName.startsWith(DocumentPrefixes.CLIENT) ||
                        (event.panelDocName != null && !event.panelDocName.startsWith(DocumentPrefixes.PANEL))
                    ) {
                        throw IllegalArgumentException("Nombres de documentos inválidos")
                    }

                    eventRepository.updateEvent(event.clientDocName, event)
                        .onSuccess {
                            onSuccess()
                            onMessage("Evento actualizado exitosamente")
                        }
                        .onFailure { error ->
                            onError(error.message ?: "Error al actualizar el evento")
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating events", e)
                onError(e.message ?: "Error al actualizar el evento")
            }
        }
    }

    fun updateEventStatus(
        clientDocName: String,
        eventDocName: String,
        newStatus: String,
        acceptorDocName: String?,
        isAdmin: Boolean,
        onSuccess: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!clientDocName.startsWith(DocumentPrefixes.CLIENT) ||
                        !eventDocName.startsWith(DocumentPrefixes.EVENT)
                    ) {
                        throw IllegalArgumentException("Nombres de documentos inválidos")
                    }

                    eventRepository.updateEventStatus(
                        clientDocName = clientDocName,
                        eventDocName = eventDocName,
                        newStatus = newStatus,
                        acceptorDocName = acceptorDocName,
                        isAdmin = isAdmin
                    ).onSuccess {
                        onSuccess()
                        when (newStatus) {
                            EventStatus.STATUS_ACEPTADO -> onMessage("Evento aceptado exitosamente")
                            EventStatus.STATUS_FINALIZADO -> onMessage("Evento finalizado exitosamente")
                            else -> onMessage("Estado actualizado exitosamente")
                        }
                    }.onFailure { error ->
                        onError(error.message ?: "Error al actualizar el estado")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating events status", e)
                onError(e.message ?: "Error al actualizar el estado")
            }
        }
    }

    fun deleteEvent(
        clientDocName: String,
        eventDocName: String,
        onSuccess: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!clientDocName.startsWith(DocumentPrefixes.CLIENT) ||
                        !eventDocName.startsWith(DocumentPrefixes.EVENT)
                    ) {
                        throw IllegalArgumentException("Nombres de documentos inválidos")
                    }

                    eventRepository.deleteEvent(clientDocName, eventDocName)
                        .onSuccess {
                            onSuccess()
                            onMessage("Evento eliminado exitosamente")
                        }
                        .onFailure { error ->
                            onError(error.message ?: "Error al eliminar el evento")
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting events", e)
                onError(e.message ?: "Error al eliminar el evento")
            }
        }
    }

    fun startPanelObservation(event: Event) {
        if (event.panelDocName != null && event.isProgramado) {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        panelRepository.observePanelUpdates(event.clientDocName, event.panelDocName)
                            .collect { panel ->
                                if (panel != null) {
                                    if (panel.name != event.panelName) {
                                        handlePanelUpdate(event.copy(panelName = panel.name))
                                    }
                                } else {
                                    handleDeletedPanel(event)
                                }
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error observing panel updates", e)
                }
            }
        }
    }

    private suspend fun handlePanelUpdate(event: Event) {
        withContext(Dispatchers.IO) {
            try {
                eventRepository.updateEvent(event.clientDocName, event)
                    .onSuccess {
                        viewModelScope.launch {
                            onMessage("Se actualizó el nombre del panel en el evento")
                        }
                    }
                    .onFailure { error ->
                        viewModelScope.launch {
                            onError(error.message ?: "Error al actualizar el panel")
                        }
                    }
            } catch (e: Exception) {
                viewModelScope.launch {
                    onError(e.message ?: "Error al actualizar el panel")
                }
            }
        }
    }

    private suspend fun handleDeletedPanel(event: Event) {
        withContext(Dispatchers.IO) {
            try {
                val updatedEvent = event.copy(panelDocName = null, panelName = null)
                eventRepository.updateEvent(event.clientDocName, updatedEvent)
                    .onSuccess {
                        viewModelScope.launch {
                            onMessage("Panel eliminado: se actualizó el evento")
                        }
                    }
                    .onFailure { error ->
                        viewModelScope.launch {
                            onError(error.message ?: "Error al manejar panel eliminado")
                        }
                    }
            } catch (e: Exception) {
                viewModelScope.launch {
                    onError(e.message ?: "Error al manejar panel eliminado")
                }
            }
        }
    }
}