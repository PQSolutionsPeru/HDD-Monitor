package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pqsolutions.hdd_monitor.data.AuthRepository
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPreferences.isFirstLaunchFlow,
                userPreferences.userDataFlow,
                userPreferences.themeFlow,
                userPreferences.languageFlow,
                userPreferences.notificationsEnabledFlow
            ) { isFirstLaunch, userData, theme, language, notificationsEnabled ->
                MainUiState(
                    isFirstLaunch = isFirstLaunch,
                    isLoggedIn = userData != null,
                    userData = userData,
                    theme = theme,
                    language = language,
                    notificationsEnabled = notificationsEnabled,
                    currentRoute = if (isFirstLaunch) "onboarding" else if (userData != null) "dashboard" else "login"
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.Login -> login(event.email, event.password)
            is MainUiEvent.Logout -> logout()
            is MainUiEvent.FinishOnboarding -> finishOnboarding()
            is MainUiEvent.SetTheme -> setTheme(event.theme)
            is MainUiEvent.SetLanguage -> setLanguage(event.language)
            is MainUiEvent.SetNotificationsEnabled -> setNotificationsEnabled(event.enabled)
        }
    }

    private fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Attempting login for email: $email")
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                authRepository.login(email, password).fold(
                    onSuccess = { user ->
                        userPreferences.setUserData(user)
                        userPreferences.setAuthToken("dummy_token") // Replace with actual token
                        userPreferences.setLastSyncDate(System.currentTimeMillis())
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
                userPreferences.clearUserData()
                Log.d("MainViewModel", "Logout successful")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Logout failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message ?: "Logout failed")
            }
        }
    }

    private fun finishOnboarding() {
        viewModelScope.launch {
            userPreferences.setFirstLaunch(false)
        }
    }

    private fun setTheme(theme: String) {
        viewModelScope.launch {
            userPreferences.setTheme(theme)
        }
    }

    private fun setLanguage(language: String) {
        viewModelScope.launch {
            userPreferences.setLanguage(language)
        }
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setNotificationsEnabled(enabled)
        }
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val userData: UserData? = null,
    val error: String? = null,
    val currentRoute: String = "login",
    val theme: String = "system",
    val language: String = "es",
    val notificationsEnabled: Boolean = true
)

sealed class MainUiEvent {
    data class Login(val email: String, val password: String) : MainUiEvent()
    object Logout : MainUiEvent()
    object FinishOnboarding : MainUiEvent()
    data class SetTheme(val theme: String) : MainUiEvent()
    data class SetLanguage(val language: String) : MainUiEvent()
    data class SetNotificationsEnabled(val enabled: Boolean) : MainUiEvent()
}