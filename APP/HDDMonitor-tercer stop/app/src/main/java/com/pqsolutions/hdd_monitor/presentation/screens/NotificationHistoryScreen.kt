package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Notification
import com.pqsolutions.hdd_monitor.presentation.components.LoadingContent
import com.pqsolutions.hdd_monitor.presentation.components.NotificationBellWrapper
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.LocalClientManager
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationHistoryViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch

private const val TAG = "NotificationHistoryScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    viewModel: NotificationHistoryViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    navController: NavController,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val notificationState by notificationViewModel.combinedNotificationState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 5 }
    }

    HDD1_2Theme {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.notification_history_title),
                    onBackClick = onBackClick,
                    navController = navController,
                    actions = {
                        NotificationBellWrapper(
                            modifier = Modifier.padding(end = 8.dp),
                            navController = navController
                        )
                    }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it * 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it * 2 })
                ) {
                    FloatingActionButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.scroll_to_top)
                        )
                    }
                }
            }
        ) { paddingValues ->
            LoadingContent(
                isLoading = uiState.isLoading,
                isEmpty = uiState.notifications.isEmpty() && !uiState.isLoading && uiState.error == null,
                error = uiState.error,
                onRetry = { viewModel.refreshNotifications() },
                emptyContent = { EmptyNotificationsContent() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        if (notificationState.hasUnread) {
                            item {
                                NotificationSummaryCard(
                                    eventsCount = notificationState.eventsCount,
                                    panelAlertsCount = notificationState.panelAlertsCount
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        items(
                            items = uiState.notifications,
                            key = { notification -> "${notification.clientDocName}_${notification.documentName}" }
                        ) { notification ->
                            NotificationCard(notification = notification)
                        }
                    }

                    // Indicador de scroll superior
                    AnimatedVisibility(
                        visible = listState.firstVisibleItemIndex > 0,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSummaryCard(
    eventsCount: Int,
    panelAlertsCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.notification_summary_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (eventsCount > 0) {
                Text(
                    text = stringResource(R.string.new_events_count, eventsCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (panelAlertsCount > 0) {
                Text(
                    text = stringResource(R.string.active_alerts_count, panelAlertsCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
fun NotificationCard(
    notification: Notification
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRecent()) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.client_name_prefix,
                        LocalClientManager.current.getClientName(notification.clientDocName)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = notification.date_time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.panel_id_prefix, notification.panelDocName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.relay_name_prefix, notification.relayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}