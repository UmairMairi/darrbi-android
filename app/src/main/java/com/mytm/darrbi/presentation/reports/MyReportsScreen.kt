package com.mytm.darrbi.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.domain.model.UserReport

/** Rating options shown in the Rate Us sheet, mapped to a 1–5 score. */
private val RATINGS = listOf(
    R.string.rate_excellent to 5,
    R.string.rate_best to 4,
    R.string.rate_good to 3,
    R.string.rate_average to 2,
    R.string.rate_poor to 1,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReportsScreen(
    onBack: () -> Unit,
    viewModel: MyReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
            Text(stringResource(R.string.my_reports_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.reports.forEach { report ->
                    ReportCard(report) { viewModel.onEvent(MyReportsEvent.OpenRate(report)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.ratingTarget != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(MyReportsEvent.DismissRate) },
            sheetState = sheetState,
            containerColor = DarrbiTheme.colors.surface,
        ) {
            RateUsSheet(state.selectedRating, viewModel::onEvent)
        }
    }
}

@Composable
private fun ReportCard(report: UserReport, onRate: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.issueType, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
                    Text(stringResource(R.string.report_issue_type), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(if (report.solved) R.string.report_status_solved else R.string.report_status_pending),
                        style = DarrbiTheme.typography.bodyMedium,
                        color = if (report.solved) DarrbiTheme.colors.primary else DarrbiTheme.colors.warning,
                    )
                    Text(stringResource(R.string.report_status_label), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                }
            }
            DividerRow(stringResource(R.string.report_date_time), formatReportDateTime(report.dateTimeIso))
            DividerRow(stringResource(R.string.report_description), report.description, valueBold = true)
            if (report.solved) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DarrbiTheme.colors.outline)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.report_rating), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (report.rating != null) {
                        Text(ratingLabel(report.rating), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarrbiTheme.colors.primary.copy(alpha = 0.12f),
                            modifier = Modifier.clickable(onClick = onRate),
                        ) {
                            Text(
                                text = stringResource(R.string.report_rate_us),
                                style = DarrbiTheme.typography.label,
                                color = DarrbiTheme.colors.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DividerRow(label: String, value: String, valueBold: Boolean = false) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = DarrbiTheme.colors.outline)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = if (valueBold) DarrbiTheme.typography.bodyMedium else DarrbiTheme.typography.bodyMedium,
            color = DarrbiTheme.colors.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun RateUsSheet(selected: Int, onEvent: (MyReportsEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
        Text(stringResource(R.string.rate_us_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.rate_us_subtitle), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        RATINGS.forEach { (labelRes, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onEvent(MyReportsEvent.SelectRating(value)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == value,
                    onClick = { onEvent(MyReportsEvent.SelectRating(value)) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = DarrbiTheme.colors.primary,
                        unselectedColor = DarrbiTheme.colors.outline,
                    ),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(labelRes), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
            }
            HorizontalDivider(color = DarrbiTheme.colors.outline)
        }
        Spacer(Modifier.height(20.dp))
        DarrbiPrimaryButton(text = stringResource(R.string.common_submit), onClick = { onEvent(MyReportsEvent.SubmitRating) })
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ratingLabel(rating: Int): String =
    stringResource(RATINGS.firstOrNull { it.second == rating }?.first ?: R.string.rate_good)

/** ISO → "07/08/22 09:41 PM". */
private fun formatReportDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val inputs = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm")
    for (p in inputs) {
        runCatching {
            val date = java.text.SimpleDateFormat(p, java.util.Locale.ENGLISH).parse(iso) ?: return@runCatching
            return java.text.SimpleDateFormat("dd/MM/yy hh:mm a", java.util.Locale.ENGLISH).format(date)
        }
    }
    return ""
}
