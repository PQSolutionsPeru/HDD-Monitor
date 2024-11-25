package com.pqsolutions.hdd_monitor.presentation.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import com.pqsolutions.hdd_monitor.presentation.screens.AdminDashboardScreen
import com.pqsolutions.hdd_monitor.presentation.screens.ClientManagementScreen
import com.pqsolutions.hdd_monitor.presentation.screens.EventScreen
import com.pqsolutions.hdd_monitor.presentation.screens.LoginScreen
import com.pqsolutions.hdd_monitor.presentation.screens.NotificationHistoryScreen
import com.pqsolutions.hdd_monitor.presentation.screens.OnboardingScreen
import com.pqsolutions.hdd_monitor.presentation.screens.UserDashboardScreen
import com.pqsolutions.hdd_monitor.presentation.state.MainUiEvent
import com.pqsolutions.hdd_monitor.presentation.state.MainUiState
import com.pqsolutions.hdd_monitor.presentation.viewmodel.EventViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel

private const val TAG = "AppNavigation"

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object ClientManagement : Screen("client_management")
    object NotificationHistory : Screen("notification_history")
    object Events : Screen("events")
    object Agenda : Screen("agenda") {
        const val routeWithClearStack = "agenda?clearStack=true"
    }
}

class NavigationActions(private val navController: NavHostController) {
    fun navigateTo(route: String) {
        if (route == navController.currentDestination?.route) return

        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateUp() {
        navController.navigateUp()
    }

    fun navigateToLogin() {
        navController.navigate(Screen.Login.route) {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel = hiltViewModel(),
    startDestination: String = Screen.Login.route
) {
    Log.d(TAG, "Starting AppNavigation composition")
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val notificationState by viewModel.notificationState.collectAsState()

    // Recordar la última ruta para evitar navegaciones duplicadas
    val lastRoute = remember { mutableStateOf<String?>(null) }

    val navigationActions = remember(navController) {
        NavigationActions(navController)
    }

    val deepLinks = listOf(
        navDeepLink {
            uriPattern = "hddmonitor://notifications/{type}"
            action = "android.intent.action.VIEW"
        }
    )

    NavHost(
        navController = navController,
        startDestination = getStartDestination(uiState)
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    viewModel.onEvent(MainUiEvent.FinishOnboarding)
                    navigationActions.navigateToLogin()
                }
            )
        }

        composable(Screen.Login.route) {
            if (!uiState.isLoggedIn) {
                LoginScreen(
                    onLoginClick = { email, password ->
                        if (!uiState.isLoading) {
                            Log.d(TAG, "Login attempt with email: $email")
                            viewModel.onEvent(MainUiEvent.Login(email, password))
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navigationActions.navigateTo(Screen.Dashboard.route)
                }
            }
        }

        composable(
            route = Screen.Dashboard.route,
            deepLinks = deepLinks
        ) {
            if (!uiState.isLoggedIn) {
                LaunchedEffect(Unit) {
                    navigationActions.navigateToLogin()
                }
                return@composable
            }

            LaunchedEffect(Unit) {
                handleDeepLink(navController, viewModel)
            }

            Log.d(TAG, "Navigating to Dashboard. User role: ${uiState.userData?.role}")

            when (uiState.userData?.role) {
                UserRole.ADMIN -> {
                    AdminDashboardScreen(
                        navController = navController,
                        onLogoutClick = { handleLogout(viewModel) },
                        onManageUsersClick = {
                            navigationActions.navigateTo(Screen.ClientManagement.route)
                        },
                        onViewEventsClick = {
                            navigationActions.navigateTo(Screen.Events.route)
                        },
                        onViewNotificationHistoryClick = {
                            navigationActions.navigateTo(Screen.NotificationHistory.route)
                        }
                    )
                }
                UserRole.USER -> {
                    UserDashboardScreen(
                        navController = navController,
                        onLogoutClick = { handleLogout(viewModel) },
                        onViewNotificationHistoryClick = {
                            navigationActions.navigateTo(Screen.NotificationHistory.route)
                        },
                        onViewEventsClick = {
                            navigationActions.navigateTo(Screen.Events.route)
                        }
                    )
                }
                else -> {
                    Log.d(TAG, "Invalid user role: ${uiState.userData?.role}, navigating to Login")
                    LaunchedEffect(Unit) {
                        navigationActions.navigateToLogin()
                    }
                }
            }
        }

        composable(Screen.ClientManagement.route) {
            if (!uiState.isLoggedIn) {
                LaunchedEffect(Unit) {
                    navigationActions.navigateToLogin()
                }
                return@composable
            }

            ClientManagementScreen(
                navController = navController,
                onBackClick = { navigationActions.navigateUp() },
                onNotificationClick = {
                    navigationActions.navigateTo(Screen.Events.route)
                }
            )
        }

        composable(Screen.NotificationHistory.route) {
            if (!uiState.isLoggedIn) {
                LaunchedEffect(Unit) {
                    navigationActions.navigateToLogin()
                }
                return@composable
            }

            NotificationHistoryScreen(
                navController = navController,
                onBackClick = { navigationActions.navigateUp() }
            )
        }

        composable(Screen.Events.route) {
            if (!uiState.isLoggedIn) {
                LaunchedEffect(Unit) {
                    navigationActions.navigateToLogin()
                }
                return@composable
            }

            EventScreen(
                navController = navController,
                onBackClick = { navigationActions.navigateUp() },
                isAdmin = uiState.userData?.role == UserRole.ADMIN
            )
        }

        composable(Screen.Agenda.route) {
            if (!uiState.isLoggedIn) {
                LaunchedEffect(Unit) {
                    navigationActions.navigateToLogin()
                }
                return@composable
            }

            val eventViewModel: EventViewModel = hiltViewModel()
            EventScreen(
                viewModel = eventViewModel,
                navController = navController,
                onBackClick = { navController.popBackStack() },
                isAdmin = uiState.userData?.role == UserRole.ADMIN
            )
        }
    }

    // Manejar cambios de ruta basados en el estado de autenticación
    LaunchedEffect(uiState.currentRoute, uiState.isLoggedIn) {
        val targetRoute = when {
            uiState.isLoggedIn -> Screen.Dashboard.route
            else -> Screen.Login.route
        }

        // Solo navegar si la ruta ha cambiado y es diferente a la actual
        if (targetRoute != lastRoute.value && targetRoute != navController.currentDestination?.route) {
            lastRoute.value = targetRoute
            navigationActions.navigateTo(targetRoute)
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

private suspend fun handleDeepLink(navController: NavController, viewModel: MainViewModel) {
    navController.currentBackStackEntry?.arguments?.let { args ->
        val notificationType = args.getString("type")

        when (notificationType) {
            "events" -> {
                Log.d(TAG, "Deep link: Navigating to Events")
                navController.navigate(Screen.Events.route) {
                    launchSingleTop = true
                }
            }
            "notifications" -> {
                Log.d(TAG, "Deep link: Navigating to Notification History")
                navController.navigate(Screen.NotificationHistory.route) {
                    launchSingleTop = true
                }
            }
            "panel_alerts" -> {
                Log.d(TAG, "Deep link: Navigating to Dashboard")
                navController.navigate(Screen.Dashboard.route) {
                    launchSingleTop = true
                }
            }
            "agenda" -> {
                Log.d(TAG, "Deep link: Navigating to Agenda")
                navController.navigate(Screen.Agenda.route) {
                    launchSingleTop = true
                }
            }
        }
    }
}