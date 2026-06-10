package com.mytm.darrbi.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton

/**
 * Full "application under review" detail screen reached from the dashboard's "See Details".
 * Mirrors ride-android's UnderReviewDetailActivity: status header + support actions + Done.
 */
@Composable
fun CaptainStatusDetailScreen(
    onDone: () -> Unit,
    onCallCustomerCare: () -> Unit = {},
    onWhatsappCare: () -> Unit = {},
    onReportProblem: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surface) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StatusClockBadge()
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.status_detail_title),
                    style = DarrbiTheme.typography.titleLarge,
                    color = DarrbiTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.status_detail_subtitle),
                    style = DarrbiTheme.typography.body,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                SupportCard(
                    onCallCustomerCare = onCallCustomerCare,
                    onWhatsappCare = onWhatsappCare,
                    onReportProblem = onReportProblem,
                )
            }
            DarrbiPrimaryButton(
                text = stringResource(R.string.common_done),
                onClick = onDone,
            )
        }
    }
}

@Composable
private fun StatusClockBadge() {
    val ringColor = DarrbiTheme.colors.warning
    Box(
        modifier = Modifier
            .size(132.dp)
            .drawBehind {
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2f,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(DarrbiTheme.colors.warning),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = DarrbiTheme.colors.onPrimary,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun SupportCard(
    onCallCustomerCare: () -> Unit,
    onWhatsappCare: () -> Unit,
    onReportProblem: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Column {
            SupportRow(
                icon = Icons.Filled.Call,
                label = stringResource(R.string.support_call_customer_care),
                onClick = onCallCustomerCare,
            )
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            SupportRow(
                icon = Icons.Filled.Chat,
                label = stringResource(R.string.support_whatsapp_care),
                onClick = onWhatsappCare,
            )
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            SupportRow(
                icon = Icons.Filled.BugReport,
                label = stringResource(R.string.support_report_problem),
                onClick = onReportProblem,
            )
        }
    }
}

@Composable
private fun SupportRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DarrbiTheme.colors.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = DarrbiTheme.typography.bodyMedium,
            color = DarrbiTheme.colors.onSurface,
        )
    }
}
