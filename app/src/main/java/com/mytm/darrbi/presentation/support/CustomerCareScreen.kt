package com.mytm.darrbi.presentation.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/** Customer care screen: call, WhatsApp and report-a-problem support actions. */
@Composable
fun CustomerCareScreen(
    onBack: () -> Unit,
    onCallCustomerCare: () -> Unit,
    onWhatsapp: () -> Unit = {},
    onReportProblem: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarrbiTheme.colors.surface)
                    .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_back), tint = DarrbiTheme.colors.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.menu_customer_care),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(24.dp))
            SupportCard(
                onCallCustomerCare = onCallCustomerCare,
                onWhatsapp = onWhatsapp,
                onReportProblem = onReportProblem,
            )
        }
    }
}

@Composable
private fun SupportCard(
    onCallCustomerCare: () -> Unit,
    onWhatsapp: () -> Unit,
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
                onClick = onWhatsapp,
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
