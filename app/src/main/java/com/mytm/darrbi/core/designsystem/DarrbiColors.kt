package com.mytm.darrbi.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for the app. Raw [Color] literals are allowed ONLY here (the design system is the
 * single source of color); UI code must read colors via [DarrbiTheme.colors], never hardcode them.
 *
 * Seeded from ride-android's palette; expand slots as screens need them.
 */
@Immutable
data class DarrbiColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color,
    val onError: Color,
    val warning: Color,
    // Brand component tokens (buttons, inputs, selectable rows).
    val buttonContainer: Color,
    val onButton: Color,
    val buttonDisabledContainer: Color,
    val onButtonDisabled: Color,
    val inputBorder: Color,
    /** Brand splash background — intentionally black in both light and dark (the logo is light-on-dark). */
    val splashBackground: Color,
    /** Bright accent green for the RIDER/CAPTAIN mode toggle's active segment (per the design). */
    val modeAccent: Color,
    /** Pastel background tints for the rider home category tiles, applied cyclically by tile index. */
    val categoryTints: List<Color>,
)

private val BrandGreen = Color(0xFF13B542)
private val BrandBlue = Color(0xFF3399FF)
private val Black = Color(0xFF000000)
private val NearBlack = Color(0xFF0A0A0A)
private val White = Color(0xFFFFFFFF)
private val ModeGreen = Color(0xFF34D94F)

val LightDarrbiColors = DarrbiColors(
    primary = BrandGreen,
    onPrimary = White,
    secondary = BrandBlue,
    onSecondary = White,
    background = White,
    onBackground = Color(0xFF111111),
    surface = White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF666666),
    outline = Color(0xFFE2E2E2),
    error = Color(0xFFD32F2F),
    onError = White,
    warning = Color(0xFFF5A623),
    buttonContainer = NearBlack,
    onButton = White,
    buttonDisabledContainer = Color(0xFFC9C9C9),
    onButtonDisabled = White,
    inputBorder = Color(0xFFE2E2E2),
    splashBackground = Black,
    modeAccent = ModeGreen,
    categoryTints = listOf(
        Color(0xFFE7F6EC), // mint  — Derrbi Taxi
        Color(0xFFF1F2F4), // grey  — Car Rental
        Color(0xFFF0EDF8), // lilac — Cargo
        Color(0xFFFBEEE6), // peach — Delivery
        Color(0xFFEAF1FB), // blue  — Scheduled Rides
    ),
)

val DarkDarrbiColors = DarrbiColors(
    primary = BrandGreen,
    onPrimary = White,
    secondary = BrandBlue,
    onSecondary = White,
    background = Color(0xFF121212),
    onBackground = White,
    surface = Color(0xFF1B1B1B),
    onSurface = White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFA0A0A0),
    outline = Color(0xFF545454),
    error = Color(0xFFEF5350),
    onError = Black,
    warning = Color(0xFFF5A623),
    buttonContainer = White,
    onButton = NearBlack,
    buttonDisabledContainer = Color(0xFF3A3A3A),
    onButtonDisabled = Color(0xFF8A8A8A),
    inputBorder = Color(0xFF545454),
    splashBackground = Black,
    modeAccent = ModeGreen,
    categoryTints = listOf(
        Color(0xFF15301E), // muted green — Derrbi Taxi
        Color(0xFF262829), // muted grey  — Car Rental
        Color(0xFF26233A), // muted lilac — Cargo
        Color(0xFF332620), // muted peach — Delivery
        Color(0xFF1E2A3A), // muted blue  — Scheduled Rides
    ),
)
