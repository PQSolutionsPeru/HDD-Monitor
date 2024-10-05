package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.components.DashboardButton
import com.pqsolutions.hdd_monitor.presentation.components.PanelItem
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell
import com.pqsolutions.hdd_monitor.presentation.components.LogoutButton
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect

private const val TAG = "UserDashboardScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    hasPendingNotifications: Boolean,
    onBackPressed: () -> Unit
) {
    Log.d(TAG, "Composing UserDashboardScreen, hasPendingNotifications: $hasPendingNotifications")

    BackHandler(onBack = onBackPressed)

    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current

        HandleLifecycleEvents(viewModel)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.user_dashboard_title)) },
                    actions = {
                        AnimatedNotificationBell(
                            hasNewNotifications = hasPendingNotifications,
                            onClick = {
                                Log.d(TAG, "Notification bell clicked")
                                onViewAlertsClick()
                            },
                            modifier = Modifier.size(48.dp)
                        )
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    DashboardActions(
                        onViewEventHistoryClick = onViewEventHistoryClick,
                        onViewAlertsClick = onViewAlertsClick,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                when {
                    uiState.isLoading -> {
                        item {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentSize()
                            )
                        }
                    }
                    uiState.error != null -> {
                        item {
                            Text(
                                text = uiState.error ?: stringResource(R.string.unknown_error),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    uiState.panels.isEmpty() -> {
                        item {
                            Text(
                                text = stringResource(R.string.no_panels_found),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    else -> {
                        items(uiState.panels) { panel ->
                            PanelItem(panel)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    LogoutButton(onLogoutClick, context)
                }
            }
        }
    }

    Log.d(TAG, "UserDashboardScreen composition completed")
}

@Composable
private fun HandleLifecycleEvents(viewModel: DashboardViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "ON_RESUME: Refreshing panels")
                    viewModel.refreshPanels()
                }
                else -> {} // Do nothing for other events
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun DashboardActions(
    onViewEventHistoryClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    context: android.content.Context
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        DashboardButton(
            onClick = {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onViewEventHistoryClick()
            },
            text = stringResource(R.string.view_event_history)
        )
        Spacer(modifier = Modifier.height(16.dp))
        DashboardButton(
            onClick = {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onViewAlertsClick()
            },
            text = stringResource(R.string.view_alerts)
        )
    }
}