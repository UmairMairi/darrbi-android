package com.mytm.darrbi.presentation.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/**
 * Branded splash — full-screen black background with the centered Darrbi logo, matching ride-android's
 * launcher splash (`activity_splash`/`activity_splash_two`: black background, centered `app_logo2`).
 *
 * Routing (version check -> session -> onboarding/home) will be wired here once those screens exist.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarrbiTheme.colors.splashBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.darrbi_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxWidth(fraction = 0.55f),
            contentScale = ContentScale.Fit,
        )
    }
}
