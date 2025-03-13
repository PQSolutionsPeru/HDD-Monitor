package com.pqsolutions.hdd_monitor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.service.MonitoringService
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.util.EventNotificationScheduler
import com.pqsolutions.hdd_monitor.util.EventReminderWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class HddApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "HddApplication"
        private const val SERVICE_CHECK_WORK = "service_check_work"
    }

    @Inject
    lateinit var eventRepository: EventRepository

    @Inject
    lateinit var eventNotificationScheduler: EventNotificationScheduler

    @Inject
    lateinit var eventReminderWorkerFactory: EventReminderWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .setWorkerFactory(eventReminderWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        initializeFirebaseMessaging()

        // Crear canales de notificación
        createNotificationChannels()

        // Inicializar canal de notificaciones para recordatorios de eventos
        eventNotificationScheduler.createNotificationChannel()

        // Programar notificaciones para eventos pendientes
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                eventRepository.scheduleAllPendingEventNotifications()
                Log.d(TAG, "Programación de notificaciones para eventos pendientes iniciada")
            } catch (e: Exception) {
                Log.e(TAG, "Error programando notificaciones para eventos", e)
            }
        }

        // Iniciar servicio de monitoreo
        startMonitoringService()

        // Configurar la app para mantenerla viva
        setupKeepAlive()

        // Programar trabajo periódico para verificar el servicio
        scheduleServiceCheck()
    }

    private fun initializeFirebaseMessaging() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Error obteniendo token FCM", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d(TAG, "FCM Token: $token")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal para el servicio de monitoreo
            NotificationChannel(
                "MonitoringServiceChannel",
                "Estado del Servicio",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Indica que el servicio de monitoreo está activo"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = NotificationManager.IMPORTANCE_MIN
                notificationManager.createNotificationChannel(this)
            }

            // Canal para eventos y alarmas
            NotificationChannel(
                "event_notifications",
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_description)
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationManager.IMPORTANCE_HIGH
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    private fun startMonitoringService() {
        try {
            val serviceIntent = Intent(this, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "Servicio de monitoreo iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando servicio de monitoreo", e)
        }
    }

    private fun setupKeepAlive() {
        // Solicitar ignorar optimización de batería
        requestBatteryOptimizationDisable()

        // Mantener viva la aplicación durante el modo de ahorro de batería
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error solicitando ignorar optimización de batería", e)
                }
            }
        }
    }

    private fun requestBatteryOptimizationDisable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error solicitando ignorar optimización de batería", e)
                }
            }
        }
    }

    private fun scheduleServiceCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val serviceCheckWork = PeriodicWorkRequestBuilder<ServiceCheckWorker>(
            15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                30000L, // 30 segundos como backoff mínimo recomendado
                TimeUnit.MILLISECONDS
            )
            .addTag("service_check")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ServiceCheckWorker.SERVICE_CHECK_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            serviceCheckWork
        )

        Log.d(TAG, "Service check work programado")
    }
}