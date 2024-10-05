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
            userRepository.getUsers()
                .onSuccess { users ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            users = users,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error occurred"
                        )
                    }
                }
        }
    }

    fun createUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userRepository.createUser(userData)
                .onSuccess { loadUsers() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to create user"
                        )
                    }
                }
        }
    }

    fun updateUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userRepository.updateUser(userData)
                .onSuccess { loadUsers() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to update user"
                        )
                    }
                }
        }
    }

    fun deleteUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userRepository.deleteUser(userData.id, userData.clientId)
                .onSuccess { loadUsers() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to delete user"
                        )
                    }
                }
        }
    }
}

data class UserManagementUiState(
    val isLoading: Boolean = false,
    val users: List<UserData> = emptyList(),
    val error: String? = null
)