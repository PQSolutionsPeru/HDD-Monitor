package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.components.DashboardButton
import com.pqsolutions.hdd_monitor.presentation.components.PanelsList
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell

private const val TAG = "UserDashboardScreen"

@Composable
fun UserDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    hasPendingNotifications: Boolean
) {
    Log.d(TAG, "Composing UserDashboardScreen, hasPendingNotifications: $hasPendingNotifications")

    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current
        val scrollState = rememberScrollState()

        HandleLifecycleEvents(viewModel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            DashboardHeader(
                hasPendingNotifications = hasPendingNotifications,
                onViewAlertsClick = onViewAlertsClick
            )
            Spacer(modifier = Modifier.height(32.dp))
            DashboardActions(
                onViewEventHistoryClick = onViewEventHistoryClick,
                onViewAlertsClick = onViewAlertsClick,
                context = context
            )
            Spacer(modifier = Modifier.height(32.dp))
            PanelsList(uiState)
            Spacer(modifier = Modifier.height(32.dp))
            LogoutButton(onLogoutClick, context)
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
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "ON_PAUSE: Cancelling current job")
                    viewModel.cancelCurrentJob()
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
private fun DashboardHeader(
    hasPendingNotifications: Boolean,
    onViewAlertsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.user_dashboard_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        AnimatedNotificationBell(
            hasNewNotifications = hasPendingNotifications,
            onClick = {
                Log.d(TAG, "Notification bell clicked")
                onViewAlertsClick()
            },
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun DashboardActions(
    onViewEventHistoryClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    context: android.content.Context
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
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
}

@Composable
private fun LogoutButton(onLogoutClick: () -> Unit, context: android.content.Context) {
    DashboardButton(
        onClick = {
            performHapticFeedback(context)
            playSoundEffect(context, R.raw.button_click)
            onLogoutClick()
        },
        text = stringResource(R.string.logout),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    )
}