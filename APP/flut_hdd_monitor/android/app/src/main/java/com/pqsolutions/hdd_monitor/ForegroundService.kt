package com.pqsolutions.hdd_monitor

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class ForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Foreground Service started")

        // Do some work here
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Foreground Service destroyed")
    }

    companion object {
        private const val TAG = "ForegroundService"
    }
}
