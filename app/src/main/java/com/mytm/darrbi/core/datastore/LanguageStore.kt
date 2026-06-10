package com.mytm.darrbi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mytm.darrbi.core.common.ApplicationScope
import com.mytm.darrbi.core.common.LanguageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the chosen language ("en"/"ar") and exposes it synchronously for the language interceptor. */
@Singleton
class LanguageStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) : LanguageProvider {

    @Volatile
    override var language: String = DEFAULT_LANGUAGE
        private set

    val languageFlow: Flow<String> = dataStore.data.map { it[Keys.LANGUAGE] ?: DEFAULT_LANGUAGE }

    init {
        scope.launch { languageFlow.collect { language = it } }
    }

    suspend fun setLanguage(tag: String) {
        dataStore.edit { it[Keys.LANGUAGE] = tag }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
    }

    companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}
