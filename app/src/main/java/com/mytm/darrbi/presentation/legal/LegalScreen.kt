package com.mytm.darrbi.presentation.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/**
 * Legal text screen for Terms & Conditions / Privacy Policy. Content is fetched as HTML from
 * `GET /master/pages/.../{language}` and rendered (no dummy text).
 */
@Composable
fun LegalScreen(titleRes: Int, page: LegalPage, onBack: () -> Unit, viewModel: LegalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(page) { viewModel.load(page) }

    Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarrbiTheme.colors.surface)
                    .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_back), tint = DarrbiTheme.colors.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(titleRes), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(16.dp))
            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarrbiTheme.colors.primary)
                }
                state.html != null -> Text(
                    text = AnnotatedString.fromHtml(state.html!!),
                    style = DarrbiTheme.typography.body,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                )
                else -> Text(
                    text = state.errorMessage ?: stringResource(R.string.error_generic),
                    style = DarrbiTheme.typography.body,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
