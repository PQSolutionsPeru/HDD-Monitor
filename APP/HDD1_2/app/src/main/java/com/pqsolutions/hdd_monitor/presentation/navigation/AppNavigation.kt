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
import com.pqsolutions.hdd_monitor.presentation.screens.BleConfigScreen
import com.pqsolutions.hdd_monitor.presentation.screens.ClientManagementScreen
import com.pqsolutions.hdd_monitor.presentation.screens.EventScreen
import com.pqsolutions.hdd_monitor.presentation.screens.LoginScreen
import com.pqsolutions.hdd_monitor.presentation.screens.NotificationHistoryScreen
import com.pqsolutions.hdd_monitor.presentation.screens.OnboardingScreen
import com.pqsolutions.hdd_monitor.presentation.screens.UserDashboardScreen
import com.pqsolutions.hdd_monitor.presentation.state.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.state.MainUiState
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel

private const val TAG = "AppNavigation"

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object ClientManagement : Screen("client_management")
    object NotificationHistory : Screen("notification_history")
    object Events : Screen("events")
    object BleConfig : Screen("ble_config")
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    startDestination: String = Screen.Login.route
) {
    Log.d(TAG, "Starting AppNavigation composition")
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val hasPendingNotifications by viewModel.hasPendingNotifications.collectAsState()

    NavHost(
        navController = navController,
        startDestination = getStartDestination(uiState)
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    viewModel.onEvent(MainUiEvent.FinishOnboarding)
                    navController.navigateToLogin()
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginClick = { email, password ->
                    Log.d(TAG, "Login attempt with email: $email")
                    viewModel.onEvent(MainUiEvent.Login(email, password))
                }
            )
        }

        composable(Screen.Dashboard.route) {
            LaunchedEffect(Unit) {
                handleDeepLink(navController, viewModel)
            }

            Log.d(TAG, "Navigating to Dashboard. User role: ${uiState.userData?.role}")
            when (uiState.userData?.role) {
                UserRole.ADMIN -> {
                    AdminDashboardScreen(
                        onLogoutClick = { handleLogout(viewModel) },
                        onManageUsersClick = { navController.navigate(Screen.ClientManagement.route) },
                        onViewEventsClick = { navController.navigate(Screen.Events.route) },
                        onViewNotificationHistoryClick = { navController.navigate(Screen.NotificationHistory.route) },
                        onConfigureEsp32Click = { navController.navigate(Screen.BleConfig.route) },
                        hasPendingNotifications = hasPendingNotifications
                    )
                }
                UserRole.USER -> {
                    UserDashboardScreen(
                        onLogoutClick = { handleLogout(viewModel) },
                        onViewNotificationHistoryClick = { navController.navigate(Screen.NotificationHistory.route) },
                        onViewEventsClick = { navController.navigate(Screen.Events.route) },
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

        composable(Screen.ClientManagement.route) {
            ClientManagementScreen(
                onBackClick = { navController.popBackStack() },
                hasPendingNotifications = hasPendingNotifications,
                onNotificationClick = { navController.navigate(Screen.Events.route) }
            )
        }

        composable(Screen.NotificationHistory.route) {
            NotificationHistoryScreen(
                onBackClick = { navController.popBackStack() },
                hasPendingNotifications = hasPendingNotifications,
                onNotificationClick = { navController.navigate(Screen.Events.route) }
            )
        }

        composable(Screen.Events.route) {
            EventScreen(
                onBackClick = { navController.popBackStack() },
                isAdmin = uiState.userData?.role == UserRole.ADMIN,
                hasPendingNotifications = hasPendingNotifications
            )
        }

        composable(Screen.BleConfig.route) {
            BleConfigScreen(
                onConfigurationComplete = { navController.navigate(Screen.Dashboard.route) },
                onBackClick = { navController.popBackStack() }
            )
        }
    }

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
        uiState.isFirstLaunch -> Screen.Onboarding.route
        uiState.isLoggedIn -> Screen.Dashboard.route
        else -> Screen.Login.route
    }
}

private fun handleLogout(viewModel: MainViewModel) {
    Log.d(TAG, "Logout clicked")
    viewModel.onEvent(MainUiEvent.Logout)
}

private fun NavHostController.navigateToLogin() {
    navigate(Screen.Login.route) {
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
                    navController.navigate(Screen.Dashboard.route)
                }
            }
            "event" -> navController.navigate(Screen.Events.route)
            "notification" -> navController.navigate(Screen.NotificationHistory.route)
        }
    }
}