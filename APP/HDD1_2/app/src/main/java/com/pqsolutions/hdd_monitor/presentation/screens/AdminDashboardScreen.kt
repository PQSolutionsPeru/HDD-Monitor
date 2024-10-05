package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.Relay
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell
import com.pqsolutions.hdd_monitor.presentation.components.LogoutButton

private const val TAG = "AdminDashboardScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onManageUsersClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit,
    hasPendingNotifications: Boolean,
    onBackPressed: () -> Unit
) {
    Log.d(TAG, "Starting composition with hasPendingNotifications: $hasPendingNotifications")

    BackHandler(onBack = onBackPressed)

    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            Log.d(TAG, "LaunchedEffect: Loading panels")
            viewModel.loadPanels()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.admin_dashboard_title)) },
                    actions = {
                        AnimatedNotificationBell(
                            hasNewNotifications = hasPendingNotifications,
                            onClick = {
                                Log.d(TAG, "Notification bell clicked")
                                onViewAlertsClick()
                            },
                            modifier = Modifier.size(48.dp)
                        )
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                item {
                    ActionButtons(
                        onManageUsersClick = onManageUsersClick,
                        onViewAlertsClick = onViewAlertsClick,
                        onViewEventHistoryClick = onViewEventHistoryClick,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    PanelsList(uiState.panels, viewModel::selectPanel)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    LogoutButton(onLogoutClick, context)
                }
            }
        }

        // Panel details dialog
        if (uiState.selectedPanel != null) {
            PanelDetailsDialog(
                panel = uiState.selectedPanel!!,
                onDismiss = { viewModel.deselectPanel() }
            )
        }
    }
    Log.d(TAG, "Finishing composition")
}

@Composable
fun ActionButtons(
    onManageUsersClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit,
    context: android.content.Context
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                Log.d(TAG, "Manage Users button clicked")
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onManageUsersClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.manage_users))
        }
        Button(
            onClick = {
                Log.d(TAG, "View Alerts button clicked")
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onViewAlertsClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.view_alerts))
        }
        Button(
            onClick = {
                Log.d(TAG, "View Event History button clicked")
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onViewEventHistoryClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.view_event_history))
        }
    }
}

@Composable
fun PanelsList(panels: List<Panel>, onPanelClick: (String) -> Unit) {
    Text(
        text = "Paneles de Incendio (${panels.size})",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    if (panels.isEmpty()) {
        Text("No hay paneles disponibles")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            panels.forEach { panel ->
                PanelItem(panel, onPanelClick)
            }
        }
    }
}

@Composable
fun PanelItem(panel: Panel, onPanelClick: (String) -> Unit) {
    val allRelaysOk = panel.relays.all { it.status == "OK" }
    val statusColor = if (allRelaysOk) Color.Green else Color.Red
    val statusText = if (allRelaysOk) "OK" else "Alerta"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPanelClick(panel.ID) },
        colors = CardDefaults.cardColors(
            containerColor = if (allRelaysOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = panel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Ubicación: ${panel.location}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Cliente: ${panel.clientId}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Estado:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PanelDetailsDialog(panel: Panel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(panel.name) },
        text = {
            Column {
                Text("Ubicación: ${panel.location}")
                Text("Cliente: ${panel.clientId}")
                Text("IP: ${panel.ESP32_IP}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Relays:", fontWeight = FontWeight.Bold)
                panel.relays.forEach { relay ->
                    RelayStatus(relay)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun RelayStatus(relay: Relay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = relay.name,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = relay.status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (relay.status == "OK") Color.Green else Color.Red,
            fontWeight = FontWeight.Bold
        )
    }
}