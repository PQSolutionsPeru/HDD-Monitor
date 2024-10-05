package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
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
    private val userPreferences: UserPreferences,
    private val alertRepository: AlertRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _hasPendingNotifications = MutableStateFlow(false)
    val hasPendingNotifications: StateFlow<Boolean> = _hasPendingNotifications.asStateFlow()

    private var logoutJob: Job? = null
    private var checkNotificationsJob: Job? = null

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.pqsolutions.hdd_monitor.NEW_ALERT",
                "com.pqsolutions.hdd_monitor.NEW_MESSAGE",
                "com.pqsolutions.hdd_monitor.PANEL_UPDATE" -> {
                    checkPendingNotifications()
                }
            }
        }
    }

    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            userPreferences.isFirstLaunchFlow.collect { isFirstLaunch ->
                _uiState.update { it.copy(isFirstLaunch = isFirstLaunch) }
            }
        }
        viewModelScope.launch {
            userPreferences.userDataFlow.collect { userData ->
                updateUiState(userData)
            }
        }
        checkAuthState()
        registerNotificationReceiver()
    }

    private fun updateUiState(userData: UserData?) {
        _uiState.update { currentState ->
            currentState.copy(
                isLoggedIn = userData != null,
                userData = userData,
                currentRoute = when {
                    currentState.isFirstLaunch -> "onboarding"
                    userData != null -> "dashboard"
                    else -> "login"
                }
            )
        }
        if (userData != null) {
            checkPendingNotifications()
        }
    }

    private fun registerNotificationReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.pqsolutions.hdd_monitor.NEW_ALERT")
            addAction("com.pqsolutions.hdd_monitor.NEW_MESSAGE")
            addAction("com.pqsolutions.hdd_monitor.PANEL_UPDATE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(notificationReceiver, filter)
        }
    }

    fun checkAuthState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val currentUser = userRepository.getCurrentUser()
            Log.d(TAG, "Current user: $currentUser")
            if (currentUser != null) {
                userPreferences.setUserData(currentUser)
                val isFirstLaunch = userPreferences.isFirstLaunchFlow.first()
                _uiState.update { it.copy(
                    isLoggedIn = true,
                    userData = currentUser,
                    isFirstLaunch = isFirstLaunch,
                    currentRoute = if (isFirstLaunch) "onboarding" else "dashboard"
                ) }
            } else {
                userPreferences.clearUserData()
                _uiState.update { it.copy(
                    isLoggedIn = false,
                    userData = null,
                    currentRoute = "login"
                ) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun checkPendingNotifications() {
        checkNotificationsJob?.cancel()
        checkNotificationsJob = viewModelScope.launch {
            userRepository.getCurrentUser()?.let { currentUser ->
                Log.d(TAG, "Checking pending notifications for user: ${currentUser.id}")
                alertRepository.getAlertsFlow(currentUser.clientId)
                    .distinctUntilChanged()
                    .collect { alerts ->
                        val hasPending = alerts.any { it.status == "PROGRAMADO" }
                        _hasPendingNotifications.value = hasPending
                        Log.d(TAG, "Pending notifications updated: $hasPending")
                    }
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
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password cannot be empty") }
            return
        }
        viewModelScope.launch {
            try {
                Log.d(TAG, "Attempting login for email: $email")
                _uiState.update { it.copy(isLoading = true, error = null) }
                authRepository.login(email, password).fold(
                    onSuccess = { user ->
                        handleLoginSuccess(user)
                    },
                    onFailure = { e ->
                        handleLoginFailure(e)
                    }
                )
            } catch (e: Exception) {
                handleNetworkError(e)
            }
        }
    }

    private suspend fun handleLoginSuccess(user: UserData) {
        userPreferences.setUserData(user)
        updateFCMToken()
        val isFirstLaunch = userPreferences.isFirstLaunchFlow.first()
        _uiState.update { it.copy(
            isLoading = false,
            isLoggedIn = true,
            userData = user,
            error = null,
            isFirstLaunch = isFirstLaunch,
            currentRoute = if (isFirstLaunch) "onboarding" else "dashboard"
        ) }
        Log.d(TAG, "Login successful for user: ${user.name}")
        checkPendingNotifications()
    }

    private fun handleLoginFailure(e: Throwable) {
        Log.e(TAG, "Login failed: ${e.message}", e)
        _uiState.update { it.copy(
            isLoading = false,
            error = e.message ?: "Unknown error occurred"
        ) }
    }

    private fun handleNetworkError(e: Exception) {
        Log.e(TAG, "Network error during login: ${e.message}", e)
        _uiState.update { it.copy(
            isLoading = false,
            error = "Network error: ${e.message}"
        ) }
    }

    private fun logout() {
        logoutJob?.cancel()
        logoutJob = viewModelScope.launch {
            Log.d(TAG, "Attempting logout")
            try {
                _uiState.update { it.copy(isLoading = true, isLoggingOut = true) }
                authRepository.logout()
                userPreferences.clearUserData()
                _hasPendingNotifications.value = false
                Log.d(TAG, "Logout successful")
            } catch (e: Exception) {
                Log.e(TAG, "Logout failed: ${e.message}", e)
                _uiState.update { it.copy(error = e.message ?: "Logout failed") }
            } finally {
                _uiState.update { it.copy(
                    isLoading = false,
                    isLoggedIn = false,
                    userData = null,
                    isLoggingOut = false,
                    currentRoute = "login"
                ) }
            }
        }
    }

    private fun finishOnboarding() {
        viewModelScope.launch {
            userPreferences.setFirstLaunch(false)
            _uiState.update { it.copy(
                isFirstLaunch = false,
                currentRoute = "dashboard"
            ) }
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

    private suspend fun updateFCMToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            userRepository.getCurrentUser()?.let { currentUser ->
                userRepository.updateUserToken(currentUser.id, token)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FCM token: ${e.message}")
        }
    }

    fun sendMessage(subject: String, content: String) {
        viewModelScope.launch {
            try {
                userRepository.getCurrentUser()?.let { currentUser ->
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val message = Message(currentUser.id, currentUser.clientId, content, subject, timestamp)
                    userRepository.addMessage(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        logoutJob?.cancel()
        checkNotificationsJob?.cancel()
        try {
            context.unregisterReceiver(notificationReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
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
    val notificationsEnabled: Boolean = true,
    val isLoggingOut: Boolean = false
)

sealed class MainUiEvent {
    data class Login(val email: String, val password: String) : MainUiEvent()
    object Logout : MainUiEvent()
    object FinishOnboarding : MainUiEvent()
    data class SetTheme(val theme: String) : MainUiEvent()
    data class SetLanguage(val language: String) : MainUiEvent()
    data class SetNotificationsEnabled(val enabled: Boolean) : MainUiEvent()
}