package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell
import com.pqsolutions.hdd_monitor.presentation.components.LoadingContent
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationItem
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationType
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    notificationViewModel: NotificationViewModel,
    onBackClick: () -> Unit,
    hasPendingNotifications: Boolean,
    onNavigateToEvent: (String) -> Unit,
    onNavigateToPanel: (String) -> Unit
) {
    val uiState by notificationViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        try {
            // Refrescar notificaciones (incluye limpieza interna)
            notificationViewModel.refresh()

            // Esperar un poco antes de marcar como leídas para asegurar que estén cargadas
            kotlinx.coroutines.delay(500)
            notificationViewModel.markAllAsRead()
        } catch (e: Exception) {
            Log.e("NotificationHistoryScreen", "Error en LaunchedEffect", e)
        }
    }

    // Mantenemos este DisposableEffect para limpiar al salir
    DisposableEffect(Unit) {
        onDispose {
            notificationViewModel.clearError()
        }
    }

    val showEmptyState = remember(uiState.notifications) {
        uiState.notifications.isEmpty() && !uiState.isLoading && uiState.error == null
    }

    BackHandler {
        onBackClick()
    }

    HDD1_2Theme {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.notification_history_title),
                    onBackClick = {
                        try {
                            onBackClick()
                        } catch (e: Exception) {
                            Log.e("NotificationHistoryScreen", "Error en navegación", e)
                        }
                    }
                )
            }
        ) { paddingValues ->
            LoadingContent(
                isLoading = uiState.isLoading,
                isEmpty = showEmptyState,
                error = uiState.error,
                onRetry = { notificationViewModel.refresh() },
                emptyContent = { EmptyNotificationsContent() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NotificationsList(
                    notifications = uiState.notifications,
                    onNotificationClick = { notification ->
                        notificationViewModel.onNotificationClick(
                            notification = notification,
                            onNavigateToEvent = onNavigateToEvent,
                            onNavigateToPanel = onNavigateToPanel
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyNotificationsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_notifications),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NotificationsList(
    notifications: List<NotificationItem>,
    onNotificationClick: (NotificationItem) -> Unit
) {
    // IMPORTANTE: Asegurar que las notificaciones siempre estén ordenadas por timestamp
    val sortedNotifications = remember(notifications) {
        notifications.sortedByDescending { it.timestamp }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(
            items = sortedNotifications,
            key = { notification -> "${notification.clientDocName}_${notification.documentName}" }
        ) { notification ->
            NotificationCard(
                notification = notification,
                onClick = { onNotificationClick(notification) }
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (!notification.isRead)
                MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono según tipo de notificación
            Icon(
                imageVector = when {
                    notification.notificationType == NotificationType.EVENT -> Icons.Filled.Event
                    notification.notificationType == NotificationType.RELAY && notification.text.contains("DISC") -> Icons.Filled.Warning
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 8.dp),
                tint = when {
                    notification.notificationType == NotificationType.EVENT -> MaterialTheme.colorScheme.primary
                    notification.notificationType == NotificationType.RELAY && notification.text.contains("DISC") -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                }
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    notification.panelDocName?.let { panelId ->
                        Text(
                            text = stringResource(R.string.field_panel_id, panelId),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = notification.date_time,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                notification.relayName?.let { relay ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.field_relay, relay),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}