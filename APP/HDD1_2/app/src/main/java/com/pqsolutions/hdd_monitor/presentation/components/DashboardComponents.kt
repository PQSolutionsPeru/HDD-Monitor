package com.pqsolutions.hdd_monitor.presentation.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.Relay
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardUiState
import com.pqsolutions.hdd_monitor.presentation.theme.*
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import kotlinx.coroutines.delay

private const val TAG = "DashboardComponents"

@Composable
fun DashboardButton(
    onClick: () -> Unit,
    text: String,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    Log.d(TAG, "Composing DashboardButton: $text")
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)

    Button(
        onClick = {
            Log.d(TAG, "DashboardButton clicked: $text")
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
    Log.d(TAG, "Composing PanelsList")
    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorMessage(uiState.error)
        uiState.panels.isEmpty() -> EmptyPanelsMessage()
        else -> PanelsContent(uiState)
    }
}

@Composable
private fun LoadingIndicator() {
    Log.d(TAG, "PanelsList: Loading")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(200.dp)
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
    }
}

@Composable
private fun ErrorMessage(error: String) {
    Log.d(TAG, "PanelsList: Error - $error")
    Text(
        text = stringResource(R.string.error_message, error),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun EmptyPanelsMessage() {
    Log.d(TAG, "PanelsList: No panels")
    Text(
        text = stringResource(R.string.no_panels_found),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun PanelsContent(uiState: DashboardUiState) {
    Log.d(TAG, "PanelsList: Displaying ${uiState.panels.size} panels")
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
        uiState.panels.forEach { panel ->
            PanelItem(panel)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AlertsList(alerts: List<String>) {
    Log.d(TAG, "Composing AlertsList: ${alerts.size} alerts")
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
    Log.d(TAG, "Composing PanelItem: ${panel.name}, Relays: ${panel.relays.size}")
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                Log.d(TAG, "PanelItem clicked: ${panel.name}")
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
            if (panel.relays.isEmpty()) {
                Text(
                    text = stringResource(R.string.loading_relays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                panel.relays.forEach { relay ->
                    RelayStatusItem(relay)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun RelayStatusItem(relay: Relay) {
    Log.d(TAG, "Composing RelayStatusItem: ${relay.name}")
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
    Log.d(TAG, "Composing StatusChip: $status")
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