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
import com.pqsolutions.hdd_monitor.data.AuthRepository
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserData
import com.pqsolutions.hdd_monitor.data.UserPreferences
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.presentation.state.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.state.MainUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences,
    private val eventRepository: EventRepository,
    private val panelRepository: PanelRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _hasPendingNotifications = MutableStateFlow(false)
    val hasPendingNotifications: StateFlow<Boolean> = _hasPendingNotifications.asStateFlow()

    private var sessionCheckJob: Job? = null

    companion object {
        private const val TAG = "MainViewModel"
        const val PANEL_UPDATE_ACTION = "com.pqsolutions.hdd_monitor.PANEL_UPDATE"
        const val CLIENT_MANAGEMENT_ROUTE = "client_management" // Nueva constante
    }

    private val panelUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PANEL_UPDATE_ACTION) {
                val clientDocName = intent.getStringExtra("clientDocName")
                val panelDocName = intent.getStringExtra("panelDocName")
                val relayName = intent.getStringExtra("relayName")
                val relayStatus = intent.getStringExtra("relayStatus")
                if (clientDocName != null && panelDocName != null && relayName != null && relayStatus != null) {
                    updateRelay(clientDocName, panelDocName, relayName, relayStatus)
                }
            }
        }
    }

    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            val savedUser = userPreferences.getUserData()
            if (savedUser != null && authRepository.isUserLoggedIn()) {
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = true,
                    userData = savedUser,
                    currentRoute = "dashboard"
                )
                startSessionCheck()
                checkPendingNotifications()
            }

            combineUserPreferences().collect { state ->
                _uiState.value = state
                Log.d(TAG, "UI State updated: $state")
                if (state.isLoggedIn) {
                    checkPendingNotifications()
                }
            }
        }

        registerPanelUpdateReceiver()
        subscribeToTopic()
    }

    private fun startSessionCheck() {
        sessionCheckJob?.cancel()
        sessionCheckJob = viewModelScope.launch {
            while (true) {
                if (!authRepository.isUserLoggedIn()) {
                    handleLogout()
                    break
                }
                kotlinx.coroutines.delay(60000) // Verificar cada minuto
            }
        }
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
            currentRoute = determineRoute(isFirstLaunch, userData)
        )
    }

    private fun determineRoute(isFirstLaunch: Boolean, userData: UserData?): String =
        when {
            isFirstLaunch -> "onboarding"
            userData != null -> "dashboard"
            else -> "login"
        }

    private fun registerPanelUpdateReceiver() {
        LocalBroadcastManager.getInstance(context).registerReceiver(
            panelUpdateReceiver,
            IntentFilter(PANEL_UPDATE_ACTION)
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

    fun updateRelay(clientDocName: String, panelDocName: String, relayName: String, relayStatus: String) {
        viewModelScope.launch {
            try {
                panelRepository.updateRelayStatus(clientDocName, panelDocName, relayName, relayStatus)
                Log.d(TAG, "Relay updated successfully: Panel=$panelDocName, Relay=$relayName, Status=$relayStatus")
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
        startSessionCheck()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoggedIn = true,
            userData = user,
            error = null,
            currentRoute = "dashboard"
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
                // Primero limpiamos los listeners
                eventRepository.clearListeners()

                // Luego procedemos con el logout
                authRepository.logout().fold(
                    onSuccess = {
                        handleLogout()
                        Log.d(TAG, "Logout successful")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Logout failed: ${e.message}", e)
                        _uiState.value = _uiState.value.copy(error = e.message ?: "Logout failed")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Logout failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message ?: "Logout failed")
            }
        }
    }

    private fun handleLogout() {
        viewModelScope.launch {
            userPreferences.clearUserData()
            sessionCheckJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = false,
                userData = null,
                error = null,
                currentRoute = "login"
            )
            _hasPendingNotifications.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionCheckJob?.cancel()
        eventRepository.clearListeners()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(panelUpdateReceiver)
    }

    private fun checkPendingNotifications() {
        viewModelScope.launch {
            uiState.value.userData?.let { user ->
                eventRepository.getEventsFlow(user.clientDocName)
                    .distinctUntilChanged()
                    .collect { events ->
                        val hasPending = events.any { it.status == "PROGRAMADO" }
                        if (hasPending != _hasPendingNotifications.value) {
                            _hasPendingNotifications.value = hasPending
                            Log.d(TAG, "Pending notifications updated: $hasPending")
                        }
                    }
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
                userRepository.updateFcmToken(currentUser.documentName, token)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FCM token: ${e.message}")
        }
    }
}