package com.pqsolutions.hdd_monitor.presentation.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.Notification
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel

@Composable
fun useNotificationHandler(
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel()
): NotificationHandlerState {
    var showNotificationDialog by remember { mutableStateOf(false) }
    val notificationState by notificationViewModel.combinedNotificationState.collectAsState()
    val pendingEvents by dashboardViewModel.pendingEvents.collectAsState()
    val pendingNotifications by dashboardViewModel.pendingNotifications.collectAsState()

    return NotificationHandlerState(
        showDialog = showNotificationDialog,
        notificationState = notificationState,
        pendingEvents = pendingEvents,
        pendingNotifications = pendingNotifications,
        onShowDialog = { showNotificationDialog = true },
        onDismissDialog = { showNotificationDialog = false }
    )
}

data class NotificationHandlerState(
    val showDialog: Boolean,
    val notificationState: CombinedNotificationState,
    val pendingEvents: List<Event>,
    val pendingNotifications: List<Notification>,
    val onShowDialog: () -> Unit,
    val onDismissDialog: () -> Unit
)