package com.pqsolutions.hdd_monitor.presentation.components.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.presentation.state.EventDialogEvent
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    eventTypes: List<String>,
    selectedEventType: String?,
    isAdmin: Boolean,
    onEvent: (EventDialogEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Estados para el diálogo
    val dialogState = remember(event) {
        EventDialogState(
            isEditing = event != null,
            canEdit = event?.isProgramado ?: true,
            title = event?.title ?: newEventTitle,
            description = event?.text ?: newEventDescription
        )
    }

    // Date y Time Formatters
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // Pickers
    val datePickerDialog = rememberDatePickerDialog(selectedDate, onEvent)
    val timePickerDialog = rememberTimePickerDialog(selectedTime, onEvent)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Título del diálogo
                Text(
                    text = stringResource(
                        if (dialogState.isEditing) R.string.edit_event
                        else R.string.create_new_event
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Contenido scrolleable
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    EventDialogContent(
                        dialogState = dialogState,
                        event = event,
                        clients = clients,
                        availablePanels = availablePanels,
                        selectedDate = selectedDate,
                        selectedTime = selectedTime,
                        selectedClientForPanels = selectedClientForPanels,
                        selectedPanelDocName = selectedPanelDocName,
                        selectedEventType = selectedEventType,
                        eventTypes = eventTypes,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                        isAdmin = isAdmin,
                        onDateClick = { datePickerDialog.show() },
                        onTimeClick = { timePickerDialog.show() },
                        onEvent = onEvent
                    )
                }

                // Botones de acción
                EventDialogActions(
                    dialogState = dialogState,
                    onConfirm = { scope.launch { onEvent(EventDialogEvent.Confirm) } },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun rememberDatePickerDialog(
    selectedDate: LocalDate?,
    onEvent: (EventDialogEvent) -> Unit
): DatePickerDialog {
    val context = LocalContext.current
    return remember(selectedDate) {
        val calendar = java.util.Calendar.getInstance()
        selectedDate?.let {
            calendar.set(it.year, it.monthValue - 1, it.dayOfMonth)
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onEvent(EventDialogEvent.DateSelected(LocalDate.of(year, month + 1, dayOfMonth)))
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }
    }
}

@Composable
private fun rememberTimePickerDialog(
    selectedTime: LocalTime?,
    onEvent: (EventDialogEvent) -> Unit
): TimePickerDialog {
    val context = LocalContext.current
    return remember(selectedTime) {
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
}