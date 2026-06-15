package com.mytm.darrbi.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/** Active app mode shown in the dashboard toggle. */
enum class RideMode { Rider, Captain }

/**
 * RIDER / CAPTAIN segmented pill (matches the dashboard reference): a dark container with the active
 * segment on a green background. Tapping the inactive segment reports it via [onSelect].
 */
@Composable
fun ModeToggle(
    selected: RideMode,
    onSelect: (RideMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarrbiTheme.colors.buttonContainer)
            .padding(2.dp),
    ) {
        ModeSegment(
            label = stringResource(R.string.mode_rider),
            active = selected == RideMode.Rider,
            onClick = { if (enabled) onSelect(RideMode.Rider) },
        )
        ModeSegment(
            label = stringResource(R.string.mode_captain),
            active = selected == RideMode.Captain,
            onClick = { if (enabled) onSelect(RideMode.Captain) },
        )
    }
}

@Composable
private fun ModeSegment(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        // Button token is 16sp bold; the toggle uses it 2sp smaller and a tighter width.
        style = DarrbiTheme.typography.button.copy(fontSize = 14.sp),
        color = if (active) DarrbiTheme.colors.onSurface else DarrbiTheme.colors.onButton,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) DarrbiTheme.colors.modeAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}
