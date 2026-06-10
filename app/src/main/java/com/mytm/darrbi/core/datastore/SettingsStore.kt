package com.mytm.darrbi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App settings (e.g. dark mode override). `null` dark mode = follow system. */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val darkModeFlow: Flow<Boolean?> = dataStore.data.map { it[Keys.DARK_MODE] }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }
}
