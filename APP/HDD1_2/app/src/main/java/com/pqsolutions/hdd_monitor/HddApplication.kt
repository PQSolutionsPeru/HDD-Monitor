package com.pqsolutions.hdd_monitor

import android.app.Application
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class HddApplication : MultiDexApplication() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate() {
        super.onCreate()
        initializeApp()
    }

    private fun initializeApp() {
        applicationScope.launch {
            initializeGooglePlayServices()
            initializeFirebase()
            initializeFirestore()
        }
    }

    private fun initializeGooglePlayServices() {
        try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(this)
            when (resultCode) {
                ConnectionResult.SUCCESS -> Log.d(TAG, "Google Play Services está disponible y actualizado")
                else -> {
                    if (availability.isUserResolvableError(resultCode)) {
                        Log.w(TAG, "Google Play Services necesita actualización: ${availability.getErrorString(resultCode)}")
                        // Considera mostrar un diálogo al usuario para actualizar
                    } else {
                        Log.e(TAG, "Este dispositivo no es compatible con Google Play Services: ${availability.getErrorString(resultCode)}")
                        // Considera mostrar un mensaje al usuario o tomar una acción alternativa
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar Google Play Services", e)
        }
    }

    private fun initializeFirebase() {
        try {
            FirebaseApp.initializeApp(this)?.let {
                Log.d(TAG, "Firebase initialized successfully in Application: ${it.name}")
            } ?: throw Exception("FirebaseApp.initializeApp returned null")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase in Application", e)
        }
    }

    private fun initializeFirestore() {
        try {
            firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            firestore.firestoreSettings = settings
            Log.d(TAG, "Firestore inicializado con éxito")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar Firestore", e)
        }
    }

    companion object {
        private const val TAG = "HddApplication"
    }
}