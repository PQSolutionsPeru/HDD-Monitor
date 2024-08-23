package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.Relay
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardUiState
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.theme.*
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdminDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onManageUsersClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit
) {
    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.admin_dashboard_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    DashboardButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onManageUsersClick()
                        },
                        text = stringResource(R.string.manage_users)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DashboardButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewAlertsClick()
                        },
                        text = stringResource(R.string.view_alerts)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DashboardButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewEventHistoryClick()
                        },
                        text = stringResource(R.string.view_event_history)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            PanelsList(uiState)
            Spacer(modifier = Modifier.height(32.dp))
            DashboardButton(
                onClick = {
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onLogoutClick()
                },
                text = stringResource(R.string.logout),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Composable
fun UserDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit,
    onViewAlertsClick: () -> Unit
) {
    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.user_dashboard_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    DashboardButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewEventHistoryClick()
                        },
                        text = stringResource(R.string.view_event_history)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DashboardButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewAlertsClick()
                        },
                        text = stringResource(R.string.view_alerts)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            PanelsList(uiState)
            Spacer(modifier = Modifier.height(32.dp))
            DashboardButton(
                onClick = {
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onLogoutClick()
                },
                text = stringResource(R.string.logout),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Composable
fun DashboardButton(
    onClick: () -> Unit,
    text: String,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)

    Button(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale),
        colors = colors,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

@Composable
fun PanelsList(uiState: DashboardUiState) {
    when {
        uiState.isLoading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        uiState.error != null -> {
            Text(
                text = stringResource(R.string.error_message, uiState.error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        else -> {
            Column {
                Text(
                    text = stringResource(R.string.fire_panels),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (uiState.alerts.isNotEmpty()) {
                    AlertsList(uiState.alerts)
                    Spacer(modifier = Modifier.height(24.dp))
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.panels) { panel ->
                        PanelItem(panel)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsList(alerts: List<String>) {
    Column {
        Text(
            text = stringResource(R.string.active_alerts),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        alerts.forEach { alert ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = alert,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PanelItem(panel: Panel) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                // Implementar acción al hacer clic en el panel
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = panel.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.location, panel.location),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            panel.relays.forEach { relay ->
                RelayStatusItem(relay)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun RelayStatusItem(relay: Relay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = relay.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        StatusChip(status = relay.status)
    }
}

@Composable
fun StatusChip(status: String) {
    val (backgroundColor, textColor) = when (status) {
        "OK" -> HddGreen to HddWhite
        "WARNING" -> HddYellow to HddBlack
        else -> HddRed to HddWhite
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}