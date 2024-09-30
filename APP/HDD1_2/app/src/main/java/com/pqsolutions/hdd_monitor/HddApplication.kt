package com.pqsolutions.hdd_monitor

import android.app.Application
import android.util.Log
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
class HddApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate() {
        super.onCreate()
        initializeApp()
    }

    private fun initializeApp() {
        initializeGooglePlayServices()
        initializeFirebase()
        initializeFirestore()
        // Aquí puedes inicializar otros componentes de la app
    }

    private fun initializeGooglePlayServices() {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (availability.isUserResolvableError(resultCode)) {
                Log.w(TAG, "Google Play Services está disponible pero necesita actualización: ${availability.getErrorString(resultCode)}")
                // Aquí podrías mostrar un diálogo al usuario para que actualice Google Play Services
            } else {
                Log.e(TAG, "Este dispositivo no es compatible con Google Play Services: ${availability.getErrorString(resultCode)}")
                // Aquí podrías mostrar un mensaje al usuario o tomar alguna acción alternativa
            }
        } else {
            Log.d(TAG, "Google Play Services está disponible y actualizado")
        }
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            Log.d(TAG, "Firebase inicializado con éxito")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar Firebase", e)
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