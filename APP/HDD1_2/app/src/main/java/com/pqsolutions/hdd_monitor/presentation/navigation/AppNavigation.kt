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
import com.pqsolutions.hdd_monitor.data.UserRole
import com.pqsolutions.hdd_monitor.presentation.screens.AdminDashboardScreen
import com.pqsolutions.hdd_monitor.presentation.screens.AlertScreen
import com.pqsolutions.hdd_monitor.presentation.screens.EventHistoryScreen
import com.pqsolutions.hdd_monitor.presentation.screens.LoginScreen
import com.pqsolutions.hdd_monitor.presentation.screens.OnboardingScreen
import com.pqsolutions.hdd_monitor.presentation.screens.UserDashboardScreen
import com.pqsolutions.hdd_monitor.presentation.screens.UserManagementScreen
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel

private const val TAG = "AppNavigation"

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    Log.d(TAG, "Starting AppNavigation composition")
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val hasPendingNotifications by viewModel.hasPendingNotifications.collectAsState()

    NavHost(
        navController = navController,
        startDestination = getStartDestination(uiState.isFirstLaunch, uiState.isLoggedIn)
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
                val notificationType = navController.currentBackStackEntry
                    ?.arguments?.getString("notificationType")
                val panelId = navController.currentBackStackEntry
                    ?.arguments?.getString("panelId")
                val relayName = navController.currentBackStackEntry
                    ?.arguments?.getString("relayName")
                viewModel.handleNotificationNavigation(notificationType, panelId, relayName)
            }
            Log.d(TAG, "Navigating to Dashboard. User role: ${uiState.userData?.role}")
            when (uiState.userData?.role) {
                UserRole.ADMIN -> AdminDashboardScreen(
                    onLogoutClick = { handleLogout(viewModel) },
                    onManageUsersClick = { navController.navigate("user_management") },
                    onViewAlertsClick = { navController.navigate("alerts") },
                    onViewEventHistoryClick = { navController.navigate("event_history") },
                    hasPendingNotifications = hasPendingNotifications
                )
                UserRole.USER -> UserDashboardScreen(
                    onLogoutClick = { handleLogout(viewModel) },
                    onViewEventHistoryClick = { navController.navigate("event_history") },
                    onViewAlertsClick = { navController.navigate("alerts") },
                    hasPendingNotifications = hasPendingNotifications
                )
                else -> {
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
                onNotificationClick = { navController.navigate("alerts") }
            )
        }
        composable("event_history") {
            EventHistoryScreen(
                onBackClick = { navController.popBackStack() },
                hasPendingNotifications = hasPendingNotifications,
                onNotificationClick = { navController.navigate("alerts") }
            )
        }
        composable("alerts") {
            AlertScreen(
                onBackClick = { navController.popBackStack() },
                isAdmin = uiState.userData?.role == UserRole.ADMIN,
                hasPendingNotifications = hasPendingNotifications
            )
        }
    }

    LaunchedEffect(uiState.currentRoute) {
        Log.d(TAG, "LaunchedEffect: Current route changed to ${uiState.currentRoute}")
        if (uiState.currentRoute != navController.currentDestination?.route) {
            navController.navigate(uiState.currentRoute) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }

    Log.d(TAG, "AppNavigation composition completed")
}

private fun getStartDestination(isFirstLaunch: Boolean, isLoggedIn: Boolean): String {
    return when {
        isFirstLaunch -> "onboarding"
        isLoggedIn -> "dashboard"
        else -> "login"
    }
}

private fun handleLogout(viewModel: MainViewModel) {
    Log.d(TAG, "Logout clicked")
    viewModel.onEvent(MainUiEvent.Logout)
}

private fun NavHostController.navigateToLogin() {
    navigate("login") {
        popUpTo(graph.startDestinationId) { inclusive = true }
    }
}