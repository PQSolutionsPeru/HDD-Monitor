package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pqsolutions.hdd_monitor.presentation.components.HddButton
import com.pqsolutions.hdd_monitor.presentation.components.HddOutlinedTextField
import com.pqsolutions.hdd_monitor.presentation.util.Dimensions
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback

@Composable
fun LoginScreen(onLoginClick: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.paddingLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "HDD Monitor",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { contentDescription = "App title" }
        )
        Spacer(modifier = Modifier.height(Dimensions.spacingLarge))
        HddOutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            modifier = Modifier.semantics { contentDescription = "Email input field" }
        )
        Spacer(modifier = Modifier.height(Dimensions.spacingMedium))
        HddOutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            isPassword = true,
            modifier = Modifier.semantics { contentDescription = "Password input field" }
        )
        Spacer(modifier = Modifier.height(Dimensions.spacingLarge))
        HddButton(
            onClick = {
                performHapticFeedback(hapticFeedback)
                onLoginClick(email, password)
            },
            text = "Login",
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.buttonHeight)
                .semantics { contentDescription = "Login button" }
        )
    }
}