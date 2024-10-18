package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.data.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = userRepository.getUsers()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                users = result.getOrNull() ?: emptyList(),
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun createUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = userRepository.createUser(userData)
            if (result.isSuccess) {
                loadUsers()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun updateUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = userRepository.updateUser(userData)
            if (result.isSuccess) {
                loadUsers()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun deleteUser(userData: UserData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Ajusta la llamada al método deleteUser pasando solo userId y clientId
            val result = userRepository.deleteUser(userData.id, userData.clientId)
            if (result.isSuccess) {
                loadUsers()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }
}

data class UserManagementUiState(
    val isLoading: Boolean = false,
    val users: List<UserData> = emptyList(),
    val error: String? = null
)