package com.pqsolutions.hdd_monitor.presentation

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.google.firebase.FirebaseApp
import com.pqsolutions.hdd_monitor.presentation.navigation.AppNavigation
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        initializeFirebase()
        handleNotificationIntent(intent)
        setAppContent()

        Log.d(TAG, "onCreate completed")
    }

    private fun initializeFirebase() {
        FirebaseApp.initializeApp(this)
        Log.d(TAG, "FirebaseApp initialized")
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val notificationType = intent?.getStringExtra("notificationType")
        val panelId = intent?.getStringExtra("panelId")
        val relayName = intent?.getStringExtra("relayName")

        if (notificationType != null) {
            viewModel.handleNotificationNavigation(notificationType, panelId, relayName)
        }
    }

    private fun setAppContent() {
        Log.d(TAG, "Setting content")
        setContent {
            HDD1_2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    Log.d(TAG, "Current UI State: $uiState")

                    AppNavigation(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}