package com.mytm.darrbi.presentation.profile

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
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
 * "Coming soon" sheet for profile features that aren't wired up yet (Car Deal Details, Saved Cards).
 * [featureLabelRes] names the tapped feature so the copy reads contextually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonSheet(featureLabelRes: Int, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarrbiTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Layered accent badge: a soft outer halo behind a tinted disc holding the icon.
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
                        imageVector = Icons.Rounded.RocketLaunch,
                        contentDescription = null,
                        tint = DarrbiTheme.colors.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.coming_soon_title),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.coming_soon_message, stringResource(featureLabelRes)),
                style = DarrbiTheme.typography.body,
                color = DarrbiTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.coming_soon_action),
                onClick = onDismiss,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
