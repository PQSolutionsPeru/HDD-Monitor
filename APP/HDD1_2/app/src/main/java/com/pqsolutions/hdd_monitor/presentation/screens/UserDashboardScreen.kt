package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
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
    hasPendingNotifications: Boolean
) {
    Log.d(TAG, "UserDashboardScreen composition started")

    val uiState by viewModel.uiState.collectAsState()
    val notificationUiState by notificationViewModel.uiState.collectAsState()
    val context = LocalContext.current

    HDD1_2Theme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    DashboardHeader(
                        hasNewNotifications = hasPendingNotifications,
                        pendingCount = notificationUiState.pendingCount,
                        onViewEventsClick = onViewEventsClick
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DashboardActions(
                        onViewEventsClick = onViewEventsClick,
                        onViewNotificationHistoryClick = onViewNotificationHistoryClick,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                items(uiState.panels) { panel ->
                    UserPanelItem(panel)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                LogoutButton(onLogoutClick, context)
            }
        }
    }

    Log.d(TAG, "UserDashboardScreen composition finished")
}

@Composable
private fun DashboardHeader(
    hasNewNotifications: Boolean,
    pendingCount: Int,
    onViewEventsClick: () -> Unit
) {
    Log.d(TAG, "Rendering DashboardHeader")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.user_dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        AnimatedNotificationBell(
            hasNewNotifications = hasNewNotifications,
            notificationCount = pendingCount,
            onClick = {
                Log.d(TAG, "Notification bell clicked")
                onViewEventsClick()
            },
            modifier = Modifier.size(48.dp)
        )
    }
    Log.d(TAG, "DashboardHeader rendered")
}

@Composable
private fun DashboardActions(
    onViewEventsClick: () -> Unit,
    onViewNotificationHistoryClick: () -> Unit,
    context: android.content.Context
) {
    Log.d(TAG, "Rendering DashboardActions")
    Column {
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
    }
    Log.d(TAG, "DashboardActions rendered")
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
fun UserPanelItem(panel: Panel) {
    Log.d(TAG, "Renderizando UserPanelItem: ${panel.name}, Estado: ${panel.overallStatus}")
    var expanded by remember { mutableStateOf(false) }
    val hasIssues = panel.overallStatus != "OK"
    val statusColor = if (hasIssues) Color.Red else Color.Green
    val overallStatus = panel.overallStatus

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
            Text(
                text = "Estado: $overallStatus",
                color = statusColor,
                style = MaterialTheme.typography.bodyMedium
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Detalles de relays:", style = MaterialTheme.typography.bodyMedium)
                panel.relays.forEach { relay ->
                    UserRelayStatus(relay)
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

@Composable
private fun LogoutButton(
    onLogoutClick: () -> Unit,
    context: android.content.Context
) {
    Log.d(TAG, "Rendering LogoutButton")
    Button(
        onClick = {
            Log.d(TAG, "Logout button clicked")
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
    Log.d(TAG, "LogoutButton rendered")
}