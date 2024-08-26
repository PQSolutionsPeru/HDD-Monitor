package com.pqsolutions.hdd_monitor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(private val context: Context) {

    private val isFirstLaunchKey = booleanPreferencesKey("is_first_launch")
    private val clientIdKey = stringPreferencesKey("client_id")

    suspend fun isFirstLaunch(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[isFirstLaunchKey] ?: true
        }.first()
    }

    suspend fun setFirstLaunch(isFirstLaunch: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[isFirstLaunchKey] = isFirstLaunch
        }
    }

    suspend fun getClientId(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[clientIdKey]
        }.first()
    }

    suspend fun setClientId(clientId: String) {
        context.dataStore.edit { preferences ->
            preferences[clientIdKey] = clientId
        }
    }
}