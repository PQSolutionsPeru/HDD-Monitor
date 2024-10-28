package com.pqsolutions.hdd_monitor.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.presentation.components.BackButton
import com.pqsolutions.hdd_monitor.presentation.components.EventCard
import com.pqsolutions.hdd_monitor.presentation.components.EventDialog
import com.pqsolutions.hdd_monitor.presentation.components.LoadingContent
import com.pqsolutions.hdd_monitor.presentation.components.RefreshableContent
import com.pqsolutions.hdd_monitor.presentation.components.ScreenContent
import com.pqsolutions.hdd_monitor.presentation.components.ScreenHeader
import com.pqsolutions.hdd_monitor.presentation.state.EventDialogEvent
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.EventViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(
    viewModel: EventViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    isAdmin: Boolean,
    hasPendingNotifications: Boolean
) {
    val state by viewModel.state.collectAsState()
    val notificationState by notificationViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val showEmptyState by remember {
        derivedStateOf {
            state.events.isEmpty() && !state.isLoading && state.error == null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadEvents()
        if (isAdmin) {
            viewModel.loadClients()
        }
    }

    ScreenContent(
        header = {
            ScreenHeader(
                title = stringResource(R.string.events),
                hasPendingNotifications = hasPendingNotifications,
                notificationCount = notificationState.pendingCount,
                onNotificationClick = { /* Ya estamos en la pantalla de eventos */ }
            )
        },
        content = {
            RefreshableContent(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.loadEvents() },
                listState = listState
            ) {
                LoadingContent(
                    isLoading = state.isLoading,
                    isEmpty = showEmptyState,
                    error = state.error,
                    onRetry = { viewModel.loadEvents() },
                    emptyContent = {
                        EmptyEventsContent()
                    }
                ) {
                    EventsList(
                        events = state.events,
                        clients = state.clients,
                        isAdmin = isAdmin,
                        listState = listState,
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
                                "ACEPTADO"
                            )
                            notificationViewModel.refresh()
                        },
                        onContactAdmin = { event ->
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            launchWhatsApp(context, event)
                        }
                    )
                }
            }
        },
        footer = {
            EventScreenFooter(
                isAdmin = isAdmin,
                onCreateClick = {
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    viewModel.showCreateDialog()
                },
                onBackClick = onBackClick
            )
        }
    )

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
            isAdmin = isAdmin,
            onEvent = viewModel::onDialogEvent,
            onDismiss = { viewModel.onDialogEvent(EventDialogEvent.Dismiss) }
        )
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
private fun EventsList(
    events: List<Event>,
    clients: List<com.pqsolutions.hdd_monitor.data.Client>,
    isAdmin: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEditClick: (Event) -> Unit,
    onDeleteClick: (Event) -> Unit,
    onAcceptClick: (Event) -> Unit,
    onContactAdmin: (Event) -> Unit
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            items = events,
            // Crear una key única combinando clientDocName y documentName
            key = { event -> "${event.clientDocName}_${event.documentName}" }
        ) { event ->
            val client = clients.find { client ->
                client.documentName == event.clientDocName
            }
            EventCard(
                event = event,
                client = client,
                isAdmin = isAdmin,
                onEditClick = { onEditClick(event) },
                onDeleteClick = { onDeleteClick(event) },
                onAcceptClick = { onAcceptClick(event) },
                onContactAdmin = { onContactAdmin(event) }
            )
        }
    }
}

@Composable
private fun EventScreenFooter(
    isAdmin: Boolean,
    onCreateClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isAdmin) {
            Button(
                onClick = onCreateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(stringResource(R.string.create_new_event))
            }
        }
        BackButton(onBackClick = onBackClick)
    }
}

private fun launchWhatsApp(context: android.content.Context, event: Event) {
    val message = context.getString(R.string.whatsapp_event_message, event.title)
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://wa.me/+51993533004?text=$encodedMessage")
    }
    context.startActivity(intent)
}