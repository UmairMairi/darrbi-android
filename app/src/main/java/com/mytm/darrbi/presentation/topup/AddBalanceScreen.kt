package com.mytm.darrbi.presentation.topup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField

private enum class AddBalanceStage { Input, Success, Failed }

/**
 * "Add Balance" (topup) — a bottom sheet stacked over the profile screen (opened from the Balance card's
 * Topup button). No Apple Pay button (Android). On success/failure it swaps to a result state in the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBalanceSheet(onDismiss: () -> Unit) {
    var stage by remember { mutableStateOf(AddBalanceStage.Input) }
    var amount by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarrbiTheme.colors.surface,
    ) {
        when (stage) {
            AddBalanceStage.Input -> InputContent(
                amount = amount,
                onAmountChange = { new -> amount = new.filter { it.isDigit() } },
                onAddBalance = { stage = AddBalanceStage.Success },
            )
            AddBalanceStage.Success -> ResultContent(
                ringColor = DarrbiTheme.colors.primary,
                icon = Icons.Filled.Check,
                title = stringResource(R.string.topup_success_title),
                subtitle = stringResource(R.string.topup_success_subtitle, amount),
                primaryText = stringResource(R.string.common_done),
                onPrimary = onDismiss,
            )
            AddBalanceStage.Failed -> ResultContent(
                ringColor = DarrbiTheme.colors.error,
                icon = Icons.Filled.Close,
                title = stringResource(R.string.topup_failed_title),
                subtitle = stringResource(R.string.topup_failed_subtitle),
                primaryText = stringResource(R.string.common_done),
                onPrimary = onDismiss,
                secondaryText = stringResource(R.string.common_try_again),
                onSecondary = { stage = AddBalanceStage.Input },
            )
        }
    }
}

@Composable
private fun InputContent(amount: String, onAmountChange: (String) -> Unit, onAddBalance: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.addbalance_title),
            style = DarrbiTheme.typography.titleLarge,
            color = DarrbiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        DarrbiTextField(
            value = amount,
            onValueChange = onAmountChange,
            label = stringResource(R.string.addbalance_enter_amount),
            keyboardType = KeyboardType.Number,
            trailingContent = {
                Text(
                    text = stringResource(R.string.profile_currency_sar),
                    style = DarrbiTheme.typography.button,
                    color = DarrbiTheme.colors.primary,
                )
            },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(10, 50, 100).forEach { value ->
                QuickAmountChip(value, { onAmountChange(value.toString()) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.addbalance_saved_cards),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.addbalance_add_new),
                style = DarrbiTheme.typography.button,
                color = DarrbiTheme.colors.primary,
                modifier = Modifier.clickable {},
            )
        }
        Text(
            text = stringResource(R.string.addbalance_no_cards),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
        DarrbiPrimaryButton(
            text = stringResource(R.string.addbalance_add_balance),
            enabled = amount.isNotBlank(),
            onClick = onAddBalance,
        )
    }
}

@Composable
private fun QuickAmountChip(amount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = DarrbiTheme.colors.surfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.addbalance_quick, amount),
            style = DarrbiTheme.typography.bodyMedium,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun ResultContent(
    ringColor: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        ResultBadge(ringColor = ringColor, icon = icon)
        Spacer(Modifier.height(24.dp))
        Text(title, style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        DarrbiPrimaryButton(text = primaryText, onClick = onPrimary)
        if (secondaryText != null && onSecondary != null) {
            Spacer(Modifier.height(12.dp))
            DarrbiSecondaryButton(text = secondaryText, onClick = onSecondary)
        }
    }
}

@Composable
private fun ResultBadge(ringColor: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(132.dp)
            .drawBehind {
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(104.dp).clip(CircleShape).background(ringColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = DarrbiTheme.colors.onPrimary, modifier = Modifier.size(48.dp))
        }
    }
}
