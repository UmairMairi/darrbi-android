package com.mytm.darrbi.presentation.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField

/**
 * Update IBAN — the same Enter-IBAN form used on the dashboard, shown as a bottom sheet over the profile.
 * [onUpdated] fires once the IBAN validates successfully (the caller refreshes the profile IBAN).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateIbanSheet(
    onDismiss: () -> Unit,
    onUpdated: () -> Unit,
    viewModel: UpdateIbanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(state.done) {
        if (state.done) onUpdated()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarrbiTheme.colors.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.iban_enter_title), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.iban_enter_subtitle), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            DarrbiTextField(
                value = state.ibanInput,
                onValueChange = viewModel::onIbanChanged,
                label = stringResource(R.string.iban_label),
                keyboardType = KeyboardType.Text,
                isError = state.error,
                visualTransformation = IbanVisualTransformation,
                trailingContent = {
                    when {
                        state.verifying -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = DarrbiTheme.colors.primary)
                        state.error -> Icon(Icons.Filled.Close, null, tint = DarrbiTheme.colors.error)
                        state.bankName != null -> Icon(Icons.Filled.Check, null, tint = DarrbiTheme.colors.primary)
                    }
                },
            )
            if (state.error) {
                Spacer(Modifier.height(8.dp))
                InfoPill(stringResource(R.string.iban_incorrect), error = true)
            } else if (state.bankName != null) {
                Spacer(Modifier.height(8.dp))
                InfoPill(stringResource(R.string.iban_bank_format, state.bankName!!), error = false)
            }
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.common_done),
                onClick = viewModel::submit,
                enabled = state.canSubmit,
            )
        }
    }
}

@Composable
private fun InfoPill(text: String, error: Boolean) {
    val accent = if (error) DarrbiTheme.colors.error else DarrbiTheme.colors.primary
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.12f)) {
        Text(
            text = text,
            style = DarrbiTheme.typography.label,
            color = accent,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Groups the IBAN into blocks of 4 for display ("SA44 2000 …"), same as the dashboard's field. */
private val IbanVisualTransformation = VisualTransformation { text ->
    val grouped = text.text.chunked(4).joinToString(" ")
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = offset + (offset - 1).coerceAtLeast(0) / 4
        override fun transformedToOriginal(offset: Int): Int = offset - offset / 5
    }
    TransformedText(AnnotatedString(grouped), mapping)
}
