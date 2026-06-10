package com.mytm.darrbi.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/** Confirm sheet for logging out (matches ride-android's "Want to log out from Derrbi?" dialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoutConfirmSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarrbiTheme.colors.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResourceCompat(R.string.logout_confirm_title),
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResourceCompat(R.string.logout_confirm_subtitle),
                style = DarrbiTheme.typography.body,
                color = DarrbiTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarrbiTheme.colors.error,
                        contentColor = DarrbiTheme.colors.onError,
                    ),
                ) {
                    Text(stringResourceCompat(R.string.logout_dismiss), style = DarrbiTheme.typography.button)
                }
                OutlinedButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarrbiTheme.colors.onSurface),
                ) {
                    Text(stringResourceCompat(R.string.logout_confirm), style = DarrbiTheme.typography.button)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)
