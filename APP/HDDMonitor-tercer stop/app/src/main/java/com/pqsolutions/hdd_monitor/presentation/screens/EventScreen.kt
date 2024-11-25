package com.pqsolutions.hdd_monitor.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import com.pqsolutions.hdd_monitor.presentation.components.EventCard
import com.pqsolutions.hdd_monitor.presentation.components.LoadingContent
import com.pqsolutions.hdd_monitor.presentation.components.NotificationBellWrapper
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.components.events.EventDialog
import com.pqsolutions.hdd_monitor.presentation.state.EventDialogEvent
import com.pqsolutions.hdd_monitor.presentation.state.EventUIEvent
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.EventViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun EventScreen(
    viewModel: EventViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
    navController: NavController,
    onBackClick: () -> Unit,
    isAdmin: Boolean,
    isAgendaView: Boolean = false,  // Añadir este parámetro
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (isAgendaView) {
            // Cargar eventos con filtro de agenda
            viewModel.loadEventsForAgenda()
        } else {
            viewModel.loadEvents()
        }
        if (isAdmin) {
            viewModel.loadClients()
        }
    }

    // Colectar los eventos de UI
    LaunchedEffect(true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is EventUIEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                else -> {}
            }
        }
    }

    HDD1_2Theme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.events),
                    onBackClick = onBackClick,
                    navController = navController,
                    actions = {
                        NotificationBellWrapper(
                            modifier = Modifier.size(48.dp),
                            navController = navController
                        )

                        if (isAdmin) {
                            IconButton(
                                onClick = {
                                    performHapticFeedback(context)
                                    playSoundEffect(context, R.raw.button_click)
                                    viewModel.showCreateDialog()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.create_new_event)
                                )
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LoadingContent(
                    isLoading = state.isLoading,
                    isEmpty = state.events.isEmpty() && !state.isLoading,
                    error = state.error,
                    onRetry = { viewModel.loadEvents() },
                    emptyContent = { EmptyEventsContent() }
                ) {
                    EventList(
                        events = state.events,
                        clients = state.clients,
                        users = state.users.associateBy { it.documentName },
                        isAdmin = isAdmin,
                        onEditClick = { event ->
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            viewModel.showEditDialog(event)
                        },
                        onDeleteClick = { event ->
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            viewModel.deleteEvent(event.clientDocName, event.documentName)
                        },
                        onAcceptClick = { event ->
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            viewModel.updateEventStatus(
                                event.clientDocName,
                                event.documentName,
                                EventStatus.STATUS_ACEPTADO
                            )
                            mainViewModel.notificationManager.refresh()
                        },
                        onFinalizeClick = { event ->
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            viewModel.updateEventStatus(
                                event.clientDocName,
                                event.documentName,
                                EventStatus.STATUS_FINALIZADO
                            )
                            mainViewModel.notificationManager.refresh()
                        },
                        onContactWhatsApp = { phone, name, event ->
                            launchWhatsApp(context, phone, name, event)
                        }
                    )
                }
            }

            if (state.showDialog) {
                EventDialog(
                    event = state.selectedEvent,
                    clients = state.clients,
                    availablePanels = state.availablePanels,
                    selectedDate = state.currentDate,
                    selectedTime = state.currentTime,
                    selectedClientForPanels = state.selectedClientForPanels,
                    selectedPanelDocName = state.selectedPanelDocName,
                    newEventTitle = state.newEventTitle,
                    newEventDescription = state.newEventDescription,
                    eventTypes = state.eventTypes,
                    selectedEventType = state.selectedEventType,
                    isAdmin = isAdmin,
                    onEvent = viewModel::onDialogEvent,
                    onDismiss = { viewModel.onDialogEvent(EventDialogEvent.Dismiss) }
                )
            }
        }
    }
}

@Composable
private fun EmptyEventsContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.no_events),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EventList(
    events: List<Event>,
    clients: List<Client>,
    users: Map<String, UserData>,
    isAdmin: Boolean,
    onEditClick: (Event) -> Unit,
    onDeleteClick: (Event) -> Unit,
    onAcceptClick: (Event) -> Unit,
    onFinalizeClick: (Event) -> Unit,
    onContactWhatsApp: (String, String, Event) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier
    ) {
        items(
            items = events,
            key = { event -> "${event.clientDocName}_${event.documentName}" }
        ) { event ->
            val client = clients.find { it.documentName == event.clientDocName }
            val user = users[event.createdByUserId]

            EventCard(
                event = event,
                client = client,
                user = user,
                isAdmin = isAdmin,
                onEditClick = { onEditClick(event) },
                onDeleteClick = { onDeleteClick(event) },
                onAcceptClick = { onAcceptClick(event) },
                onFinalizeClick = { onFinalizeClick(event) },
                onContactWhatsApp = onContactWhatsApp
            )
        }
    }
}

private fun launchWhatsApp(context: android.content.Context, phone: String, name: String, event: Event) {
    val message = context.getString(R.string.whatsapp_event_message, event.title)
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://wa.me/$phone?text=$encodedMessage")
    }
    context.startActivity(intent)
}