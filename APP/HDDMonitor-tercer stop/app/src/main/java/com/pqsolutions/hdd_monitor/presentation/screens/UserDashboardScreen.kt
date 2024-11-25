package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.Relay
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.theme.HddGreen
import com.pqsolutions.hdd_monitor.presentation.theme.HddRed
import com.pqsolutions.hdd_monitor.presentation.theme.PanelColors
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel

private const val TAG = "UserDashboardScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onViewEventsClick: () -> Unit,
    onViewNotificationHistoryClick: () -> Unit,
    hasPendingNotifications: Boolean
) {
    Log.d(TAG, "UserDashboardScreen composition started")

    val uiState = viewModel.uiState.collectAsState()
    val notificationUiState = notificationViewModel.uiState.collectAsState()
    val context = LocalContext.current

    HDD1_2Theme {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.user_dashboard_title),
                    navController = navController,
                    actions = {
                        AnimatedNotificationBell(
                            hasNewNotifications = hasPendingNotifications,
                            notificationCount = notificationUiState.value.pendingCount,
                            onClick = onViewEventsClick,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                )
            },
            bottomBar = {
                Button(
                    onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onLogoutClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(stringResource(R.string.logout))
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.value.isLoading -> {
                        LoadingContent()
                    }
                    uiState.value.error != null -> {
                        ErrorContent(
                            error = uiState.value.error!!,
                            onRetry = { viewModel.refreshAll() }
                        )
                    }
                    uiState.value.panels.isEmpty() -> {
                        EmptyPanelsContent()
                    }
                    else -> {
                        UserDashboardContent(
                            uiState = uiState.value,
                            onViewEventsClick = onViewEventsClick,
                            onViewNotificationHistoryClick = onViewNotificationHistoryClick
                        )
                    }
                }
            }
        }
    }

    Log.d(TAG, "UserDashboardScreen composition finished")
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun EmptyPanelsContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.no_panels_available),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UserDashboardContent(
    uiState: DashboardViewModel.DashboardUiState,
    onViewEventsClick: () -> Unit,
    onViewNotificationHistoryClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Botones de acción
        DashboardActions(
            onViewEventsClick = onViewEventsClick,
            onViewNotificationHistoryClick = onViewNotificationHistoryClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de paneles
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            items(
                items = uiState.panels,
                key = { panel -> "${panel.clientDocName}_${panel.documentName}" }
            ) { panel ->
                UserPanelItem(panel)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DashboardActions(
    onViewEventsClick: () -> Unit,
    onViewNotificationHistoryClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        DashboardButton(
            onClick = {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onViewEventsClick()
            },
            text = stringResource(R.string.view_events)
        )

        Spacer(modifier = Modifier.height(8.dp))

        DashboardButton(
            onClick = {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onViewNotificationHistoryClick()
            },
            text = stringResource(R.string.view_notification_history)
        )
    }
}

@Composable
private fun DashboardButton(onClick: () -> Unit, text: String) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}

@Composable
private fun UserPanelItem(panel: Panel) {
    Log.d(TAG, "Rendering UserPanelItem: ${panel.name}, Status: ${panel.overallStatus}")
    var expanded by remember { mutableStateOf(false) }
    val hasIssues = panel.overallStatus != "OK"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = PanelColors.getPanelBackgroundColor(panel.overallStatus)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = panel.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.field_location, panel.location),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (panel.overallStatus == "OK")
                    stringResource(R.string.panel_status_ok, panel.overallStatus)
                else
                    stringResource(R.string.panel_status_alert, panel.overallStatus),
                color = if (hasIssues) HddRed else HddGreen,
                style = MaterialTheme.typography.bodyMedium
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.relay_details),
                    style = MaterialTheme.typography.bodyMedium
                )
                panel.relays.forEach { relay ->
                    UserRelayStatusItem(relay)
                }
            }
        }
    }
}

@Composable
private fun UserRelayStatusItem(relay: Relay) {
    Log.d(TAG, "Rendering UserRelayStatusItem: ${relay.name}, Status: ${relay.status}")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.field_relay, relay.name),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.field_status, relay.status),
            color = PanelColors.getStatusColor(relay.status),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}