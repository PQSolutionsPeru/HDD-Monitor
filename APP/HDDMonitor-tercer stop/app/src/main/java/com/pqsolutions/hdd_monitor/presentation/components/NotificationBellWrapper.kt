package com.pqsolutions.hdd_monitor.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.pqsolutions.hdd_monitor.presentation.navigation.Screen
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch

@Composable
fun NotificationBellWrapper(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val notificationViewModel: NotificationViewModel = hiltViewModel()

    val notificationState by notificationViewModel.combinedNotificationState.collectAsState()
    val pendingEvents by dashboardViewModel.pendingEvents.collectAsState()
    val pendingNotifications by dashboardViewModel.pendingNotifications.collectAsState()

    var showNotificationDialog by remember { mutableStateOf(false) }

    // Manejar los cambios en el ciclo de vida
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    coroutineScope.launch {
                        notificationViewModel.refresh()
                        dashboardViewModel.refreshAll()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Limpiar cualquier diálogo abierto al salir
                    showNotificationDialog = false
                }
                else -> { /* no action needed */ }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Manejar navegación de forma segura
    LaunchedEffect(navController.currentBackStackEntry) {
        // Si volvemos a esta pantalla, asegurarse de que el estado es correcto
        showNotificationDialog = false
    }

    AnimatedNotificationBell(
        hasNewNotifications = notificationState.hasUnread || pendingEvents.isNotEmpty() || pendingNotifications.isNotEmpty(),
        notificationCount = notificationState.totalCount + pendingEvents.size + pendingNotifications.size,
        onClick = { showNotificationDialog = true },
        modifier = modifier
    )

    if (showNotificationDialog) {
        NotificationDialog(
            notificationState = notificationState,
            pendingEvents = pendingEvents,
            pendingNotifications = pendingNotifications,
            onDismiss = {
                showNotificationDialog = false
                coroutineScope.launch {
                    notificationViewModel.markAllAsRead()
                }
            },
            onViewAllEvents = {
                showNotificationDialog = false
                coroutineScope.launch {
                    navController.navigate(Screen.Events.route) {
                        launchSingleTop = true
                    }
                }
            },
            onViewAllNotifications = {
                showNotificationDialog = false
                coroutineScope.launch {
                    navController.navigate(Screen.NotificationHistory.route) {
                        launchSingleTop = true
                    }
                }
            },
            onViewAgenda = {
                showNotificationDialog = false
                navController.navigate(Screen.Agenda.route) {
                    launchSingleTop = true
                }
            }
        )
    }
}