package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.AuthRepository
import com.pqsolutions.hdd_monitor.data.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.Login -> login(event.email, event.password)
            is MainUiEvent.Logout -> logout()
        }
    }

    private fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Attempting login for email: $email")
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                authRepository.login(email, password).fold(
                    onSuccess = { user ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            userData = user,
                            currentRoute = "dashboard"
                        )
                        Log.d("MainViewModel", "Login successful for user: ${user.name}")
                    },
                    onFailure = { e ->
                        Log.e("MainViewModel", "Login failed: ${e.message}", e)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Unknown error occurred"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Network error during login: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            Log.d("MainViewModel", "Attempting logout")
            try {
                authRepository.logout()
                _uiState.value = MainUiState(currentRoute = "login")
                Log.d("MainViewModel", "Logout successful")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Logout failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message ?: "Logout failed")
            }
        }
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userData: UserData? = null,
    val error: String? = null,
    val currentRoute: String = "login"
)

sealed class MainUiEvent {
    data class Login(val email: String, val password: String) : MainUiEvent()
    object Logout : MainUiEvent()
}