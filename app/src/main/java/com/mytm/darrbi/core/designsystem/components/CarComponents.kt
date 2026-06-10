package com.mytm.darrbi.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/** Outlined secondary CTA (e.g. "See Details") — white with a border + dark text. */
@Composable
fun DarrbiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DarrbiTheme.colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DarrbiTheme.colors.onSurface),
    ) {
        Text(text = text, style = DarrbiTheme.typography.button)
    }
}

/** Themed switch — green track when on, grey when off. */
@Composable
fun DarrbiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = DarrbiTheme.colors.onPrimary,
            checkedTrackColor = DarrbiTheme.colors.primary,
            checkedBorderColor = DarrbiTheme.colors.primary,
            uncheckedThumbColor = DarrbiTheme.colors.surface,
            uncheckedTrackColor = DarrbiTheme.colors.buttonDisabledContainer,
            uncheckedBorderColor = DarrbiTheme.colors.buttonDisabledContainer,
        ),
    )
}

// KSA license plate — a fixed real-world artifact, so its cream/black/yellow live here in the design system.
private val PlateBackground = Color(0xFFF1EEE2)
private val PlateInk = Color(0xFF111111)
private val PlateBadge = Color(0xFFF3C200)

@Composable
fun LicensePlate(numbers: String, letters: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PlateBackground)
            .border(2.dp, PlateInk, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = numbers, color = PlateInk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = "KSA",
            color = PlateInk,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(PlateBadge)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(text = letters, color = PlateInk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}
