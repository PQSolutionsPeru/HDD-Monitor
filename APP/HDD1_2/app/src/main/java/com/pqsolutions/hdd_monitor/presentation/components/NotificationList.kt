package com.pqsolutions.hdd_monitor.presentation.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import com.pqsolutions.hdd_monitor.data.Alert
import com.pqsolutions.hdd_monitor.presentation.screens.AlertItem

@Composable
fun NotificationList(notifications: List<Alert>) {
    LazyColumn {
        items(notifications) { notification ->
            AlertItem(
                alert = notification,
                isAdmin = false, // Las notificaciones no son editables en esta vista
                onEditClick = { }, // No se permite editar
                onDeleteClick = { } // No se permite eliminar
            )
        }
    }
}