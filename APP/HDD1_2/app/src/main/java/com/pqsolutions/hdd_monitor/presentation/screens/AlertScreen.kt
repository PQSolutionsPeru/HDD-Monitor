package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.presentation.viewmodel.AlertViewModel
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AlertScreen(
    viewModel: AlertViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    isAdmin: Boolean
) {
    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        var showDialog by remember { mutableStateOf(false) }
        var editingAlert by remember { mutableStateOf<Alert?>(null) }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            viewModel.loadAlerts()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.alerts),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                uiState.error != null -> {
                    ErrorMessage(
                        message = uiState.error,
                        onRetry = { viewModel.loadAlerts() }
                    )
                }
                uiState.alerts.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_alerts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                else -> {
                    AnimatedContent(
                        targetState = uiState.alerts,
                        transitionSpec = {
                            fadeIn(initialAlpha = 0.3f) togetherWith fadeOut(targetAlpha = 0f)
                        }
                    ) { alerts ->
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(alerts, key = { it.id }) { alert ->
                                AlertItem(
                                    alert = alert,
                                    isAdmin = isAdmin,
                                    onEditClick = {
                                        editingAlert = alert
                                        showDialog = true
                                    },
                                    onDeleteClick = {
                                        performHapticFeedback(context)
                                        playSoundEffect(context, R.raw.button_click)
                                        viewModel.deleteAlert(alert.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (isAdmin) {
                Button(
                    onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        editingAlert = null
                        showDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(R.string.create_new_alert), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Button(
                onClick = {
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onBackClick()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(stringResource(R.string.back), style = MaterialTheme.typography.labelLarge)
            }
        }

        if (showDialog) {
            AlertDialog(
                alert = editingAlert,
                onDismiss = { showDialog = false },
                onConfirm = { alert ->
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    if (editingAlert == null) {
                        viewModel.createAlert(alert)
                    } else {
                        viewModel.updateAlert(alert)
                    }
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun AlertItem(
    alert: Alert,
    isAdmin: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.date_time, alert.dateTime),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.status, alert.status),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.priority, alert.priority),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.description,
                style = MaterialTheme.typography.bodySmall
            )
            if (isAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onEditClick()
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_alert))
                    }
                    IconButton(onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onDeleteClick()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_alert))
                    }
                }
            }
        }
    }
}

@Composable
fun AlertDialog(
    alert: Alert? = null,
    onDismiss: () -> Unit,
    onConfirm: (Alert) -> Unit
) {
    var title by remember { mutableStateOf(alert?.title ?: "") }
    var description by remember { mutableStateOf(alert?.description ?: "") }
    var priority by remember { mutableStateOf(alert?.priority ?: "Low") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (alert == null) stringResource(R.string.add_alert) else stringResource(R.string.edit_alert)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.priority), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = priority == "Low",
                        onClick = { priority = "Low" }
                    )
                    Text(stringResource(R.string.low), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = priority == "Medium",
                        onClick = { priority = "Medium" }
                    )
                    Text(stringResource(R.string.medium), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = priority == "High",
                        onClick = { priority = "High" }
                    )
                    Text(stringResource(R.string.high), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onConfirm(Alert(
                    id = alert?.id ?: "",
                    title = title,
                    description = description,
                    priority = priority,
                    dateTime = alert?.dateTime ?: System.currentTimeMillis().toString(),
                    status = alert?.status ?: "Active"
                ))
            }) {
                Text(stringResource(R.string.confirm), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            Button(onClick = {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun ErrorMessage(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message ?: stringResource(R.string.unknown_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}