package com.pqsolutions.hdd_monitor.presentation.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    event: Event?,
    clients: List<Client>,
    availablePanels: List<Panel>,
    selectedDate: LocalDate?,
    selectedTime: LocalTime?,
    selectedClientForPanels: String?,
    selectedPanelDocName: String?,
    newEventTitle: String,
    newEventDescription: String,
    isAdmin: Boolean,
    onEvent: (EventDialogEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isEditing = event != null
    val canEdit = event == null || event.isProgramado

    val title = if (isEditing) event?.title ?: "" else newEventTitle
    val description = if (isEditing) event?.text ?: "" else newEventDescription
    var selectedClients by remember { mutableStateOf(setOf<String>()) }

    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // Validación del máximo de caracteres
    val maxTitleLength = 50
    val maxDescriptionLength = 500

    // Validación de fechas
    val isDateValid = if (isEditing) {
        selectedDate != null
    } else {
        selectedDate != null &&
                (selectedDate.isAfter(LocalDate.now()) || selectedDate.isEqual(LocalDate.now()))
    }

    val isTimeValid = if (isEditing) {
        selectedTime != null
    } else {
        selectedTime != null &&
                (selectedDate?.isAfter(LocalDate.now()) == true ||
                        (selectedDate?.isEqual(LocalDate.now()) == true &&
                                selectedTime.isAfter(LocalTime.now())))
    }

    // Validación del formulario
    val isFormValid = canEdit &&
            title.isNotBlank() &&
            description.isNotBlank() &&
            isDateValid &&
            isTimeValid &&
            title.length <= maxTitleLength &&
            description.length <= maxDescriptionLength &&
            (isEditing || selectedClients.isNotEmpty())

    // Date Picker
    val datePickerDialog = remember {
        val calendar = Calendar.getInstance()
        selectedDate?.let {
            calendar.set(it.year, it.monthValue - 1, it.dayOfMonth)
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onEvent(EventDialogEvent.DateSelected(LocalDate.of(year, month + 1, dayOfMonth)))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }
    }

    // Time Picker
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onEvent(EventDialogEvent.TimeSelected(LocalTime.of(hourOfDay, minute)))
            },
            selectedTime?.hour ?: LocalTime.now().hour,
            selectedTime?.minute ?: LocalTime.now().minute,
            true
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (event == null) R.string.create_new_event else R.string.edit_event
                )
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    if (!canEdit) {
                        Text(
                            text = stringResource(R.string.event_already_accepted),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            if (canEdit && it.length <= maxTitleLength) {
                                onEvent(EventDialogEvent.TitleChanged(it))
                            }
                        },
                        label = { Text(stringResource(R.string.event_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canEdit,
                        singleLine = true,
                        isError = title.isBlank(),
                        supportingText = {
                            Text(
                                "${title.length}/$maxTitleLength" +
                                        if (title.isBlank()) " - ${stringResource(R.string.required_field)}" else ""
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            if (canEdit && it.length <= maxDescriptionLength) {
                                onEvent(EventDialogEvent.DescriptionChanged(it))
                            }
                        },
                        label = { Text(stringResource(R.string.event_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canEdit,
                        minLines = 3,
                        maxLines = 5,
                        isError = description.isBlank(),
                        supportingText = {
                            Text(
                                "${description.length}/$maxDescriptionLength" +
                                        if (description.isBlank()) " - ${stringResource(R.string.required_field)}" else ""
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = selectedDate?.format(dateFormatter) ?: "",
                            onValueChange = { },
                            label = { Text(stringResource(R.string.date)) },
                            modifier = Modifier.weight(1f),
                            readOnly = true,
                            enabled = canEdit,
                            isError = !isDateValid,
                            trailingIcon = {
                                IconButton(
                                    onClick = { if (canEdit) datePickerDialog.show() },
                                    enabled = canEdit
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = selectedTime?.format(timeFormatter) ?: "",
                            onValueChange = { },
                            label = { Text(stringResource(R.string.time)) },
                            modifier = Modifier.weight(1f),
                            readOnly = true,
                            enabled = canEdit,
                            isError = !isTimeValid,
                            trailingIcon = {
                                IconButton(
                                    onClick = { if (canEdit) timePickerDialog.show() },
                                    enabled = canEdit
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null)
                                }
                            }
                        )
                    }
                }

                // Selección de cliente y panel para nuevo evento
                if (!isEditing && isAdmin) {
                    item {
                        Text(
                            text = stringResource(R.string.select_clients),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )

                        Text(
                            text = stringResource(R.string.single_client_panel_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(clients.sortedBy { it.name }) { client ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = client.documentName in selectedClients,
                                onCheckedChange = { checked ->
                                    if (canEdit) {
                                        selectedClients = if (checked) {
                                            setOf(client.documentName)  // Solo permitir uno
                                        } else {
                                            emptySet()
                                        }
                                        onEvent(EventDialogEvent.MultipleClientsSelected(selectedClients.toList()))
                                    }
                                },
                                enabled = canEdit
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

                // Selector de panel cuando hay un cliente seleccionado
                if (selectedClients.size == 1 && availablePanels.isNotEmpty()) {
                    item {
                        var expanded by remember { mutableStateOf(false) }

                        Text(
                            text = stringResource(R.string.select_panel),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { if (canEdit) expanded = it }
                        ) {
                            OutlinedTextField(
                                value = availablePanels.find { it.documentName == selectedPanelDocName }?.name
                                    ?: stringResource(R.string.select_panel),
                                onValueChange = { },
                                label = { Text(stringResource(R.string.panel)) },
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = canEdit
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                availablePanels
                                    .sortedBy { it.name }
                                    .forEach { panel ->
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
        },
        confirmButton = {
            Button(
                onClick = {
                    Log.d("EventDialog", "Confirmando con título: $title")
                    Log.d("EventDialog", "Confirmando con descripción: $description")
                    Log.d("EventDialog", "Confirmando con panel: $selectedPanelDocName")
                    onEvent(EventDialogEvent.Confirm)
                },
                enabled = isFormValid
            ) {
                Text(
                    stringResource(
                        if (isEditing) R.string.update else R.string.create
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}