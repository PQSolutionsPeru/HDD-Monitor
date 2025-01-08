package com.pqsolutions.hdd_monitor.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
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
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.presentation.navigation.AppNavigation
import com.pqsolutions.hdd_monitor.presentation.theme.HDD1_2Theme
import com.pqsolutions.hdd_monitor.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
        const val CHANNEL_ID_RELAY = "relay_status"
        const val CHANNEL_ID_EVENT = "event_notifications"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Iniciando aplicación")

        // Solicitar permisos de notificación en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        initializeFirebase()
        createNotificationChannels()
        setAppContent()

        Log.d(TAG, "onCreate: Configuración inicial completada")
    }

    private fun requestNotificationPermission() {
        Log.d(TAG, "Solicitando permisos de notificación")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                Log.d(TAG, "Permisos de notificación ya otorgados")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permisos de notificación otorgados por el usuario")
                } else {
                    Log.d(TAG, "Permisos de notificación denegados por el usuario")
                }
            }
        }
    }

    private fun initializeFirebase() {
        Log.d(TAG, "Inicializando Firebase")
        FirebaseApp.initializeApp(this)

        // Verificar y mostrar el token FCM
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Token FCM obtenido exitosamente: ${task.result}")
                    lifecycleScope.launch {
                        try {
                            viewModel.updateFCMToken()
                            Log.d(TAG, "Token FCM actualizado en el repositorio")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error actualizando token FCM", e)
                        }
                    }
                } else {
                    Log.e(TAG, "Error obteniendo token FCM", task.exception)
                }
            }

        // Configurar comportamiento de mensajes en primer plano
        FirebaseMessaging.getInstance().isAutoInitEnabled = true

        // Suscribirse a tópicos relevantes
        FirebaseMessaging.getInstance().subscribeToTopic("relay-status")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Suscripción exitosa al tópico relay-status")
                } else {
                    Log.e(TAG, "Error en suscripción a relay-status", task.exception)
                }
            }

        Log.d(TAG, "Inicialización de Firebase completada")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creando canales de notificación")
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal para notificaciones de relay
            NotificationChannel(
                CHANNEL_ID_RELAY,
                "Estado de Relay",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de cambios de estado en relays"
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500)
                enableLights(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setShowBadge(true)
                notificationManager.createNotificationChannel(this)
                Log.d(TAG, "Canal de relay creado: $id")
            }

            // Canal para notificaciones de eventos
            NotificationChannel(
                CHANNEL_ID_EVENT,
                "Eventos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de actualizaciones de eventos"
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500)
                enableLights(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setShowBadge(true)
                notificationManager.createNotificationChannel(this)
                Log.d(TAG, "Canal de eventos creado: $id")
            }
        }
    }

    private fun setAppContent() {
        Log.d(TAG, "Configurando contenido de la aplicación")
        setContent {
            HDD1_2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    Log.d(TAG, "Estado actual de UI: $uiState")

                    AppNavigation(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Actividad iniciada")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Actividad en primer plano")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Actividad pausada")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Actividad detenida")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Actividad destruida")
    }
}