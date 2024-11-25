package com.pqsolutions.hdd_monitor.di

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.NotificationRepository
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserPreferences
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.presentation.managers.NotificationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
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
    @Named("HddMonitorPrefs")
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("HddMonitorPrefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideEventRepository(firestore: FirebaseFirestore): EventRepository {
        return EventRepository(firestore)
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

    @Provides
    @Singleton
    fun provideNotificationManager(
        eventRepository: EventRepository,
        userRepository: UserRepository,
        panelRepository: PanelRepository,
        @ApplicationContext context: Context  // Agregado el contexto
    ): NotificationManager {
        return NotificationManager(
            eventRepository = eventRepository,
            userRepository = userRepository,
            panelRepository = panelRepository,
            context = context
        )
    }
}