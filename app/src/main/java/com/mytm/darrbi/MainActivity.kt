package com.mytm.darrbi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.core.datastore.LanguageStore
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.localization.ProvideLocale
import com.mytm.darrbi.presentation.DarrbiRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var languageStore: LanguageStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw edge-to-edge with transparent bars so the full-screen map extends under the system bars;
        // each screen insets only its own content (e.g. bottom cards use navigationBarsPadding). Dark style
        // = light bar icons, which read on the black splash/onboarding and over the map.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            // Localize ABOVE the theme so the locale-aware font (Madani for ar / SF Pro for en) and RTL
            // resolve correctly when DarrbiTheme builds its typography. Driven by the persisted language.
            val language by languageStore.languageFlow
                .collectAsStateWithLifecycle(initialValue = languageStore.language)
            ProvideLocale(language) {
                // Force light tokens: the brand splash/onboarding is a fixed black-backdrop + white-card design.
                DarrbiTheme(darkTheme = false) {
                    DarrbiRoot()
                }
            }
        }
    }
}
