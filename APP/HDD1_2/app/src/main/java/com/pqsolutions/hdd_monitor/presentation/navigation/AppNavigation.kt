package com.pqsolutions.hdd_monitor.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pqsolutions.hdd_monitor.data.UserRole
import com.pqsolutions.hdd_monitor.presentation.screens.*
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.LoginViewModel

@Composable
fun AppNavigation(mainViewModel: MainViewModel, loginViewModel: LoginViewModel) {
    val uiState by mainViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val hasPendingNotifications by mainViewModel.hasPendingNotifications.collectAsState()

    LaunchedEffect(uiState.isLoggedIn, uiState.isFirstLaunch) {
        when {
            uiState.isFirstLaunch && uiState.isLoggedIn -> navController.navigateSingleTopTo("onboarding")
            uiState.isLoggedIn -> navController.navigateSingleTopTo("dashboard")
            else -> navController.navigateSingleTopTo("login")
        }
    }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen()
        }
        composable("onboarding") {
            OnboardingScreen(
                onFinish = {
                    mainViewModel.onEvent(MainUiEvent.FinishOnboarding)
                    navController.navigateSingleTopTo("dashboard")
                }
            )
        }
        composable("login") {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    mainViewModel.checkAuthState()
                }
            )
        }
        composable("dashboard") {
            val activity = LocalContext.current as? ComponentActivity
            if (uiState.isLoading) {
                LoadingScreen()
            } else {
                when (uiState.userData?.role) {
                    UserRole.ADMIN -> AdminDashboardScreen(
                        onLogoutClick = { handleLogout(mainViewModel, navController) },
                        onManageUsersClick = { navController.navigate("user_management") },
                        onViewAlertsClick = { navController.navigate("alerts") },
                        onViewEventHistoryClick = { navController.navigate("event_history") },
                        hasPendingNotifications = hasPendingNotifications,
                        onBackPressed = { activity?.finish() }
                    )
                    UserRole.USER -> UserDashboardScreen(
                        onLogoutClick = { handleLogout(mainViewModel, navController) },
                        onViewEventHistoryClick = { navController.navigate("event_history") },
                        onViewAlertsClick = { navController.navigate("alerts") },
                        hasPendingNotifications = hasPendingNotifications,
                        onBackPressed = { activity?.finish() }
                    )
                    null -> {
                        LaunchedEffect(Unit) {
                            navController.navigateSingleTopTo("login")
                        }
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
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun SplashScreen() {
    // Implementa tu pantalla de splash aquí
}

private fun handleLogout(viewModel: MainViewModel, navController: NavHostController) {
    viewModel.onEvent(MainUiEvent.Logout)
}

fun NavHostController.navigateSingleTopTo(route: String) {
    this.navigate(route) {
        popUpTo(this@navigateSingleTopTo.graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}