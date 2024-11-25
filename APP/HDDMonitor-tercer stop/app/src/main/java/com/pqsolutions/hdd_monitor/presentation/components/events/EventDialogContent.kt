package com.pqsolutions.hdd_monitor.presentation.components.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.presentation.state.EventDialogEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialogContent(
    dialogState: EventDialogState,
    event: Event?,
    clients: List<Client>,
    availablePanels: List<Panel>,
    selectedDate: LocalDate?,
    selectedTime: LocalTime?,
    selectedClientForPanels: String?,
    selectedPanelDocName: String?,
    selectedEventType: String?,
    eventTypes: List<String>,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    isAdmin: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onEvent: (EventDialogEvent) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mensaje de evento aceptado
        if (!dialogState.canEdit) {
            Text(
                text = stringResource(R.string.event_already_accepted),
                color = MaterialTheme.colorScheme.error
            )
        }

        // Selector de tipo de evento
        EventTypeSelector(
            selectedEventType = selectedEventType,
            eventTypes = eventTypes,
            isAdmin = isAdmin,
            onTypeSelected = { onEvent(EventDialogEvent.EventTypeSelected(it)) },
            onCreateNewType = { onEvent(EventDialogEvent.ShowCreateTypeDialog) },
            onDeleteType = { onEvent(EventDialogEvent.DeleteEventType(it)) },
            enabled = dialogState.canEdit
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Campo de título
        var titleState by remember { mutableStateOf(dialogState.title) }
        OutlinedTextField(
            value = titleState,
            onValueChange = { newValue ->
                if (dialogState.canEdit && newValue.length <= 50) {
                    titleState = newValue
                    onEvent(EventDialogEvent.TitleChanged(newValue))
                }
            },
            label = { Text(stringResource(R.string.event_title)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = dialogState.canEdit,
            singleLine = true,
            supportingText = {
                Text("${titleState.length}/50")
            },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Campo de descripción
        var descriptionState by remember { mutableStateOf(dialogState.description) }
        OutlinedTextField(
            value = descriptionState,
            onValueChange = { newValue ->
                if (dialogState.canEdit && newValue.length <= 500) {
                    descriptionState = newValue
                    onEvent(EventDialogEvent.DescriptionChanged(newValue))
                }
            },
            label = { Text(stringResource(R.string.event_description)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = dialogState.canEdit,
            minLines = 3,
            maxLines = 5,
            supportingText = {
                Text("${descriptionState.length}/500")
            },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Fecha y hora
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Selector de fecha
            OutlinedTextField(
                value = selectedDate?.format(dateFormatter) ?: "",
                onValueChange = { },
                label = { Text(stringResource(R.string.date)) },
                modifier = Modifier.weight(1f),
                readOnly = true,
                enabled = dialogState.canEdit,
                trailingIcon = {
                    IconButton(
                        onClick = { if (dialogState.canEdit) onDateClick() }
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                    }
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Selector de hora
            OutlinedTextField(
                value = selectedTime?.format(timeFormatter) ?: "",
                onValueChange = { },
                label = { Text(stringResource(R.string.time)) },
                modifier = Modifier.weight(1f),
                readOnly = true,
                enabled = dialogState.canEdit,
                trailingIcon = {
                    IconButton(
                        onClick = { if (dialogState.canEdit) onTimeClick() }
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                    }
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // Selector de clientes y paneles
        if (!dialogState.isEditing && isAdmin) {
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

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(clients.size) { index ->
                        val client = clients[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isChecked by remember { mutableStateOf(client.documentName in dialogState.selectedClients) }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    isChecked = checked
                                    val newSelection = if (checked) {
                                        setOf(client.documentName)
                                    } else {
                                        emptySet()
                                    }
                                    onEvent(EventDialogEvent.MultipleClientsSelected(newSelection.toList()))
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
        }

        // Selector de panel
        if (dialogState.selectedClients.size == 1 && availablePanels.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.select_panel),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (dialogState.canEdit) expanded = it }
                ) {
                    OutlinedTextField(
                        value = availablePanels.find { it.documentName == selectedPanelDocName }?.name
                            ?: stringResource(R.string.select_panel),
                        onValueChange = { },
                        readOnly = true,
                        enabled = dialogState.canEdit,
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
                        availablePanels.sortedBy { it.name }.forEach { panel ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = panel.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    onEvent(EventDialogEvent.PanelSelected(panel.documentName))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}