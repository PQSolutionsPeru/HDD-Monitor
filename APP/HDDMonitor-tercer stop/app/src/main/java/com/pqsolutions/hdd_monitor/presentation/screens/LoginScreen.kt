package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.components.HddButton
import com.pqsolutions.hdd_monitor.presentation.components.HddOutlinedTextField
import com.pqsolutions.hdd_monitor.presentation.util.Dimensions
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Prevenir múltiples intentos de login
    var isLoggingIn by remember { mutableStateOf(false) }

    if (uiState.isLoading || isLoggingIn) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.paddingLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { contentDescription = "App title" }
        )

        Spacer(modifier = Modifier.height(Dimensions.spacingLarge))

        HddOutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.email),
            modifier = Modifier.semantics { contentDescription = "Email input field" }
        )

        Spacer(modifier = Modifier.height(Dimensions.spacingMedium))

        HddOutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.password),
            isPassword = true,
            modifier = Modifier.semantics { contentDescription = "Password input field" }
        )

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(Dimensions.spacingMedium))
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(Dimensions.spacingLarge))

        HddButton(
            onClick = {
                if (!isLoggingIn && !uiState.isLoading) {
                    isLoggingIn = true
                    performHapticFeedback(context)
                    playSoundEffect(context, R.raw.button_click)
                    onLoginClick(email, password)
                }
            },
            enabled = !isLoggingIn && !uiState.isLoading && email.isNotBlank() && password.isNotBlank(),
            text = stringResource(R.string.login),
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.buttonHeight)
                .semantics { contentDescription = "Login button" }
        )
    }

    // Reset isLoggingIn cuando cambia el estado
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            isLoggingIn = false
        }
    }
}