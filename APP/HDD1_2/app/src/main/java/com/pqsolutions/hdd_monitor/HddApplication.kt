package com.pqsolutions.hdd_monitor

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import androidx.multidex.MultiDexApplication

@HiltAndroidApp
class HddApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}