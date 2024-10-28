package com.pqsolutions.hdd_monitor.presentation.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.presentation.screens.AdminDashboardScreen
import com.pqsolutions.hdd_monitor.presentation.screens.EventScreen
import com.pqsolutions.hdd_monitor.presentation.screens.LoginScreen
import com.pqsolutions.hdd_monitor.presentation.screens.NotificationHistoryScreen
import com.pqsolutions.hdd_monitor.presentation.screens.OnboardingScreen
import com.pqsolutions.hdd_monitor.presentation.screens.UserDashboardScreen
import com.pqsolutions.hdd_monitor.presentation.screens.UserManagementScreen
import com.pqsolutions.hdd_monitor.presentation.state.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.state.MainUiState
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel

private const val TAG = "AppNavigation"

sealed class NavigationEvent {
    object NavigateToLogin : NavigationEvent()
    object NavigateToDashboard : NavigationEvent()
    object NavigateToEvents : NavigationEvent()
    object NavigateToUserManagement : NavigationEvent()
    object NavigateToNotificationHistory : NavigationEvent()
    object NavigateBack : NavigationEvent()
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    startDestination: String = "login"
) {
    Log.d(TAG, "Starting AppNavigation composition")
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val hasPendingNotifications by viewModel.hasPendingNotifications.collectAsState()

    NavHost(
        navController = navController,
        startDestination = getStartDestination(uiState)
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onFinish = {
                    viewModel.onEvent(MainUiEvent.FinishOnboarding)
                    navController.navigateToLogin()
                }
            )
        }
        composable("login") {
            LoginScreen(
                onLoginClick = { email, password ->
                    Log.d(TAG, "Login attempt with email: $email")
                    viewModel.onEvent(MainUiEvent.Login(email, password))
                }
            )
        }
        composable("dashboard") {
            LaunchedEffect(Unit) {
                handleDeepLink(navController, viewModel)
            }

            Log.d(TAG, "Navigating to Dashboard. User role: ${uiState.userData?.role}")
            when (uiState.userData?.role) {
                UserRole.ADMIN -> {
                    AdminDashboardScreen(
                        onLogoutClick = { handleLogout(viewModel) },
                        onManageUsersClick = { navController.navigate("user_management") },
                        onViewEventsClick = { navController.navigate("events") },
                        onViewNotificationHistoryClick = { navController.navigate("notification_history") },
                        hasPendingNotifications = hasPendingNotifications
                    )
                }
                UserRole.USER -> {
                    UserDashboardScreen(
                        onLogoutClick = { handleLogout(viewModel) },
                        onViewNotificationHistoryClick = { navController.navigate("notification_history") },
                        onViewEventsClick = { navController.navigate("events") },
                        hasPendingNotifications = hasPendingNotifications
                    )
                }
                null -> {
                    Log.d(TAG, "Invalid user role, navigating to Login")
                    LaunchedEffect(Unit) {
                        navController.navigateToLogin()
                    }
                }
            }
        }
        composable("user_management") {
            UserManagementScreen(
                onBackClick = { navController.popBackStack() },
                hasPendingNotifications = hasPendingNotifications,
                onNotificationClick = { navController.navigate("events") }
            )
        }
        composable("notification_history") {
            NotificationHistoryScreen(
                onBackClick = { navController.popBackStack() },
                hasPendingNotifications = hasPendingNotifications,
                onNotificationClick = { navController.navigate("events") }
            )
        }
        composable("events") {
            EventScreen(
                onBackClick = { navController.popBackStack() },
                isAdmin = uiState.userData?.role == UserRole.ADMIN,
                hasPendingNotifications = hasPendingNotifications
            )
        }
    }

    // Manejar cambios de ruta basados en el estado de UI
    LaunchedEffect(uiState.currentRoute) {
        Log.d(TAG, "LaunchedEffect: Current route changed to ${uiState.currentRoute}")
        if (uiState.currentRoute != navController.currentDestination?.route) {
            navController.navigate(uiState.currentRoute) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Log.d(TAG, "AppNavigation composition completed")
}

private fun getStartDestination(uiState: MainUiState): String {
    return when {
        uiState.isFirstLaunch -> "onboarding"
        uiState.isLoggedIn -> "dashboard"
        else -> "login"
    }
}

private fun handleLogout(viewModel: MainViewModel) {
    Log.d(TAG, "Logout clicked")
    viewModel.onEvent(MainUiEvent.Logout)
}

private fun NavHostController.navigateToLogin() {
    navigate("login") {
        popUpTo(graph.startDestinationId) {
            inclusive = true
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private suspend fun handleDeepLink(navController: NavHostController, viewModel: MainViewModel) {
    navController.currentBackStackEntry?.arguments?.let { args ->
        val notificationType = args.getString("notificationType")
        val panelId = args.getString("panelId")
        val relayName = args.getString("relayName")

        when (notificationType) {
            "relay_update" -> {
                if (panelId != null && relayName != null) {
                    // Manejar navegación específica para actualizaciones de relay
                    navController.navigate("dashboard")
                }
            }
            "event" -> navController.navigate("events")
            "notification" -> navController.navigate("notification_history")
        }
    }
}