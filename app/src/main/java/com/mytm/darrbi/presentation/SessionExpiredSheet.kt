package com.mytm.darrbi.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton

/**
 * Non-dismissable sheet shown when the session token expires (see [SessionViewModel.sessionExpired]).
 * There is no drag handle, back press is swallowed, and swipe/scrim can't hide it — the only way out is
 * the primary button, which forces a logout via [onLogout]. Once logged out the ViewModel store is
 * cleared and this sheet's host recomposes without it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionExpiredSheet(onLogout: () -> Unit) {
    // Block swipe-to-dismiss: never allow the sheet to settle in the Hidden state.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    // Belt-and-suspenders against the system back button while the sheet is up.
    BackHandler(enabled = true) {}
    ModalBottomSheet(
        // No-op: scrim taps and back presses route here, so the sheet stays put.
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = DarrbiTheme.colors.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Layered accent badge: a soft outer halo behind a tinted disc holding the lock-clock icon.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(DarrbiTheme.colors.primary.copy(alpha = 0.06f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(DarrbiTheme.colors.primary.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockClock,
                        contentDescription = null,
                        tint = DarrbiTheme.colors.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.session_expired_title),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.session_expired_message),
                style = DarrbiTheme.typography.body,
                color = DarrbiTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.session_expired_action),
                onClick = onLogout,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
