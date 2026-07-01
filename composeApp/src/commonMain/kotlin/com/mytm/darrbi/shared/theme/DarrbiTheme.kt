package com.mytm.darrbi.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App theme. Exposes [DarrbiColors] and [DarrbiTypography] via CompositionLocals so UI reads visuals through
 * [DarrbiTheme] (never hardcoded). Typography is locale-aware and rebuilt from [appFontFamily] each
 * composition. Ported verbatim from the Android module — pure Compose, compiles on Android + iOS.
 */
val LocalDarrbiColors = staticCompositionLocalOf { LightDarrbiColors }
val LocalDarrbiTypography = staticCompositionLocalOf { darrbiTypography(FontFamilyFallback) }

@Composable
fun DarrbiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkDarrbiColors else LightDarrbiColors
    val typography = darrbiTypography(appFontFamily())
    CompositionLocalProvider(
        LocalDarrbiColors provides colors,
        LocalDarrbiTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            content = content,
        )
    }
}

object DarrbiTheme {
    val colors: DarrbiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDarrbiColors.current

    val typography: DarrbiTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalDarrbiTypography.current
}

private fun DarrbiColors.toMaterialColorScheme(darkTheme: Boolean) =
    if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            error = error,
            onError = onError,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            error = error,
            onError = onError,
        )
    }
