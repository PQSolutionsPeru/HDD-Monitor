package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.Relay
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell
import com.pqsolutions.hdd_monitor.presentation.components.ScreenTopBar
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel

private const val TAG = "AdminDashboardScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onManageUsersClick: () -> Unit,
    onViewEventsClick: () -> Unit,
    onViewNotificationHistoryClick: () -> Unit,
    onConfigureEsp32Click: () -> Unit,
    hasPendingNotifications: Boolean,
    selectedPanelId: String? = null
) {
    Log.d(TAG, "AdminDashboardScreen composition started")

    val uiState by viewModel.uiState.collectAsState()
    val notificationUiState by notificationViewModel.uiState.collectAsState()
    val context = LocalContext.current

    HDD1_2Theme {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.admin_dashboard_title),
                    actions = {
                        AnimatedNotificationBell(
                            hasNewNotifications = hasPendingNotifications,
                            notificationCount = notificationUiState.pendingCount,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                DashboardActions(
                    onManageUsersClick = onManageUsersClick,
                    onViewEventsClick = onViewEventsClick,
                    onViewNotificationHistoryClick = onViewNotificationHistoryClick,
                    onConfigureEsp32Click = onConfigureEsp32Click,
                    context = context
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    uiState.groupedPanels.forEach { (clientName, clientPanels) ->
                        item {
                            Text(
                                text = "Cliente: $clientName",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(
                            items = clientPanels,
                            key = { panel -> "${panel.clientName}_${panel.documentName}" }
                        ) { panel ->
                            AdminPanelItem(panel)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
    Log.d(TAG, "AdminDashboardScreen composition finished")
}

@Composable
private fun DashboardActions(
    onManageUsersClick: () -> Unit,
    onViewEventsClick: () -> Unit,
    onViewNotificationHistoryClick: () -> Unit,
    onConfigureEsp32Click: () -> Unit,
    context: android.content.Context
) {
    Log.d(TAG, "Rendering DashboardActions")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        DashboardButton(
            onClick = {
                Log.d(TAG, "Manage Users button clicked")
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onManageUsersClick()
            },
            text = stringResource(R.string.manage_clients)
        )
        Spacer(modifier = Modifier.height(8.dp))
        DashboardButton(
            onClick = {
                Log.d(TAG, "View Events button clicked")
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onViewEventsClick()
            },
            text = stringResource(R.string.view_events)
        )
        Spacer(modifier = Modifier.height(8.dp))
        DashboardButton(
            onClick = {
                Log.d(TAG, "View Notification History button clicked")
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onViewNotificationHistoryClick()
            },
            text = stringResource(R.string.view_notification_history)
        )
        Spacer(modifier = Modifier.height(8.dp))
        DashboardButton(
            onClick = {
                Log.d(TAG, "Configure ESP32 button clicked")
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onConfigureEsp32Click()
            },
            text = stringResource(R.string.configure_esp32)
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
fun AdminPanelItem(panel: Panel) {
    Log.d(TAG, "Rendering AdminPanelItem: ${panel.name}, Status: ${panel.overallStatus}")
    var expanded by remember { mutableStateOf(false) }
    val hasIssues = panel.overallStatus != "OK"
    val statusColor = if (hasIssues) Color.Red else Color.Green

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (hasIssues) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = panel.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "Ubicación: ${panel.location}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "ID ESP32: ${panel.esp32_id}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Estado: ${panel.overallStatus}",
                color = statusColor,
                style = MaterialTheme.typography.bodyMedium
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Detalles de relays:", style = MaterialTheme.typography.bodyMedium)
                panel.relays.forEach { relay ->
                    AdminRelayStatus(relay)
                }
            }
        }
    }
}

@Composable
fun AdminRelayStatus(relay: Relay) {
    Log.d(TAG, "Rendering AdminRelayStatus: ${relay.name}, Status: ${relay.status}")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = relay.name, style = MaterialTheme.typography.bodySmall)
        Text(
            text = relay.status,
            color = when (relay.status) {
                "OK" -> Color.Green
                "DISC" -> Color.Red
                else -> Color.Yellow
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}