package com.pqsolutions.hdd_monitor.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect

@Composable
fun UserCard(
    user: UserData,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.field_status,
                        if (user.role == UserRole.ADMIN)
                            stringResource(R.string.admin_dashboard_title)
                        else
                            stringResource(R.string.user_dashboard_title)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(
                    onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onEditClick()
                    }
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.update),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onDeleteClick()
                    }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun UserDialog(
    user: UserData? = null,
    onDismiss: () -> Unit,
    onConfirm: (UserData) -> Unit
) {
    var name by remember { mutableStateOf(user?.name ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var roleString by remember { mutableStateOf(user?.role?.let { UserRole.toFirestoreValue(it) } ?: "user") }
    var clientDocName by remember { mutableStateOf(user?.clientDocName ?: "") }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(if (user == null) R.string.create else R.string.update))
        },
        text = {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_event_title)) },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = clientDocName,
                    onValueChange = { clientDocName = it },
                    label = { Text(stringResource(R.string.field_panel_id, "")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = roleString == UserRole.toFirestoreValue(UserRole.USER)
                )

                Column {
                    Text(
                        text = stringResource(R.string.field_status, ""),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RadioButton(
                            selected = roleString == UserRole.toFirestoreValue(UserRole.USER),
                            onClick = { roleString = UserRole.toFirestoreValue(UserRole.USER) }
                        )
                        Text(stringResource(R.string.user_dashboard_title))
                        Spacer(modifier = Modifier.weight(1f))
                        RadioButton(
                            selected = roleString == UserRole.toFirestoreValue(UserRole.ADMIN),
                            onClick = { roleString = UserRole.toFirestoreValue(UserRole.ADMIN) }
                        )
                        Text(stringResource(R.string.admin_dashboard_title))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onConfirm(
                        UserData(
                            documentName = user?.documentName ?: "",
                            email = email,
                            name = name,
                            roleString = roleString,
                            clientDocName = clientDocName,
                            // fcmToken se maneja automáticamente en el backend
                            fcmToken = user?.fcmToken
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}