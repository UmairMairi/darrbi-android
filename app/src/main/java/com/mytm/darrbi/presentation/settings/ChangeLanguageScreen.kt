package com.mytm.darrbi.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiSelectableOption

/**
 * Change Language — a bottom sheet stacked over App Settings. Reuses the onboarding language options
 * (English / عربى) backed by LanguageStore, so the app locale/RTL/fonts re-apply when changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeLanguageSheet(
    onDismiss: () -> Unit,
    viewModel: ChangeLanguageViewModel = hiltViewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarrbiTheme.colors.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                text = stringResource(R.string.settings_change_language),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            DarrbiSelectableOption(
                text = stringResource(R.string.language_english),
                selected = language == "en",
                onClick = { viewModel.select("en") },
            )
            DarrbiSelectableOption(
                text = stringResource(R.string.language_arabic),
                selected = language == "ar",
                onClick = { viewModel.select("ar") },
            )
        }
    }
}
