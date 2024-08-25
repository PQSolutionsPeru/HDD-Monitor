package com.pqsolutions.hdd_monitor.presentation.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdminDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogoutClick: () -> Unit,
    onManageUsersClick: () -> Unit,
    onViewAlertsClick: () -> Unit,
    onViewEventHistoryClick: () -> Unit
) {
    Log.d("AdminDashboardScreen", "Composing AdminDashboardScreen")
    HDD1_2Theme {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            Log.d("AdminDashboardScreen", "LaunchedEffect: Loading panels")
            viewModel.loadPanels()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.admin_dashboard_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Button(
                        onClick = {
                            Log.d("AdminDashboardScreen", "Manage Users button clicked")
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onManageUsersClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.manage_users))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            Log.d("AdminDashboardScreen", "View Alerts button clicked")
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewAlertsClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.view_alerts))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            Log.d("AdminDashboardScreen", "View Event History button clicked")
                            performHapticFeedback(context)
                            playSoundEffect(context, R.raw.button_click)
                            onViewEventHistoryClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.view_event_history))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            PanelsList(uiState)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    Log.d("AdminDashboardScreen", "Logout button clicked")
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onLogoutClick()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(stringResource(R.string.logout))
            }
        }
    }
    Log.d("AdminDashboardScreen", "AdminDashboardScreen composition completed")
}