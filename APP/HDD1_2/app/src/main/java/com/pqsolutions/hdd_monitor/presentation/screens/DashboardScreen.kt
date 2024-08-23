package com.pqsolutions.hdd_monitor.presentation.screens

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.Relay
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardUiState
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.theme.*

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Text(
                text = "Panel de Control Admin",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            DashboardButton(
                onClick = onManageUsersClick,
                text = "Gestionar Usuarios"
            )
            Spacer(modifier = Modifier.height(16.dp))
            DashboardButton(
                onClick = onViewAlertsClick,
                text = "Ver Alertas"
            )
            Spacer(modifier = Modifier.height(16.dp))
            DashboardButton(
                onClick = onViewEventHistoryClick,
                text = "Historial de Eventos"
            )
            Spacer(modifier = Modifier.height(32.dp))
            PanelsList(uiState)
            Spacer(modifier = Modifier.height(32.dp))
            DashboardButton(
                onClick = onLogoutClick,
                text = "Cerrar Sesión",
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Text(
                text = "Panel de Control Usuario",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            DashboardButton(
                onClick = onViewEventHistoryClick,
                text = "Historial de Eventos"
            )
            Spacer(modifier = Modifier.height(16.dp))
            DashboardButton(
                onClick = onViewAlertsClick,
                text = "Ver Alertas"
            )
            Spacer(modifier = Modifier.height(32.dp))
            PanelsList(uiState)
            Spacer(modifier = Modifier.height(32.dp))
            DashboardButton(
                onClick = onLogoutClick,
                text = "Cerrar Sesión",
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
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = colors,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
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
                text = "Error: ${uiState.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        else -> {
            Column {
                Text(
                    text = "Paneles de Incendio",
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
            text = "Alertas Activas",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        alerts.forEach { alert ->
            Text(
                text = alert,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PanelItem(panel: Panel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Implementar acción al hacer clic en el panel */ },
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
                text = "Ubicación: ${panel.location}",
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