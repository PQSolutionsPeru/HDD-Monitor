package com.pqsolutions.hdd_monitor.presentation.screens

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.presentation.viewmodel.AlertViewModel

@Composable
fun AlertScreen(
    viewModel: AlertViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    isAdmin: Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingAlert by remember { mutableStateOf<Alert?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadAlerts("client_id") // Replace with actual client ID
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Alerts",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
            }
            uiState.error != null -> {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                LazyColumn {
                    items(uiState.alerts) { alert ->
                        AlertItem(
                            alert = alert,
                            isAdmin = isAdmin,
                            onEditClick = {
                                editingAlert = alert
                                showDialog = true
                            },
                            onDeleteClick = { viewModel.deleteAlert("client_id", alert.id) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (isAdmin) {
            Button(
                onClick = {
                    editingAlert = null
                    showDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create New Alert")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }

    if (showDialog) {
        AlertDialog(
            alert = editingAlert,
            onDismiss = { showDialog = false },
            onConfirm = { alert ->
                if (editingAlert == null) {
                    viewModel.createAlert("client_id", alert)
                } else {
                    viewModel.updateAlert("client_id", alert)
                }
                showDialog = false
            }
        )
    }
}

@Composable
fun AlertItem(
    alert: Alert,
    isAdmin: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = alert.title, style = MaterialTheme.typography.headlineSmall)
            Text(text = "Date: ${alert.dateTime}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Status: ${alert.status}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Priority: ${alert.priority}", style = MaterialTheme.typography.bodyMedium)
            Text(text = alert.description, style = MaterialTheme.typography.bodySmall)
            if (isAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Alert")
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Alert")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (alert == null) "Add Alert" else "Edit Alert") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Priority:")
                    RadioButton(
                        selected = priority == "Low",
                        onClick = { priority = "Low" }
                    )
                    Text("Low")
                    RadioButton(
                        selected = priority == "Medium",
                        onClick = { priority = "Medium" }
                    )
                    Text("Medium")
                    RadioButton(
                        selected = priority == "High",
                        onClick = { priority = "High" }
                    )
                    Text("High")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(Alert(
                    id = alert?.id ?: "",
                    title = title,
                    description = description,
                    priority = priority,
                    dateTime = alert?.dateTime ?: System.currentTimeMillis().toString(),
                    status = alert?.status ?: "Active"
                ))
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}