package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.components.BackButton
import com.pqsolutions.hdd_monitor.presentation.components.LoadingContent
import com.pqsolutions.hdd_monitor.presentation.components.RefreshableContent
import com.pqsolutions.hdd_monitor.presentation.components.ScreenContent
import com.pqsolutions.hdd_monitor.presentation.components.ScreenHeader
import com.pqsolutions.hdd_monitor.presentation.components.UserCard
import com.pqsolutions.hdd_monitor.presentation.components.UserDialog
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.UserManagementViewModel

@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    hasPendingNotifications: Boolean,
    onNotificationClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val showEmptyState by remember {
        derivedStateOf { uiState.users.isEmpty() && !uiState.isLoading && uiState.error == null }
    }

    ScreenContent(
        header = {
            ScreenHeader(
                title = stringResource(R.string.manage_users),
                hasPendingNotifications = hasPendingNotifications,
                onNotificationClick = onNotificationClick,
                showNotificationBell = true
            )
        },
        content = {
            RefreshableContent(
                isRefreshing = uiState.isLoading,
                onRefresh = { /* Implementar la función de refresh */ },
                listState = listState
            ) {
                LoadingContent(
                    isLoading = uiState.isLoading,
                    isEmpty = showEmptyState,
                    error = uiState.error,
                    onRetry = { /* Implementar función de retry */ },
                    emptyContent = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_data_available),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = uiState.users,
                            key = { user -> user.documentName }  // Cambiado de ID a documentName
                        ) { user ->
                            UserCard(
                                user = user,
                                onEditClick = {
                                    performHapticFeedback(context)
                                    playSoundEffect(context, R.raw.button_click)
                                    viewModel.showEditDialog(user)
                                },
                                onDeleteClick = {
                                    performHapticFeedback(context)
                                    playSoundEffect(context, R.raw.button_click)
                                    viewModel.deleteUser(user)
                                }
                            )
                        }
                    }
                }
            }
        },
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        performHapticFeedback(context)
                        playSoundEffect(context, R.raw.button_click)
                        viewModel.showCreateDialog()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(stringResource(R.string.create))
                }
                BackButton(onBackClick = onBackClick)
            }
        }
    )

    // Diálogo de usuario
    if (uiState.showDialog) {
        UserDialog(
            user = uiState.selectedUser,
            onDismiss = {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                viewModel.dismissDialog()
            },
            onConfirm = { userData ->
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                if (userData.documentName.isEmpty()) {
                    viewModel.createUser(userData)
                } else {
                    viewModel.updateUser(userData)
                }
            }
        )
    }
}