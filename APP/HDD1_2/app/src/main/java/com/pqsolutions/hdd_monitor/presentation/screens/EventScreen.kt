package com.pqsolutions.hdd_monitor.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.domain.model.EventStatus
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell
import com.pqsolutions.hdd_monitor.presentation.components.EventCard
import com.pqsolutions.hdd_monitor.presentation.components.EventDialog
import com.pqsolutions.hdd_monitor.presentation.components.LoadingContent
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.state.EventDialogEvent
import com.pqsolutions.hdd_monitor.presentation.state.EventFilter
import com.pqsolutions.hdd_monitor.presentation.state.EventSortOption
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.HandleKeyboardFocus
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
    HandleKeyboardFocus()
    val state by viewModel.state.collectAsState()
    val notificationState by notificationViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadEvents()
        if (isAdmin) {
            viewModel.loadClients()
        }
    }

    HDD1_2Theme {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.events),
                    onBackClick = onBackClick,
                    actions = {
                        // Menú de filtros
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filtrar eventos"
                            )
                        }

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todos") },
                                onClick = {
                                    viewModel.setFilter(EventFilter.All)
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_programmed)) },
                                onClick = {
                                    viewModel.setFilter(EventFilter.Programmed)
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.event_status_accepted)) },
                                onClick = {
                                    viewModel.setFilter(EventFilter.Accepted)
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_completed)) },
                                onClick = {
                                    viewModel.setFilter(EventFilter.Completed)
                                    showFilterMenu = false
                                }
                            )
                        }

                        // Menú de ordenamiento
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = "Ordenar eventos"
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            EventSortOption.SortField.values().forEach { field ->
                                DropdownMenuItem(
                                    text = { Text(field.toString()) },
                                    onClick = {
                                        viewModel.setSortOption(
                                            EventSortOption(
                                                field = field,
                                                direction = EventSortOption.SortDirection.DESC
                                            )
                                        )
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        AnimatedNotificationBell(
                            hasNewNotifications = hasPendingNotifications,
                            notificationCount = notificationState.pendingCount,
                            onClick = { /* Ya estamos en la pantalla de eventos */ }
                        )
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
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
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = state.events,
                            key = { event -> "${event.clientDocName}_${event.documentName}" }
                        ) { event ->
                            val client = state.clients.find { it.documentName == event.clientDocName }
                            val user = event.createdByUserId?.let { userId ->
                                state.users.find { it.documentName == userId }
                            }

                            EventCard(
                                event = event,
                                client = client,
                                user = user,
                                isAdmin = isAdmin,
                                onEditClick = {
                                    performHapticFeedback(context)
                                    playSoundEffect(context, R.raw.button_click)
                                    viewModel.showEditDialog(event)
                                },
                                onDeleteClick = {
                                    performHapticFeedback(context)
                                    playSoundEffect(context, R.raw.button_click)
                                    viewModel.deleteEvent(event.clientDocName, event.documentName)
                                },
                                onAcceptClick = {
                                    performHapticFeedback(context)
                                    playSoundEffect(context, R.raw.button_click)
                                    viewModel.updateEventStatus(
                                        event.clientDocName,
                                        event.documentName,
                                        EventStatus.STATUS_ACEPTADO
                                    )
                                },
                                onFinalizeClick = {
                                    performHapticFeedback(context)
                                    playSoundEffect(context, R.raw.button_click)
                                    viewModel.updateEventStatus(
                                        event.clientDocName,
                                        event.documentName,
                                        EventStatus.STATUS_FINALIZADO
                                    )
                                },
                                onContactWhatsApp = { phone, name, eventToShare ->
                                    launchWhatsApp(context, phone, name, eventToShare)
                                }
                            )
                        }
                    }
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

private fun launchWhatsApp(context: android.content.Context, phone: String, name: String, event: Event) {
    val message = context.getString(R.string.whatsapp_event_message, event.title)
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://wa.me/$phone?text=$encodedMessage")
    }
    context.startActivity(intent)
}