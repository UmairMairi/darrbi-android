package com.mytm.darrbi.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/**
 * White rounded bottom-sheet card used across the onboarding flow (over the black + logo backdrop).
 *
 * When the card is the bottom-most element over a full-screen map (e.g. the captain dashboard), set
 * [navigationBarPadding] so the content clears the system navigation bar — the card stays flush to the
 * screen edge while its content is inset above the nav bar. Leave it off when an ancestor already insets
 * (the onboarding column applies `navigationBarsPadding()` itself).
 */
@Composable
fun DarrbiCard(
    modifier: Modifier = Modifier,
    navigationBarPadding: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (navigationBarPadding) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            content = content,
        )
    }
}
