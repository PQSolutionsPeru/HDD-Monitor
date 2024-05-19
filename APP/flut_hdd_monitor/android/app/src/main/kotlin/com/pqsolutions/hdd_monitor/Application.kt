package com.pqsolutions.hdd_monitor

import android.app.Application
import be.tramckrijte.workmanager.WorkmanagerPlugin
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.util.GeneratedPluginRegister

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        // Configura el registrador de plugins para Workmanager
        WorkmanagerPlugin.setPluginRegistrantCallback { flutterEngine: FlutterEngine ->
            GeneratedPluginRegister.registerGeneratedPlugins(flutterEngine)
        }
    }
}
