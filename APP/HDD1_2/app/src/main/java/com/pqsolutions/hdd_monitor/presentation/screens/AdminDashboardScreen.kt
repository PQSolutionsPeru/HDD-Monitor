package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdminDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onManageUsersClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit,
    hasPendingNotifications: Boolean
) {
    Log.d("AdminDashboardScreen", "Composing AdminDashboardScreen")
    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current
        val scrollState = rememberScrollState()

        LaunchedEffect(Unit) {
            Log.d("AdminDashboardScreen", "LaunchedEffect: Loading panels")
            viewModel.loadPanels()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.admin_dashboard_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                AnimatedNotificationBell(
                    hasNewNotifications = hasPendingNotifications,
                    onClick = onViewAlertsClick
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Button(
                        onClick = {
                            Log.d("AdminDashboardScreen", "Manage Users button clicked")
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onManageUsersClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.manage_users))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            Log.d("AdminDashboardScreen", "View Alerts button clicked")
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewAlertsClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.view_alerts))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            Log.d("AdminDashboardScreen", "View Event History button clicked")
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
            Spacer(modifier = Modifier.height(32.dp))
            PanelsList(uiState.panels)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    Log.d("AdminDashboardScreen", "Logout button clicked")
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onLogoutClick()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(stringResource(R.string.logout))
            }
        }
    }
    Log.d("AdminDashboardScreen", "AdminDashboardScreen composition completed")
}

@Composable
fun PanelsList(panels: List<Panel>) {
    Text(
        text = "Paneles de Incendio (${panels.size})",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
    if (panels.isEmpty()) {
        Text("No hay paneles disponibles")
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            panels.forEach { panel ->
                PanelItem(panel)
            }
        }
    }
}

@Composable
fun PanelItem(panel: Panel) {
    var expanded by remember { mutableStateOf(false) }
    val allRelaysOk = panel.relays.all { it.status == "OK" }
    val statusColor = if (allRelaysOk) Color.Green else Color.Red
    val statusText = if (allRelaysOk) "OK" else {
        panel.relays.filter { it.status != "OK" }.joinToString(", ") { it.name }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allRelaysOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = panel.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Ubicación: ${panel.location}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "IP: ${panel.ESP32_IP}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Cliente: ${panel.clientId}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Estado:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Detalles de relays:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                panel.relays.forEach { relay ->
                    RelayStatus(relay)
                }
            }
        }
    }
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
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = relay.status,
            style = MaterialTheme.typography.bodySmall,
            color = if (relay.status == "OK") Color.Green else Color.Red,
            fontWeight = FontWeight.Bold
        )
    }
}