package com.pqsolutions.hdd_monitor.presentation.components.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.data.Panel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventTypeSelector(
    selectedEventType: String?,
    eventTypes: List<String>,
    isAdmin: Boolean,
    onTypeSelected: (String) -> Unit,
    onCreateNewType: () -> Unit,
    onDeleteType: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tipo de Evento",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        if (isAdmin) {
            Text(
                text = "(Haga clic largo en un tipo para eliminarlo)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            TextField(
                value = selectedEventType ?: "Seleccione tipo de evento",
                onValueChange = { },
                readOnly = true,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                eventTypes.sorted().forEach { eventType ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    onTypeSelected(eventType)
                                    expanded = false
                                },
                                onLongClick = {
                                    if (isAdmin) {
                                        showDeleteConfirmation = eventType
                                    }
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = eventType,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (isAdmin) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "+ Crear Nuevo Tipo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                expanded = false
                                onCreateNewType()
                            }
                        )
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para eliminar tipo
    if (showDeleteConfirmation != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text("Confirmar eliminación") },
            text = {
                Column {
                    Text("¿Está seguro que desea eliminar el tipo de evento '${showDeleteConfirmation}'?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Esta acción no se puede deshacer y solo será posible si ningún evento está usando este tipo.",
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation?.let { typeToDelete ->
                            onDeleteType(typeToDelete)
                        }
                        showDeleteConfirmation = null
                        expanded = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSelector(
    clients: List<Client>,
    selectedClients: Set<String>,
    onClientsSelected: (List<String>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.select_clients),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = stringResource(R.string.single_client_panel_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        clients.sortedBy { it.name }.forEach { client ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = client.documentName in selectedClients,
                    onCheckedChange = { checked ->
                        val newSelection = if (checked) {
                            setOf(client.documentName)
                        } else {
                            emptySet()
                        }
                        onClientsSelected(newSelection.toList())
                    }
                )

                Text(
                    text = client.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelSelector(
    panels: List<Panel>,
    selectedPanelDocName: String?,
    onPanelSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.select_panel),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            TextField(
                value = panels.find { it.documentName == selectedPanelDocName }?.name
                    ?: stringResource(R.string.select_panel),
                onValueChange = { },
                readOnly = true,
                label = { Text(stringResource(R.string.panel)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                panels.sortedBy { it.name }.forEach { panel ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = panel.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onPanelSelected(panel.documentName)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}