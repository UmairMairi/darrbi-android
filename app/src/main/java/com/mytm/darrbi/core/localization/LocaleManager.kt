package com.mytm.darrbi.core.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mytm.darrbi.core.common.ApplicationScope
import com.mytm.darrbi.core.datastore.LanguageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies and persists the app language via AndroidX per-app locale. Setting an Arabic locale makes the
 * platform flip layout direction to RTL automatically. Replaces ride-android's deprecated `LocaleHelper`.
 */
@Singleton
class LocaleManager @Inject constructor(
    private val languageStore: LanguageStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val supportedLanguages = listOf("en", "ar")

    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        scope.launch { languageStore.setLanguage(tag) }
    }

    fun currentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty) locales[0]?.language ?: languageStore.language else languageStore.language
    }
}
