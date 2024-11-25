package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.components.*
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel

private const val TAG = "AdminDashboardScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onManageUsersClick: () -> Unit,
    onViewEventsClick: () -> Unit,
    onViewNotificationHistoryClick: () -> Unit,
    hasPendingNotifications: Boolean
) {
    Log.d(TAG, "AdminDashboardScreen composition started")

    val uiState by viewModel.uiState.collectAsState()
    val notificationUiState by notificationViewModel.uiState.collectAsState()
    val context = LocalContext.current

    HDD1_2Theme {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = stringResource(R.string.admin_dashboard_title),
                    navController = navController,
                    actions = {
                        AnimatedNotificationBell(
                            hasNewNotifications = hasPendingNotifications,
                            notificationCount = notificationUiState.pendingCount,
                            onClick = onViewEventsClick,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                )
            },
            bottomBar = {
                DashboardActionButton(
                    onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        onLogoutClick()
                    },
                    text = stringResource(R.string.logout),
                    modifier = Modifier.padding(16.dp)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Botones de acción fijos
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    DashboardActionButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onManageUsersClick()
                        },
                        text = stringResource(R.string.manage_clients)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DashboardActionButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewEventsClick()
                        },
                        text = stringResource(R.string.view_events)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DashboardActionButton(
                        onClick = {
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewNotificationHistoryClick()
                        },
                        text = stringResource(R.string.view_notification_history)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lista de paneles agrupados por cliente
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    uiState.groupedPanels.forEach { (clientDocName, clientData) ->
                        val (clientName, panels) = clientData

                        item(key = "header_$clientDocName") {
                            Text(
                                text = clientName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        item {
                            PanelList(
                                panels = panels,
                                onPanelSelect = { panel ->
                                    // TODO: Implementar navegación a detalle del panel
                                    Log.d(TAG, "Panel seleccionado: ${panel.name}")
                                },
                                onEditPanel = { panel ->
                                    // TODO: Implementar edición del panel
                                    Log.d(TAG, "Editar panel: ${panel.name}")
                                },
                                onDeletePanel = { panel ->
                                    // TODO: Implementar eliminación del panel
                                    Log.d(TAG, "Eliminar panel: ${panel.name}")
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}