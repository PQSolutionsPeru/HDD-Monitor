package com.pqsolutions.hdd_monitor.presentation.navigation

import android.util.Log
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pqsolutions.hdd_monitor.data.UserRole
import com.pqsolutions.hdd_monitor.presentation.screens.*
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import com.pqsolutions.hdd_monitor.presentation.components.NotificationIcon

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    Log.d("AppNavigation", "Starting AppNavigation composition")
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val hasPendingNotifications by viewModel.hasPendingNotifications.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (uiState.isFirstLaunch) "onboarding" else if (uiState.isLoggedIn) "dashboard" else "login"
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onFinish = {
                    viewModel.onEvent(MainUiEvent.FinishOnboarding)
                    navController.navigate("login") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("login") {
            Log.d("AppNavigation", "Navigating to Login Screen")
            LoginScreen(
                onLoginClick = { email, password ->
                    Log.d("AppNavigation", "Login attempt with email: $email")
                    viewModel.onEvent(MainUiEvent.Login(email, password))
                }
            )
        }
        composable("dashboard") {
            Log.d("AppNavigation", "Navigating to Dashboard. User role: ${uiState.userData?.role}")
            when (uiState.userData?.role) {
                UserRole.ADMIN -> AdminDashboardScreen(
                    onLogoutClick = {
                        Log.d("AppNavigation", "Admin logout clicked")
                        viewModel.onEvent(MainUiEvent.Logout)
                    },
                    onManageUsersClick = {
                        Log.d("AppNavigation", "Navigate to User Management")
                        navController.navigate("user_management")
                    },
                    onViewAlertsClick = {
                        Log.d("AppNavigation", "Navigate to Alerts")
                        navController.navigate("alerts")
                    },
                    onViewEventHistoryClick = {
                        Log.d("AppNavigation", "Navigate to Event History")
                        navController.navigate("event_history")
                    },
                    hasPendingNotifications = hasPendingNotifications
                )
                UserRole.USER -> UserDashboardScreen(
                    onLogoutClick = {
                        Log.d("AppNavigation", "User logout clicked")
                        viewModel.onEvent(MainUiEvent.Logout)
                    },
                    onViewEventHistoryClick = {
                        Log.d("AppNavigation", "Navigate to Event History")
                        navController.navigate("event_history")
                    },
                    onViewAlertsClick = {
                        Log.d("AppNavigation", "Navigate to Alerts")
                        navController.navigate("alerts")
                    },
                    hasPendingNotifications = hasPendingNotifications
                )
                else -> {
                    Log.d("AppNavigation", "Invalid user role, navigating to Login")
                    LaunchedEffect(Unit) {
                        navController.navigate("login") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                }
            }
        }
        composable("user_management") {
            Log.d("AppNavigation", "Navigating to User Management Screen")
            UserManagementScreen(
                onBackClick = {
                    Log.d("AppNavigation", "Navigating back from User Management")
                    navController.popBackStack()
                },
                hasPendingNotifications = hasPendingNotifications
            )
        }
        composable("event_history") {
            Log.d("AppNavigation", "Navigating to Event History Screen")
            EventHistoryScreen(
                onBackClick = {
                    Log.d("AppNavigation", "Navigating back from Event History")
                    navController.popBackStack()
                },
                hasPendingNotifications = hasPendingNotifications
            )
        }
        composable("alerts") {
            Log.d("AppNavigation", "Navigating to Alerts Screen")
            AlertScreen(
                onBackClick = {
                    Log.d("AppNavigation", "Navigating back from Alerts")
                    navController.popBackStack()
                },
                isAdmin = uiState.userData?.role == UserRole.ADMIN,
                hasPendingNotifications = hasPendingNotifications
            )
        }
    }

    LaunchedEffect(uiState.currentRoute) {
        Log.d("AppNavigation", "LaunchedEffect: Current route changed to ${uiState.currentRoute}")
        if (uiState.currentRoute != navController.currentDestination?.route) {
            navController.navigate(uiState.currentRoute) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }

    Log.d("AppNavigation", "AppNavigation composition completed")
}