package com.pqsolutions.hdd_monitor.presentation.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.presentation.screens.*
import com.pqsolutions.hdd_monitor.presentation.state.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.state.MainUiState
import com.pqsolutions.hdd_monitor.presentation.viewmodel.DashboardViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.NotificationViewModel

private const val TAG = "AppNavigation"

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object ClientManagement : Screen("client_management")
    object NotificationHistory : Screen("notification_history")
    object Events : Screen("events")
    object BleConfig : Screen("ble_config")

    companion object {
        fun eventDetail(eventId: String) = "events/$eventId"
        fun panelDetail(panelId: String) = "dashboard?panelId=$panelId"
    }
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

    val dashboardViewModel = hiltViewModel<DashboardViewModel>()
    val notificationViewModel = hiltViewModel<NotificationViewModel>()

    NavHost(
        navController = navController,
        startDestination = getStartDestination(uiState)
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    viewModel.onEvent(MainUiEvent.FinishOnboarding)
                    safeNavigateToLogin(navController)
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

        composable(
            route = "${Screen.Dashboard.route}?panelId={panelId}",
            arguments = listOf(
                navArgument("panelId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val panelId = entry.arguments?.getString("panelId")

            LaunchedEffect(Unit) {
                handleDeepLink(navController, viewModel)
            }

            Log.d(TAG, "Navigating to Dashboard. User role: ${uiState.userData?.role}")
            when (uiState.userData?.role) {
                UserRole.ADMIN -> {
                    AdminDashboardScreen(
                        viewModel = dashboardViewModel,
                        notificationViewModel = notificationViewModel,
                        onLogoutClick = { handleLogout(viewModel) },
                        onManageUsersClick = { safeNavigate(navController, Screen.ClientManagement.route) },
                        onViewEventsClick = { safeNavigate(navController, Screen.Events.route) },
                        onViewNotificationHistoryClick = { safeNavigate(navController, Screen.NotificationHistory.route) },
                        onConfigureEsp32Click = { safeNavigate(navController, Screen.BleConfig.route) },
                        hasPendingNotifications = hasPendingNotifications,
                        selectedPanelId = panelId
                    )
                }
                UserRole.USER -> {
                    UserDashboardScreen(
                        viewModel = dashboardViewModel,
                        notificationViewModel = notificationViewModel,
                        onLogoutClick = { handleLogout(viewModel) },
                        onViewNotificationHistoryClick = { safeNavigate(navController, Screen.NotificationHistory.route) },
                        onViewEventsClick = { safeNavigate(navController, Screen.Events.route) },
                        hasPendingNotifications = hasPendingNotifications,
                        selectedPanelId = panelId
                    )
                }
                null -> {
                    Log.d(TAG, "Invalid user role, navigating to Login")
                    LaunchedEffect(Unit) {
                        safeNavigateToLogin(navController)
                    }
                }
            }
        }

        composable(Screen.ClientManagement.route) {
            ClientManagementScreen(
                onBackClick = { safeNavigateBack(navController) },
                hasPendingNotifications = hasPendingNotifications,
                onNotificationClick = { safeNavigate(navController, Screen.Events.route) }
            )
        }

        composable(Screen.NotificationHistory.route) {
            NotificationHistoryScreen(
                notificationViewModel = notificationViewModel,
                onBackClick = { safeNavigateBack(navController) },
                hasPendingNotifications = hasPendingNotifications,
                onNavigateToEvent = { eventId ->
                    navController.navigate(Screen.eventDetail(eventId))
                },
                onNavigateToPanel = { panelId ->
                    navController.navigate(Screen.panelDetail(panelId))
                }
            )
        }

        // Ruta general de eventos
        composable(Screen.Events.route) {
            EventScreen(
                onBackClick = { safeNavigateBack(navController) },
                isAdmin = uiState.userData?.role == UserRole.ADMIN,
                hasPendingNotifications = hasPendingNotifications
            )
        }

        // Ruta para eventos específicos
        composable(
            route = "${Screen.Events.route}/{eventId}",
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId")
            EventScreen(
                onBackClick = { safeNavigateBack(navController) },
                isAdmin = uiState.userData?.role == UserRole.ADMIN,
                hasPendingNotifications = hasPendingNotifications,
                eventId = eventId
            )
        }

        composable(Screen.BleConfig.route) {
            BleConfigScreen(
                onConfigurationComplete = { safeNavigate(navController, Screen.Dashboard.route) },
                onBackClick = { safeNavigateBack(navController) }
            )
        }
    }

    // Manejar cambios de ruta
    LaunchedEffect(uiState.currentRoute) {
        Log.d(TAG, "LaunchedEffect: Current route changed to ${uiState.currentRoute}")
        val currentRoute = uiState.currentRoute
        val currentDestination = navController.currentDestination?.route

        if (currentRoute != currentDestination && currentRoute.isNotBlank()) {
            try {
                when {
                    currentRoute == Screen.Login.route -> {
                        navController.navigate(currentRoute) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    currentRoute == Screen.Dashboard.route -> {
                        if (currentDestination != Screen.Dashboard.route) {
                            navController.navigate(currentRoute) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    else -> {
                        navController.navigate(currentRoute) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Navigation error: ${e.message}", e)
                try {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback navigation failed: ${e.message}")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Cleaning up ViewModels")
            dashboardViewModel.cancelCurrentJob()
            notificationViewModel.clearError()
        }
    }
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

private fun safeNavigate(navController: NavHostController, route: String) {
    try {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Navigation error to $route: ${e.message}")
    }
}

private fun safeNavigateToLogin(navController: NavHostController) {
    try {
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Navigation error to Login: ${e.message}")
    }
}

private fun safeNavigateBack(navController: NavHostController) {
    try {
        val currentRoute = navController.currentBackStackEntry?.destination?.route

        // Si no hay ruta actual o es la ruta del dashboard, navegamos al dashboard
        if (currentRoute == null || currentRoute == Screen.Dashboard.route) {
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            return
        }

        // Intentamos hacer pop del back stack
        if (!navController.popBackStack()) {
            // Si el pop falla, navegamos al dashboard
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    } catch (e: Exception) {
        Log.e("Navigation", "Error durante la navegación hacia atrás", e)
        // En caso de error, aseguramos que volvemos al dashboard
        try {
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } catch (e: Exception) {
            Log.e("Navigation", "Error en navegación de recuperación", e)
        }
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
                    safeNavigate(navController, Screen.Dashboard.route)
                }
            }
            "event" -> safeNavigate(navController, Screen.Events.route)
            "notification" -> safeNavigate(navController, Screen.NotificationHistory.route)
        }
    }
}