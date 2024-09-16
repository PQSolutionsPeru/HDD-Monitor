package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.data.EventWithMetadata
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.EventViewModel
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell

@Composable
fun EventHistoryScreen(
    viewModel: EventViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    hasPendingNotifications: Boolean,
    onNotificationClick: () -> Unit
) {
    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val isAdmin by viewModel.isAdmin.collectAsState()

        LaunchedEffect(key1 = true) {
            viewModel.loadEvents()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historial de Eventos",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                AnimatedNotificationBell(
                    hasNewNotifications = hasPendingNotifications,
                    onClick = onNotificationClick,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))

            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                uiState.events.isEmpty() -> {
                    Text(
                        text = "No hay eventos registrados",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.events) { eventWithMetadata ->
                            EventItem(eventWithMetadata, isAdmin = isAdmin)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    "Volver",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun EventItem(eventWithMetadata: EventWithMetadata, isAdmin: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = eventWithMetadata.event.date_time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = eventWithMetadata.event.type,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = eventWithMetadata.event.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Estado: ${eventWithMetadata.event.solved_status}",
                style = MaterialTheme.typography.bodySmall,
                color = when (eventWithMetadata.event.solved_status) {
                    "Resuelto" -> MaterialTheme.colorScheme.primary
                    "Pendiente" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Panel: ${eventWithMetadata.panelName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isAdmin) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cliente: ${eventWithMetadata.clientName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}