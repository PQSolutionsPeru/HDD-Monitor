package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = userRepository.getUsers()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    users = result.getOrNull() ?: emptyList(),
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showDialog = true, selectedUser = null) }
    }

    fun showEditDialog(user: UserData) {
        _uiState.update { it.copy(showDialog = true, selectedUser = user) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showDialog = false, selectedUser = null) }
    }

    fun createUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showDialog = false) }
            val result = userRepository.createUser(userData)
            if (result.isSuccess) {
                loadUsers()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    fun updateUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showDialog = false) }
            val result = userRepository.updateUser(userData)
            if (result.isSuccess) {
                loadUsers()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    fun deleteUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Usar documentName en lugar de ID
            val result = userRepository.deleteUser(userData.documentName, userData.clientDocName)
            if (result.isSuccess) {
                loadUsers()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class UserManagementUiState(
    val isLoading: Boolean = false,
    val users: List<UserData> = emptyList(),
    val error: String? = null,
    val showDialog: Boolean = false,
    val selectedUser: UserData? = null
)