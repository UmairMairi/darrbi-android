package com.mytm.darrbi.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/** A list option that draws a green rounded border when [selected] (e.g. language / rider-vs-captain). */
@Composable
fun DarrbiSelectableOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) DarrbiTheme.colors.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = DarrbiTheme.typography.bodyMedium,
            color = DarrbiTheme.colors.onSurface,
        )
    }
}

/** Custom checkbox box using the brand checked/unchecked drawables. */
@Composable
fun DarrbiCheckboxBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(
            if (checked) R.drawable.checkbox_checked else R.drawable.checkbox_unchecked,
        ),
        contentDescription = null,
        modifier = modifier
            .size(20.dp)
            .clickable { onCheckedChange(!checked) },
    )
}

/** Custom checkbox + label row (e.g. "Get OTP on WhatsApp"). */
@Composable
fun DarrbiCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DarrbiCheckboxBox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurface,
        )
    }
}
