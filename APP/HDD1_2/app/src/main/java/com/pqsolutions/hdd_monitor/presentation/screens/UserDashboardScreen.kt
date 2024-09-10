package com.pqsolutions.hdd_monitor.presentation.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.components.DashboardButton
import com.pqsolutions.hdd_monitor.presentation.components.PanelsList
import com.pqsolutions.hdd_monitor.presentation.components.AnimatedNotificationBell

@Composable
fun UserDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    hasPendingNotifications: Boolean
) {
    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.user_dashboard_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                AnimatedNotificationBell(
                    hasNewNotifications = hasPendingNotifications,
                    onClick = onViewAlertsClick
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
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
            Spacer(modifier = Modifier.height(32.dp))
            PanelsList(uiState)
            Spacer(modifier = Modifier.height(32.dp))
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
    }
}