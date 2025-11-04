package com.wllcom.quicomguide.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

object SettingsKeys {
    val AUTO_SYNC = booleanPreferencesKey("auto_sync")
    val NOTIFICATION = booleanPreferencesKey("notification")

    val ACCOUNT = stringPreferencesKey("account")

    val LAST_ACCOUNT_SYNC = stringPreferencesKey("last_account_sync")
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val autoSyncFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[SettingsKeys.AUTO_SYNC] ?: false }

    val notificationFlow: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[SettingsKeys.NOTIFICATION] ?: false }

    val accountFlow: Flow<String> = dataStore.data
        .map { prefs -> prefs[SettingsKeys.ACCOUNT] ?: "" }

    val lastAccountSyncFlow: Flow<String> = dataStore.data
        .map { prefs -> prefs[SettingsKeys.LAST_ACCOUNT_SYNC] ?: "" }

    suspend fun setAutoSync(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.AUTO_SYNC] = enabled
        }
    }

    suspend fun setNotification(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.NOTIFICATION] = enabled
        }
    }

    suspend fun setAccount(email: String) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.ACCOUNT] = email
        }
    }

    suspend fun setLastAccountSync(email: String) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.LAST_ACCOUNT_SYNC] = email
        }
    }
}