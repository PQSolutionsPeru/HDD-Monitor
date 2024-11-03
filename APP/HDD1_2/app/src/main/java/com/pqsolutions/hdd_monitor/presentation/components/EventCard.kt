package com.pqsolutions.hdd_monitor.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.data.Client
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.domain.model.EventStatus

@Composable
fun EventCard(
    event: Event,
    client: Client?,
    user: UserData?,
    isAdmin: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAcceptClick: () -> Unit,
    onContactWhatsApp: (String, String, Event) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Tipo de evento primero y en mayúsculas
            event.type?.let { type ->
                if (type.isNotEmpty()) {
                    Text(
                        text = type.uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }
            }

            // Título del evento
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Descripción del evento
            Text(
                text = event.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Fecha y hora
            Column {
                Text(
                    text = "Fecha/Hora del Evento:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = event.getFormattedDateTime(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Panel asociado (si existe)
            event.panelName?.let { panelName ->
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text(
                        text = "Panel:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = panelName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Cliente (solo en modo admin)
            if (isAdmin && client != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text(
                        text = "Cliente:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = client.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Creado por (si hay información del usuario)
            user?.let { userData ->
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text(
                        text = "Creado por:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = userData.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Estado del evento
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Estado:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = when (event.status) {
                        EventStatus.STATUS_PROGRAMADO -> MaterialTheme.colorScheme.primary
                        EventStatus.STATUS_ACEPTADO -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = event.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isAdmin) Arrangement.End else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAdmin) {
                    // Botón de contacto al usuario si hay teléfono disponible
                    if (user?.phone?.isNotEmpty() == true) {
                        Button(
                            onClick = { onContactWhatsApp(user.phone, user.name, event) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Contactar Usuario")
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    }

                    IconButton(
                        onClick = onEditClick,
                        enabled = event.isProgramado
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar evento",
                            tint = if (event.isProgramado)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = event.isProgramado
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar evento",
                            tint = if (event.isProgramado)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                } else {
                    if (event.isProgramado) {
                        Button(
                            onClick = onAcceptClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Aceptar")
                        }
                        Button(
                            onClick = { onContactWhatsApp("+51993533004", "Administrador", event) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Contactar Admin")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventList(
    events: List<Event>,
    clients: List<Client>,
    users: Map<String, UserData>,
    isAdmin: Boolean,
    onEditClick: (Event) -> Unit,
    onDeleteClick: (Event) -> Unit,
    onAcceptClick: (Event) -> Unit,
    onContactWhatsApp: (String, String, Event) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        events.forEach { event ->
            val client = clients.find { it.documentName == event.clientDocName }
            val user = users[event.createdByUserId]

            EventCard(
                event = event,
                client = client,
                user = user,
                isAdmin = isAdmin,
                onEditClick = { onEditClick(event) },
                onDeleteClick = { onDeleteClick(event) },
                onAcceptClick = { onAcceptClick(event) },
                onContactWhatsApp = onContactWhatsApp
            )
        }
    }
}