package com.pqsolutions.hdd_monitor.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.presentation.navigation.AppNavigation
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import com.pqsolutions.hdd_monitor.presentation.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.w(TAG, "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        if (!checkGooglePlayServices()) {
            Log.e(TAG, "Google Play Services not available.")
            finish()
            return
        }

        initializeFirebase()
        requestNotificationPermission()
        setAppContent()
        observeUiState()
        setupBackPressHandler()

        Log.d(TAG, "onCreate completed")
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed")
                if (shouldExitApp()) {
                    finish()
                } else {
                    // Permitir que la navegación maneje el retroceso
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun shouldExitApp(): Boolean {
        // Verifica si estamos en la pantalla principal (dashboard)
        return mainViewModel.uiState.value.isLoggedIn &&
                !mainViewModel.uiState.value.isFirstLaunch
    }

    private fun checkGooglePlayServices(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)?.show()
            } else {
                Log.e(TAG, "This device is not supported.")
            }
            return false
        }
        return true
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)?.let {
                    Log.d(TAG, "Firebase initialized successfully: ${it.name}")
                } ?: throw Exception("FirebaseApp.initializeApp returned null")
            } else {
                Log.d(TAG, "Firebase was already initialized")
            }

            FirebaseFirestore.setLoggingEnabled(true)
            Log.d(TAG, "Firestore logging enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.d(TAG, "Should show permission rationale")
                    showNotificationPermissionRationale()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun showNotificationPermissionRationale() {
        // Implementa aquí un diálogo o UI para explicar por qué necesitas el permiso
        // Por ejemplo, puedes usar AlertDialog o un SnackBar
    }

    private fun setAppContent() {
        Log.d(TAG, "Setting content")
        setContent {
            HDD1_2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by mainViewModel.uiState.collectAsState()
                    Log.d(TAG, "Current UI State: $uiState")

                    AppNavigation(mainViewModel, loginViewModel)
                }
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            mainViewModel.uiState.collectLatest { state ->
                state.error?.let { error ->
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                    // Limpiar el error después de mostrarlo
                    mainViewModel.clearError()
                }
            }
        }
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
        private const val PLAY_SERVICES_RESOLUTION_REQUEST = 9000
    }
}