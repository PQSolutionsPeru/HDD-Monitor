package com.pqsolutions.hdd_monitor.presentation.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.data.AlertRepository
import com.pqsolutions.hdd_monitor.data.AuthRepository
import com.pqsolutions.hdd_monitor.data.Message
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.data.UserPreferences
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.service.HddFirebaseMessagingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val panelRepository: PanelRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _hasPendingNotifications = MutableStateFlow(false)
    val hasPendingNotifications: StateFlow<Boolean> = _hasPendingNotifications.asStateFlow()

    private val panelUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                HddFirebaseMessagingService.PANEL_UPDATE_ACTION -> {
                    val clientId = intent.getStringExtra("clientId")
                    val panelId = intent.getStringExtra("panelId")
                    val relayName = intent.getStringExtra("relayName")
                    val relayStatus = intent.getStringExtra("relayStatus")
                    if (clientId != null && panelId != null && relayName != null && relayStatus != null) {
                        updateRelay(clientId, panelId, relayName, relayStatus)
                    }
                }
            }
        }
    }

    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            combineUserPreferences().collect { state ->
                _uiState.value = state
                Log.d(TAG, "UI State updated: $state")
                if (state.isLoggedIn) {
                    checkPendingNotifications()
                }
            }
        }
        checkAuthState()
        registerPanelUpdateReceiver()
        subscribeToTopic()
    }

    private fun combineUserPreferences() = combine(
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
    }

    private fun registerPanelUpdateReceiver() {
        LocalBroadcastManager.getInstance(context).registerReceiver(
            panelUpdateReceiver,
            IntentFilter(HddFirebaseMessagingService.PANEL_UPDATE_ACTION)
        )
    }

    private fun subscribeToTopic() {
        viewModelScope.launch {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("relay-status").await()
                Log.d(TAG, "Subscribed to relay-status topic")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to relay-status topic", e)
            }
        }
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            Log.d(TAG, "Current user: $currentUser")
            if (currentUser != null) {
                userPreferences.setUserData(currentUser)
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = true,
                    userData = currentUser,
                    error = null
                )
                checkPendingNotifications()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = false,
                    userData = null,
                    error = null
                )
            }
        }
    }

    private fun checkPendingNotifications() {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            Log.d(TAG, "Checking pending notifications for user: ${currentUser?.id}")
            if (currentUser != null) {
                alertRepository.getAlertsFlow(currentUser.clientId)
                    .distinctUntilChanged()
                    .collect { alerts ->
                        val hasPending = alerts.any { it.status == "PROGRAMADO" }
                        if (hasPending != _hasPendingNotifications.value) {
                            _hasPendingNotifications.value = hasPending
                            Log.d(TAG, "Pending notifications updated: $hasPending")
                        }
                    }
            }
        }
    }

    fun updateRelay(clientId: String, panelId: String, relayName: String, relayStatus: String) {
        viewModelScope.launch {
            try {
                panelRepository.updateRelayStatus(clientId, panelId, relayName, relayStatus)
                Log.d(TAG, "Relay updated successfully: Panel=$panelId, Relay=$relayName, Status=$relayStatus")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating relay: ${e.message}", e)
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
                Log.d(TAG, "Attempting login for email: $email")
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
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
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoggedIn = true,
            userData = user,
            error = null
        )
        Log.d(TAG, "Login successful for user: ${user.name}")
        checkPendingNotifications()
    }

    private fun handleLoginFailure(e: Throwable) {
        Log.e(TAG, "Login failed: ${e.message}", e)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = e.message ?: "Unknown error occurred"
        )
    }

    private fun handleNetworkError(e: Exception) {
        Log.e(TAG, "Network error during login: ${e.message}", e)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "Network error: ${e.message}"
        )
    }

    private fun logout() {
        viewModelScope.launch {
            Log.d(TAG, "Attempting logout")
            try {
                authRepository.logout()
                userPreferences.clearUserData()
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = false,
                    userData = null,
                    error = null
                )
                _hasPendingNotifications.value = false
                Log.d(TAG, "Logout successful")
            } catch (e: Exception) {
                Log.e(TAG, "Logout failed: ${e.message}", e)
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

    private suspend fun updateFCMToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                userRepository.updateUserToken(currentUser.id, token)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FCM token: ${e.message}")
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
                Log.e(TAG, "Error sending message: ${e.message}")
            }
        }
    }

    fun handleNotificationNavigation(notificationType: String?, panelId: String?, relayName: String?) {
        when (notificationType) {
            "relay_update" -> {
                // Navegar a la pantalla principal
                _uiState.value = _uiState.value.copy(currentRoute = "dashboard")
            }
            "alert" -> {
                // Navegar a la pantalla de alertas
                _uiState.value = _uiState.value.copy(currentRoute = "alerts")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(panelUpdateReceiver)
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