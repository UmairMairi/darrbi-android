package com.mytm.darrbi.core.localization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * Re-localizes the Compose tree for [language] ("en"/"ar") WITHOUT recreating the Activity: provides a
 * locale-configured Context (so `stringResource` resolves `values[-ar]`) plus the matching
 * `LocalLayoutDirection` (so Arabic flips to RTL automatically) and font (Madani vs SF Pro).
 *
 * IMPORTANT: the localized Context is a [ContextWrapper] around the original (Activity) context — only its
 * resources are overridden. Using a bare `createConfigurationContext(...)` as `LocalContext` breaks
 * `hiltViewModel()` / any `Context.findActivity()`, because that context has no Activity in its parent chain.
 */
@Composable
fun ProvideLocale(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val baseConfiguration = LocalConfiguration.current

    val localizedContext = remember(language, baseConfiguration) {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(baseConfiguration).apply { setLocale(locale) }
        val localizedResources = context.createConfigurationContext(config).resources
        object : ContextWrapper(context) {
            override fun getResources(): Resources = localizedResources
            override fun getAssets(): AssetManager = localizedResources.assets
        }
    }
    val layoutDirection = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalLayoutDirection provides layoutDirection,
        content = content,
    )
}
