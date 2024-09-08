package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
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
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            Log.d("MainViewModel", "Current user: $currentUser")
            if (currentUser != null) {
                userPreferences.setUserData(currentUser)
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = true,
                    userData = currentUser,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = false,
                    userData = null,
                    error = null
                )
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
                        updateFCMToken()
                        addUserActivity(user.id, user.clientId, "Inicio de sesión")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            userData = user,
                            error = null
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
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    addUserActivity(currentUser.id, currentUser.clientId, "Cierre de sesión")
                }
                authRepository.logout()
                userPreferences.clearUserData()
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = false,
                    userData = null,
                    error = null
                )
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

    private fun updateFCMToken() {
        viewModelScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    userRepository.updateUserToken(currentUser.id, token)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating FCM token: ${e.message}")
            }
        }
    }

    private fun addUserActivity(userId: String, clientId: String, action: String) {
        viewModelScope.launch {
            try {
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val activity = UserActivity(userId, clientId, timestamp, action)
                userRepository.addUserActivity(activity)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error adding user activity: ${e.message}")
            }
        }
    }

    fun sendMessage(subject: String, content: String) {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val message = Message(currentUser.id, currentUser.clientId, content, subject, timestamp)
                    userRepository.addMessage(message)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error sending message: ${e.message}")
            }
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