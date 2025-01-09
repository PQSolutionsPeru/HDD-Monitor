package com.pqsolutions.hdd_monitor.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "MonitoringServiceChannel"

    companion object {
        private const val TAG = "MonitoringService"
        private var isServiceRunning = false

        fun isRunning() = isServiceRunning
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio creado")
        createNotificationChannel()
        acquireWakeLock()
        isServiceRunning = true
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio iniciado/reiniciado")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, createNotification())
            } else {
                startForeground(NOTIFICATION_ID, createNotificationPreO())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando servicio en primer plano", e)
            // Intento de recuperación
            try {
                startForeground(NOTIFICATION_ID, createNotificationPreO())
            } catch (e2: Exception) {
                Log.e(TAG, "Error en recuperación del servicio", e2)
            }
        }
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    ensureServiceIsRunning()
                    delay(30_000) // 30 segundos
                } catch (e: Exception) {
                    Log.e(TAG, "Error en el loop de monitoreo", e)
                    delay(5_000) // Esperar 5 segundos antes de reintentar
                }
            }
        }
    }

    private fun ensureServiceIsRunning() {
        if (!isServiceRunning) {
            Log.d(TAG, "Detectada parada del servicio, intentando reiniciar")
            restartService()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Estado del Servicio",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Estado del servicio de monitoreo"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HDD Monitor")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun createNotificationPreO(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HDD Monitor")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HddMonitor::MonitoringLock"
                ).apply {
                    acquire(24 * 60 * 60 * 1000L) // 24 horas
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adquiriendo WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando WakeLock", e)
        }
    }

    private fun restartService() {
        val intent = Intent(applicationContext, MonitoringService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reiniciando servicio", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Servicio siendo destruido")
        isServiceRunning = false
        serviceJob.cancel()
        releaseWakeLock()
        super.onDestroy()
        restartService()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Tarea removida")
        super.onTaskRemoved(rootIntent)
        restartService()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.d(TAG, "Memoria baja detectada")
        // Intentar liberar recursos no esenciales pero mantener el servicio
        System.gc()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}