package com.pqsolutions.hdd_monitor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(private val context: Context) {

    private val isFirstLaunchKey = booleanPreferencesKey("is_first_launch")
    private val clientIdKey = stringPreferencesKey("client_id")
    private val themeKey = stringPreferencesKey("theme")
    private val languageKey = stringPreferencesKey("language")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val authTokenKey = stringPreferencesKey("auth_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userNameKey = stringPreferencesKey("user_name")
    private val userEmailKey = stringPreferencesKey("user_email")
    private val userRoleKey = stringPreferencesKey("user_role")
    private val lastSyncDateKey = longPreferencesKey("last_sync_date")

    val isFirstLaunchFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[isFirstLaunchKey] ?: true
    }

    val clientIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[clientIdKey]
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[themeKey] ?: "system"
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[languageKey] ?: "es"
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[notificationsEnabledKey] ?: true
    }

    val authTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[authTokenKey]
    }

    val userDataFlow: Flow<UserData?> = context.dataStore.data.map { preferences ->
        val userId = preferences[userIdKey]
        val userName = preferences[userNameKey]
        val userEmail = preferences[userEmailKey]
        val userRole = preferences[userRoleKey]
        val clientId = preferences[clientIdKey]

        if (userId != null && userName != null && userEmail != null && userRole != null) {
            UserData(
                id = userId,
                name = userName,
                email = userEmail,
                role = UserRole.valueOf(userRole),
                clientId = clientId ?: ""
            )
        } else {
            null
        }
    }

    val lastSyncDateFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[lastSyncDateKey] ?: 0L
    }

    suspend fun setFirstLaunch(isFirstLaunch: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[isFirstLaunchKey] = isFirstLaunch
        }
    }

    suspend fun setClientId(clientId: String) {
        context.dataStore.edit { preferences ->
            preferences[clientIdKey] = clientId
        }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = theme
        }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[languageKey] = language
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[notificationsEnabledKey] = enabled
        }
    }

    suspend fun setAuthToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[authTokenKey] = token
        }
    }

    suspend fun setUserData(userData: UserData) {
        context.dataStore.edit { preferences ->
            preferences[userIdKey] = userData.id
            preferences[userNameKey] = userData.name
            preferences[userEmailKey] = userData.email
            preferences[userRoleKey] = userData.role.name
            preferences[clientIdKey] = userData.clientId
        }
    }

    suspend fun setLastSyncDate(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[lastSyncDateKey] = timestamp
        }
    }

    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.remove(userIdKey)
            preferences.remove(userNameKey)
            preferences.remove(userEmailKey)
            preferences.remove(userRoleKey)
            preferences.remove(clientIdKey)
            preferences.remove(authTokenKey)
        }
    }
}