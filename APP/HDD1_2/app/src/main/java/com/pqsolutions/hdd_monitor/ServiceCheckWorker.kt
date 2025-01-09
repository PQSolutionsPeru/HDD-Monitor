package com.pqsolutions.hdd_monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pqsolutions.hdd_monitor.service.MonitoringService

class ServiceCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ServiceCheckWorker"
    }

    override suspend fun doWork(): Result {
        try {
            if (!isServiceRunning()) {
                Log.d(TAG, "Servicio de monitoreo no encontrado, reiniciando...")
                startMonitoringService()
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando servicio", e)
            return Result.retry()
        }
    }

    private fun isServiceRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (MonitoringService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(context, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}