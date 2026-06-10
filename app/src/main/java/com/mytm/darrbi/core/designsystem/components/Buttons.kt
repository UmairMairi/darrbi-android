package com.mytm.darrbi.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/** Primary CTA — black filled, rounded, full width (matches "Next"/"Verify"). Grey when disabled. */
@Composable
fun DarrbiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DarrbiTheme.colors.buttonContainer,
            contentColor = DarrbiTheme.colors.onButton,
            disabledContainerColor = DarrbiTheme.colors.buttonDisabledContainer,
            disabledContentColor = DarrbiTheme.colors.onButtonDisabled,
        ),
    ) {
        Text(text = text, style = DarrbiTheme.typography.button)
    }
}
