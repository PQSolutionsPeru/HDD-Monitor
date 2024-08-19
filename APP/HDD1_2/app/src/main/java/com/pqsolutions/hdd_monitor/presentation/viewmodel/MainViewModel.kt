package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.AuthRepository
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.domain.NotificationUseCase
import com.pqsolutions.hdd_monitor.data.Alert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationUseCase: NotificationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _notifications = MutableStateFlow<List<Alert>>(emptyList())
    val notifications: StateFlow<List<Alert>> = _notifications.asStateFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUserData()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = currentUser != null,
                userData = currentUser,
                currentRoute = if (currentUser != null) "dashboard" else "login"
            )
            currentUser?.let { user ->
                observeNotifications(user.clientId)
            }
        }
    }

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.Login -> login(event.email, event.password)
            is MainUiEvent.Logout -> logout()
            is MainUiEvent.Navigate -> navigate(event.route)
        }
    }

    private fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepository.login(email, password)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoggedIn = result.isSuccess,
                userData = result.getOrNull(),
                error = result.exceptionOrNull()?.message,
                currentRoute = if (result.isSuccess) "dashboard" else "login"
            )
            result.getOrNull()?.let { user ->
                observeNotifications(user.clientId)
            }
        }
    }

    private fun logout() {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(
            isLoggedIn = false,
            userData = null,
            currentRoute = "login"
        )
        _notifications.value = emptyList()
    }

    private fun navigate(route: String) {
        _uiState.value = _uiState.value.copy(currentRoute = route)
    }

    fun observeNotifications(clientId: String) {
        viewModelScope.launch {
            notificationUseCase.getNotifications(clientId).collect { alerts ->
                _notifications.value = alerts
            }
        }
    }

    fun onNotificationReceived(alert: Alert) {
        viewModelScope.launch {
            notificationUseCase.handleNewNotification(alert)
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
    data class Navigate(val route: String) : MainUiEvent()
}