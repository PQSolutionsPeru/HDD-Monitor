package com.pqsolutions.hdd_monitor.presentation.components.events

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R

@Composable
fun EventDialogActions(
    dialogState: EventDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(stringResource(R.string.cancel))
        }

        Button(
            onClick = onConfirm,
            enabled = dialogState.canEdit
        ) {
            Text(
                stringResource(
                    if (dialogState.isEditing) R.string.update
                    else R.string.create
                )
            )
        }
    }
}