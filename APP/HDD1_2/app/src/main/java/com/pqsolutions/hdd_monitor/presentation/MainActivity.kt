package com.pqsolutions.hdd_monitor.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.google.firebase.FirebaseApp
import com.pqsolutions.hdd_monitor.presentation.navigation.AppNavigation
import com.pqsolutions.hdd_monitor.presentation.theme.HddMonitorTheme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        setContent {
            HddMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    if (uiState.error != null) {
                        AlertDialog(
                            onDismissRequest = { /* Dismiss logic */ },
                            title = { Text("Error") },
                            text = { Text(uiState.error!!) },
                            confirmButton = {
                                TextButton(onClick = { /* Dismiss logic */ }) {
                                    Text("OK")
                                }
                            }
                        )
                    }

                    AppNavigation()
                }
            }
        }
    }
}