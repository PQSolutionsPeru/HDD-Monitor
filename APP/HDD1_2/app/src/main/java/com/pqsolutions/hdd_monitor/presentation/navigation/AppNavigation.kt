package com.pqsolutions.hdd_monitor.presentation.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pqsolutions.hdd_monitor.data.UserRole
import com.pqsolutions.hdd_monitor.presentation.screens.*
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import com.pqsolutions.hdd_monitor.presentation.components.NotificationList
import com.pqsolutions.hdd_monitor.presentation.util.enterTransition
import com.pqsolutions.hdd_monitor.presentation.util.exitTransition

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigation(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "onboarding"
    ) {
        composable(
            "onboarding",
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() }
        ) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate("login") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable(
            "login",
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() }
        ) {
            LoginScreen(
                onLoginClick = { email, password ->
                    viewModel.onEvent(MainUiEvent.Login(email, password))
                }
            )
        }
        composable(
            "dashboard",
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() }
        ) {
            when (uiState.userData?.role) {
                UserRole.ADMIN -> AdminDashboardScreen(
                    onLogoutClick = {
                        viewModel.onEvent(MainUiEvent.Logout)
                    },
                    onManageUsersClick = {
                        navController.navigate("user_management")
                    },
                    onViewAlertsClick = {
                        navController.navigate("alerts")
                    },
                    onViewEventHistoryClick = {
                        navController.navigate("event_history")
                    }
                )
                UserRole.USER -> UserDashboardScreen(
                    onLogoutClick = {
                        viewModel.onEvent(MainUiEvent.Logout)
                    },
                    onViewEventHistoryClick = {
                        navController.navigate("event_history")
                    },
                    onViewAlertsClick = {
                        navController.navigate("alerts")
                    }
                )
                null -> {
                    LaunchedEffect(Unit) {
                        navController.navigate("login")
                    }
                }
            }
        }
        composable(
            "user_management",
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() }
        ) {
            UserManagementScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            "event_history",
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() }
        ) {
            EventHistoryScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            "alerts",
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() }
        ) {
            AlertScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                isAdmin = uiState.userData?.role == UserRole.ADMIN,
                viewModel = hiltViewModel()
            )
        }
    }

    LaunchedEffect(uiState.currentRoute) {
        navController.navigate(uiState.currentRoute) {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
        }
    }

    // Observar notificaciones cuando el usuario está logueado
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            uiState.userData?.let { userData ->
                viewModel.observeNotifications(userData.clientId)
            }
        }
    }

    // Mostrar notificaciones
    if (uiState.isLoggedIn) {
        val notifications by viewModel.notifications.collectAsState()
        NotificationList(notifications = notifications)
    }
}