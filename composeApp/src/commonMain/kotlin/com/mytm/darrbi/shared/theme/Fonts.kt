package com.mytm.darrbi.shared.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.mytm.darrbi.shared.resources.Res
import com.mytm.darrbi.shared.resources.madani_arabic_black
import com.mytm.darrbi.shared.resources.madani_arabic_bold
import com.mytm.darrbi.shared.resources.madani_arabic_extra_bold
import com.mytm.darrbi.shared.resources.madani_arabic_extra_light
import com.mytm.darrbi.shared.resources.madani_arabic_light
import com.mytm.darrbi.shared.resources.madani_arabic_medium
import com.mytm.darrbi.shared.resources.madani_arabic_regular
import com.mytm.darrbi.shared.resources.madani_arabic_semi_bold
import com.mytm.darrbi.shared.resources.madani_arabic_thin
import com.mytm.darrbi.shared.resources.sf_pro_display_black
import com.mytm.darrbi.shared.resources.sf_pro_display_bold
import com.mytm.darrbi.shared.resources.sf_pro_display_heavy
import com.mytm.darrbi.shared.resources.sf_pro_display_light
import com.mytm.darrbi.shared.resources.sf_pro_display_medium
import com.mytm.darrbi.shared.resources.sf_pro_display_regular
import com.mytm.darrbi.shared.resources.sf_pro_display_semibold
import com.mytm.darrbi.shared.resources.sf_pro_display_thin
import com.mytm.darrbi.shared.resources.sf_pro_display_ultralight
import org.jetbrains.compose.resources.Font

/**
 * Non-composable fallback for the typography CompositionLocal default (resolved before [DarrbiTheme] runs).
 * The real locale-aware family is provided inside composition via [appFontFamily].
 */
val FontFamilyFallback: FontFamily = FontFamily.Default

/** SF Pro Display — used for English / Latin text. Loaded via Compose Multiplatform resources. */
@Composable
fun sfProFontFamily(): FontFamily = FontFamily(
    Font(Res.font.sf_pro_display_thin, FontWeight.Thin),
    Font(Res.font.sf_pro_display_ultralight, FontWeight.ExtraLight),
    Font(Res.font.sf_pro_display_light, FontWeight.Light),
    Font(Res.font.sf_pro_display_regular, FontWeight.Normal),
    Font(Res.font.sf_pro_display_medium, FontWeight.Medium),
    Font(Res.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(Res.font.sf_pro_display_bold, FontWeight.Bold),
    Font(Res.font.sf_pro_display_heavy, FontWeight.ExtraBold),
    Font(Res.font.sf_pro_display_black, FontWeight.Black),
)

/** Madani Arabic — used for Arabic text. */
@Composable
fun madaniFontFamily(): FontFamily = FontFamily(
    Font(Res.font.madani_arabic_thin, FontWeight.Thin),
    Font(Res.font.madani_arabic_extra_light, FontWeight.ExtraLight),
    Font(Res.font.madani_arabic_light, FontWeight.Light),
    Font(Res.font.madani_arabic_regular, FontWeight.Normal),
    Font(Res.font.madani_arabic_medium, FontWeight.Medium),
    Font(Res.font.madani_arabic_semi_bold, FontWeight.SemiBold),
    Font(Res.font.madani_arabic_bold, FontWeight.Bold),
    Font(Res.font.madani_arabic_extra_bold, FontWeight.ExtraBold),
    Font(Res.font.madani_arabic_black, FontWeight.Black),
)

/** Picks the font family by layout direction: Arabic (RTL) => Madani; otherwise SF Pro Display. */
@Composable
fun appFontFamily(): FontFamily =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) madaniFontFamily() else sfProFontFamily()
