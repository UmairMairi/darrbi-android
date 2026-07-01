package com.mytm.darrbi.shared.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Named text styles (ported verbatim). Styles carry font/weight/size only — color is applied by the
 * composable from [DarrbiTheme.colors]. The [FontFamily] is locale-aware (see [appFontFamily]).
 */
@Immutable
data class DarrbiTypography(
    val titleLarge: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val caption: TextStyle,
)

fun darrbiTypography(fontFamily: FontFamily): DarrbiTypography = DarrbiTypography(
    titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    title = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    body = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    label = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    button = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    caption = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
)
