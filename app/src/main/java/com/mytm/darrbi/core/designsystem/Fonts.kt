package com.mytm.darrbi.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.mytm.darrbi.R

/** SF Pro Display — used for English / Latin text. */
val SfProFontFamily = FontFamily(
    Font(R.font.sf_pro_display_thin, FontWeight.Thin),
    Font(R.font.sf_pro_display_ultralight, FontWeight.ExtraLight),
    Font(R.font.sf_pro_display_light, FontWeight.Light),
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.sf_pro_display_heavy, FontWeight.ExtraBold),
    Font(R.font.sf_pro_display_black, FontWeight.Black),
)

/** Madani Arabic — used for Arabic text. */
val MadaniFontFamily = FontFamily(
    Font(R.font.madani_arabic_thin, FontWeight.Thin),
    Font(R.font.madani_arabic_extra_light, FontWeight.ExtraLight),
    Font(R.font.madani_arabic_light, FontWeight.Light),
    Font(R.font.madani_arabic_regular, FontWeight.Normal),
    Font(R.font.madani_arabic_medium, FontWeight.Medium),
    Font(R.font.madani_arabic_semi_bold, FontWeight.SemiBold),
    Font(R.font.madani_arabic_bold, FontWeight.Bold),
    Font(R.font.madani_arabic_extra_bold, FontWeight.ExtraBold),
    Font(R.font.madani_arabic_black, FontWeight.Black),
)

/** Picks the font family by layout direction: Arabic (RTL) => Madani; otherwise SF Pro Display. */
@Composable
@ReadOnlyComposable
fun appFontFamily(): FontFamily =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) MadaniFontFamily else SfProFontFamily
