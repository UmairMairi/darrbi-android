package com.mytm.darrbi.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiCard
import com.mytm.darrbi.domain.model.CancelReason

/**
 * Bottom sheet shown when a rider or captain cancels an accepted trip: "Canceling Ride?" + a radio list of
 * reasons (from `master/rejected-reason`), and a red confirm button. The confirm is disabled until a reason
 * is picked. Used by both sides — only the reasons list and [confirmText] differ.
 */
@Composable
fun CancelReasonsSheet(
    reasons: List<CancelReason>,
    selectedId: String?,
    isLoading: Boolean,
    isSubmitting: Boolean,
    confirmText: String,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    DarrbiCard(modifier = modifier, navigationBarPadding = true) {
        Text(
            text = stringResource(R.string.cancel_reasons_title),
            style = DarrbiTheme.typography.titleLarge,
            color = DarrbiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.cancel_reasons_subtitle),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (isLoading && reasons.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarrbiTheme.colors.primary, modifier = Modifier.size(28.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                reasons.forEachIndexed { index, reason ->
                    ReasonRow(
                        label = if (isArabic) (reason.textArabic ?: reason.text) else reason.text,
                        selected = reason.id == selectedId,
                        onClick = { onSelect(reason.id) },
                    )
                    if (index < reasons.lastIndex) HorizontalDivider(color = DarrbiTheme.colors.outline)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val enabled = selectedId != null && !isSubmitting
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onSubmit),
            shape = RoundedCornerShape(14.dp),
            color = if (enabled) DarrbiTheme.colors.error else DarrbiTheme.colors.error.copy(alpha = 0.5f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = DarrbiTheme.colors.onError, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirmText, style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onError, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ReasonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) DarrbiTheme.colors.primary else DarrbiTheme.colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(DarrbiTheme.colors.primary))
            }
        }
        Spacer(Modifier.size(14.dp))
        Text(text = label, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}
