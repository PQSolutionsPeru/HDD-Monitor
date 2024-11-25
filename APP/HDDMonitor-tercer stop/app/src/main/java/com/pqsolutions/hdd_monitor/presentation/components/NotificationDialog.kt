package com.pqsolutions.hdd_monitor.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.Notification

@Composable
fun NotificationDialog(
    notificationState: CombinedNotificationState,
    pendingEvents: List<Event>,
    pendingNotifications: List<Notification>,
    onDismiss: () -> Unit,
    onViewAllEvents: () -> Unit,
    onViewAllNotifications: () -> Unit,
    onViewAgenda: () -> Unit
) {
    val eventsListState = rememberLazyListState()
    val notificationsListState = rememberLazyListState()

    // Memoizar las listas ordenadas
    val sortedPendingEvents by remember(pendingEvents) {
        derivedStateOf {
            pendingEvents.sortedByDescending { it.date_time }
        }
    }

    val sortedPendingNotifications by remember(pendingNotifications) {
        derivedStateOf {
            pendingNotifications.sortedByDescending { it.date_time }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.notifications_pending),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Eventos pendientes
                if (sortedPendingEvents.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.pending_events_count, notificationState.eventsCount),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyColumn(
                        state = eventsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = sortedPendingEvents,
                            key = { it.documentName }
                        ) { event ->
                            EventNotificationItem(event = event)
                        }
                    }

                    TextButton(
                        onClick = {
                            onDismiss()
                            onViewAgenda()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.view_agenda))
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Alertas de panel
                if (sortedPendingNotifications.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.active_alerts_count, notificationState.panelAlertsCount),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    LazyColumn(
                        state = notificationsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = sortedPendingNotifications,
                            key = { it.documentName }
                        ) { notification ->
                            PanelNotificationItem(notification = notification)
                        }
                    }

                    TextButton(
                        onClick = {
                            onDismiss()
                            onViewAllNotifications()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.view_notification_history))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun EventNotificationItem(
    event: Event,
    modifier: Modifier = Modifier
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = event.date_time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PanelNotificationItem(
    notification: Notification,
    modifier: Modifier = Modifier
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notification.date_time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
            )
        }
    }
}