package com.pqsolutions.hdd_monitor.presentation.components.events

data class EventDialogState(
    val isEditing: Boolean,
    val canEdit: Boolean,
    val title: String,
    val description: String,
    val selectedClients: Set<String> = emptySet(),
    val showNewTypeDialog: Boolean = false
)