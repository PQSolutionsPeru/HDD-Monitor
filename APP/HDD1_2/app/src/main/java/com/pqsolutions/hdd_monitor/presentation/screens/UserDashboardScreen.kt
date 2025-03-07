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
import androidx.compose.runtime.LaunchedEffect
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
import com.pqsolutions.hdd_monitor.presentation.navigation.Screen
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.theme.PanelColors
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel

private const val TAG = "UserDashboardScreen"

@Composable
fun UserDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onViewEventsClick: () -> Unit,
    onViewNotificationHistoryClick: () -> Unit,
    onConfigureEsp32Click: () -> Unit,
    hasPendingNotifications: Boolean,
    selectedPanelId: String? = null
) {
    Log.d(TAG, "UserDashboardScreen composition started")

    val uiState by viewModel.uiState.collectAsState()
    val notificationUiState by notificationViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(key1 = Unit) {
        Log.d(TAG, "LaunchedEffect: Loading panels for user dashboard")
        viewModel.loadPanels()
    }

    HDD1_2Theme {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.user_dashboard_title),
                    actions = {
                        AnimatedNotificationBell(
                            hasNewNotifications = hasPendingNotifications,
                            notificationCount = notificationUiState.pendingCount,
                            onClick = onViewEventsClick
                        )
                    }
                )
            },
            bottomBar = {
                Button(
                    onClick = {
                        performHapticFeedback(context)
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
                // Botones de acción fijos
                DashboardActions(
                    onViewEventsClick = onViewEventsClick,
                    onViewNotificationHistoryClick = onViewNotificationHistoryClick,
                    onConfigureEsp32Click = onConfigureEsp32Click,
                    context = context
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Lista scrolleable de paneles
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    if (uiState.panels.isEmpty()) {
                        item {
                            EmptyPanelsContent()
                        }
                    } else {
                        items(
                            items = uiState.panels,
                            key = { panel -> "${panel.clientName}_${panel.documentName}" }
                        ) { panel ->
                            UserPanelItem(panel)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    Log.d(TAG, "UserDashboardScreen composition finished")
}

@Composable
private fun DashboardActions(
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
                Log.d(TAG, "View Events button clicked")
                performHapticFeedback(context)
                onViewEventsClick()
            },
            text = stringResource(R.string.view_events)
        )
        Spacer(modifier = Modifier.height(8.dp))
        DashboardButton(
            onClick = {
                Log.d(TAG, "View Notification History button clicked")
                performHapticFeedback(context)
                onViewNotificationHistoryClick()
            },
            text = stringResource(R.string.view_notification_history)
        )
        Spacer(modifier = Modifier.height(8.dp))
        DashboardButton(
            onClick = {
                Log.d(TAG, "Configure ESP32 button clicked")
                performHapticFeedback(context)
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
private fun EmptyPanelsContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.no_panels_available),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun UserPanelItem(panel: Panel) {
    Log.d(TAG, "Rendering UserPanelItem: ${panel.name}, Status: ${panel.hasIssues}")
    var expanded by remember { mutableStateOf(false) }

    // Determinar color basado en estado del ESP32 primero, luego en relays
    val containerColor = when {
        panel.isESP32Offline() -> PanelColors.PanelBackgroundOffline
        panel.hasIssues -> Color(0xFFFFEBEE) // Rojo claro
        else -> Color(0xFFE8F5E9) // Verde claro
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = panel.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "Ubicación: ${panel.location}", style = MaterialTheme.typography.bodyMedium)

            // Estado especial para ESP32 OFFLINE
            if (panel.isESP32Offline()) {
                Text(
                    text = "Estado: ESP32 OFFLINE",
                    color = PanelColors.StatusDisc,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "Estado: ${if (panel.hasIssues) panel.relaysInDisc else "OK"}",
                    color = if (panel.hasIssues) Color.Red else Color.Green,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (panel.isESP32Offline()) {
                    Text(
                        "No hay datos disponibles - ESP32 OFFLINE",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = PanelColors.StatusDisc
                    )
                } else {
                    Text("Detalles de relays:", style = MaterialTheme.typography.bodyMedium)
                    panel.relays.forEach { relay ->
                        UserRelayStatus(relay)
                    }
                }
            }
        }
    }
}

@Composable
fun UserRelayStatus(relay: Relay) {
    Log.d(TAG, "Rendering UserRelayStatus: ${relay.name}, Status: ${relay.status}")
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