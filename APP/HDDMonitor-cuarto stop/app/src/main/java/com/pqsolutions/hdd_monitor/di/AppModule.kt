package com.pqsolutions.hdd_monitor.di

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.bluetooth.*
import com.pqsolutions.hdd_monitor.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideBleManager(
        @ApplicationContext context: Context,
        coroutineScope: CoroutineScope
    ): BleManager {
        return BleManager(context, coroutineScope)
    }

    @Provides
    @Singleton
    fun providePanelRepository(
        firestore: FirebaseFirestore,
        firebaseMessaging: FirebaseMessaging,
        @ApplicationContext context: Context,
        @Named("HddMonitorPrefs") sharedPreferences: SharedPreferences
    ): PanelRepository = PanelRepository(firestore, firebaseMessaging, context, sharedPreferences)

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideBleConfiguration(): BleConfiguration {
        return BleConfiguration(
            clientId = "",
            clientName = "",
            panelId = "",
            panelName = "",
            panelLocation = "",
            wifiSsid = "",
            wifiPassword = ""
        )
    }

    @Provides
    @Singleton
    @Named("HddMonitorPrefs")
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("HddMonitorPrefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideEventRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): EventRepository {
        return EventRepository(firestore, auth)
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): UserRepository {
        return UserRepository(firestore, auth)
    }

    @Provides
    @Singleton
    fun provideNotificationRepository(
        firestore: FirebaseFirestore,
        userRepository: UserRepository
    ): NotificationRepository {
        return NotificationRepository(firestore, userRepository)
    }
}