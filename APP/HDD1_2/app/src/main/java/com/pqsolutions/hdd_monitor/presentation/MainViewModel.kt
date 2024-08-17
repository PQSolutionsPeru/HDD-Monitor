package com.pqsolutions.hdd_monitor.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(isLoggedIn = authRepository.isUserLoggedIn())
    }

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.Login -> login(event.email, event.password)
            is MainUiEvent.Logout -> logout()
        }
    }

    private fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepository.login(email, password)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoggedIn = result.isSuccess,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    private fun logout() {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(isLoggedIn = false)
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

sealed class MainUiEvent {
    data class Login(val email: String, val password: String) : MainUiEvent()
    object Logout : MainUiEvent()
}