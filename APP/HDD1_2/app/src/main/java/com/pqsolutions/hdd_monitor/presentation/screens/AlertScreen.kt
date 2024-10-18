package com.pqsolutions.hdd_monitor.presentation.screens

import android.content.Intent
import android.net.Uri
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
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.presentation.viewmodel.AlertViewModel
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AlertScreen(
    viewModel: AlertViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    isAdmin: Boolean,
    hasPendingNotifications: Boolean
) {
    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        var showDialog by remember { mutableStateOf(false) }
        var editingAlert by remember { mutableStateOf<Alert?>(null) }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            viewModel.loadAlerts()
            if (isAdmin) {
                viewModel.loadClients()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.alerts),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                AnimatedNotificationBell(
                    hasNewNotifications = hasPendingNotifications,
                    onClick = { /* No action needed here */ },
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
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
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.alerts, key = { alert -> alert.ID.ifEmpty { "${alert.ID_CLIENT}_${alert.hashCode()}" } }) { alert ->
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
                                    viewModel.deleteAlert(alert.ID_CLIENT, alert.ID)
                                },
                                onConfirmClick = {
                                    performHapticFeedback(context)
                                    playSoundEffect(context, R.raw.button_click)
                                    viewModel.updateAlertStatus(alert.ID_CLIENT, alert.ID, "ACEPTADO")
                                }
                            )
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
                clients = uiState.clients,
                isAdmin = isAdmin,
                onDismiss = { showDialog = false },
                onConfirm = { alert, selectedClients ->
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    if (editingAlert == null) {
                        viewModel.createAlert(alert, selectedClients)
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
    onDeleteClick: () -> Unit,
    onConfirmClick: () -> Unit
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
                text = "Estado: ${alert.status}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (alert.status) {
                    "PROGRAMADO" -> MaterialTheme.colorScheme.secondary
                    "ACEPTADO" -> MaterialTheme.colorScheme.tertiary
                    "RECHAZADO" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.text,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isAdmin) {
                    IconButton(onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onEditClick()
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar alerta")
                    }
                    IconButton(onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onDeleteClick()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar alerta")
                    }
                } else if (alert.status == "PROGRAMADO") {
                    Button(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onConfirmClick()
                        }
                    ) {
                        Text(stringResource(R.string.accept))
                    }
                    Button(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            val message = "Buen día, quisiera conversar sobre el evento \"${alert.title}\""
                            val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://wa.me/+51993533004?text=$encodedMessage")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.contact_admin))
                    }
                }
            }
        }
    }
}

@Composable
fun AlertDialog(
    alert: Alert? = null,
    clients: List<Client>,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Alert, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(alert?.title ?: "") }
    var text by remember { mutableStateOf(alert?.text ?: "") }
    var status by remember { mutableStateOf(alert?.status ?: "PROGRAMADO") }
    var selectedClients by remember { mutableStateOf(listOf<String>()) }
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
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.status), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = status == "PROGRAMADO",
                        onClick = { status = "PROGRAMADO" }
                    )
                    Text("PROGRAMADO", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = status == "ACEPTADO",
                        onClick = { status = "ACEPTADO" }
                    )
                    Text("ACEPTADO", style = MaterialTheme.typography.bodyMedium)
                }
                if (isAdmin && alert == null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Seleccionar Destinatarios", style = MaterialTheme.typography.bodyLarge)
                    LazyColumn(
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(clients) { client ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedClients.contains(client.ID),
                                    onCheckedChange = { isChecked ->
                                        selectedClients = if (isChecked) {
                                            selectedClients + client.ID
                                        } else {
                                            selectedClients - client.ID
                                        }
                                    }
                                )
                                Text(client.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onConfirm(
                    Alert(
                        ID = alert?.ID ?: "",
                        ID_CLIENT = alert?.ID_CLIENT ?: "",
                        title = title,
                        text = text,
                        status = status
                    ),
                    selectedClients
                )
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