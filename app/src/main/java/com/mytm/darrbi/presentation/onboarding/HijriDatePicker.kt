package com.mytm.darrbi.presentation.onboarding

import android.icu.util.IslamicCalendar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import java.util.Locale

private data class HijriYmd(val year: Int, val month: Int, val day: Int) // month: 0..11

private fun umalqura(): IslamicCalendar =
    IslamicCalendar().apply { calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA }

private fun todayHijri(): HijriYmd {
    val c = umalqura().apply { timeInMillis = System.currentTimeMillis() }
    return HijriYmd(c.get(IslamicCalendar.YEAR), c.get(IslamicCalendar.MONTH), c.get(IslamicCalendar.DAY_OF_MONTH))
}

private fun daysInMonth(year: Int, month: Int): Int = umalqura().apply {
    clear(); set(IslamicCalendar.YEAR, year); set(IslamicCalendar.MONTH, month); set(IslamicCalendar.DAY_OF_MONTH, 1)
}.getActualMaximum(IslamicCalendar.DAY_OF_MONTH)

/** Weekday index of the 1st of the month, 0 = Sunday .. 6 = Saturday. */
private fun firstWeekday(year: Int, month: Int): Int = umalqura().apply {
    clear(); set(IslamicCalendar.YEAR, year); set(IslamicCalendar.MONTH, month); set(IslamicCalendar.DAY_OF_MONTH, 1)
}.get(IslamicCalendar.DAY_OF_WEEK) - 1

/**
 * Hijri (Umm al-Qura) date picker in a bottom sheet. Calls [onConfirm] with a `dd-MM-yyyy` Hijri string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HijriDatePickerSheet(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { todayHijri() }
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.month) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    val monthNames = stringArrayResource(R.array.hijri_months)
    val weekdays = stringArrayResource(R.array.weekday_initials)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarrbiTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.date_picker_title),
                    style = DarrbiTheme.typography.title,
                    color = DarrbiTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = DarrbiTheme.colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Month / year navigation (can't go before the current Hijri month).
            val canGoPrev = year > today.year || (year == today.year && month > today.month)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = canGoPrev,
                    onClick = {
                        if (month == 0) { month = 11; year -= 1 } else month -= 1
                        selectedDay = null
                    },
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = if (canGoPrev) DarrbiTheme.colors.onSurface else DarrbiTheme.colors.outline,
                    )
                }
                Text(
                    text = "${monthNames.getOrElse(month) { "" }} $year",
                    style = DarrbiTheme.typography.bodyMedium,
                    color = DarrbiTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (month == 11) { month = 0; year += 1 } else month += 1
                    selectedDay = null
                }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = DarrbiTheme.colors.onSurface)
                }
            }
            Spacer(Modifier.height(4.dp))

            // Weekday headers
            Row(Modifier.fillMaxWidth()) {
                weekdays.forEach { wd ->
                    Text(
                        text = wd,
                        style = DarrbiTheme.typography.label,
                        color = DarrbiTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // Day grid
            val lead = firstWeekday(year, month)
            val count = daysInMonth(year, month)
            val weeks = (lead + count + 6) / 7
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (dow in 0 until 7) {
                        val dayNum = w * 7 + dow - lead + 1
                        if (dayNum in 1..count) {
                            val selected = selectedDay == dayNum
                            // Expiry must be today or later — past days are disabled.
                            val past = year < today.year ||
                                (year == today.year && month < today.month) ||
                                (year == today.year && month == today.month && dayNum < today.day)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) DarrbiTheme.colors.primary else Color.Transparent)
                                    .clickable(enabled = !past) { selectedDay = dayNum },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    style = DarrbiTheme.typography.bodyMedium,
                                    color = when {
                                        selected -> DarrbiTheme.colors.onPrimary
                                        past -> DarrbiTheme.colors.outline
                                        else -> DarrbiTheme.colors.onSurface
                                    },
                                )
                            }
                        } else {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.common_confirm),
                enabled = selectedDay != null,
                onClick = {
                    selectedDay?.let { day ->
                        onConfirm(String.format(Locale.ENGLISH, "%02d-%02d-%04d", day, month + 1, year))
                    }
                },
            )
        }
    }
}
