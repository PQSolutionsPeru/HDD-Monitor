package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.data.UserRole
import com.pqsolutions.hdd_monitor.presentation.viewmodel.UserManagementViewModel

@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserData?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "User Management",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
            }
            uiState.error != null -> {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                LazyColumn {
                    items(uiState.users) { user ->
                        UserItem(
                            user = user,
                            onEditClick = {
                                editingUser = user
                                showDialog = true
                            },
                            onDeleteClick = { viewModel.deleteUser(user.id) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                editingUser = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create New User")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }

    if (showDialog) {
        UserDialog(
            user = editingUser,
            onDismiss = { showDialog = false },
            onConfirm = { userData ->
                if (editingUser == null) {
                    viewModel.createUser(userData)
                } else {
                    viewModel.updateUser(userData)
                }
                showDialog = false
            }
        )
    }
}

@Composable
fun UserItem(
    user: UserData,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = user.name, style = MaterialTheme.typography.bodyLarge)
                Text(text = user.email, style = MaterialTheme.typography.bodyMedium)
                Text(text = "Role: ${user.role}", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit User")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete User")
                }
            }
        }
    }
}

@Composable
fun UserDialog(
    user: UserData? = null,
    onDismiss: () -> Unit,
    onConfirm: (UserData) -> Unit
) {
    var name by remember { mutableStateOf(user?.name ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var role by remember { mutableStateOf(user?.role ?: UserRole.USER) }
    var clientId by remember { mutableStateOf(user?.clientId ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) "Add User" else "Edit User") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Role:")
                    RadioButton(
                        selected = role == UserRole.USER,
                        onClick = { role = UserRole.USER }
                    )
                    Text("User")
                    RadioButton(
                        selected = role == UserRole.ADMIN,
                        onClick = { role = UserRole.ADMIN }
                    )
                    Text("Admin")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(UserData(
                    id = user?.id ?: "",
                    name = name,
                    email = email,
                    role = role,
                    clientId = clientId
                ))
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}