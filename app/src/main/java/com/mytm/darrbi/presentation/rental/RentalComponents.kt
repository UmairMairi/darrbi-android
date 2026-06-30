package com.mytm.darrbi.presentation.rental

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiCard
import com.mytm.darrbi.core.designsystem.components.DarrbiCheckbox
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField
import com.mytm.darrbi.domain.model.CdwTierOption
import com.mytm.darrbi.domain.model.PickupBranch
import com.mytm.darrbi.domain.model.RentalBooking
import com.mytm.darrbi.domain.model.RentalBookingDetail
import com.mytm.darrbi.domain.model.RentalBookingStatus
import com.mytm.darrbi.domain.model.RentalSort
import com.mytm.darrbi.domain.model.RentalVehicleDetail
import com.mytm.darrbi.domain.model.RentalVehicleSummary
import com.mytm.darrbi.presentation.rider.formatFare
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.input.KeyboardType

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/** "SAR 320" — the company-country currency followed by the formatted amount. */
@Composable
internal fun money(currency: String, value: Double): String = "$currency ${formatFare(value)}"

/** A friendly label for a Derrbi CDW tier; unknown tiers fall back to their capitalized wire value. */
@Composable
internal fun cdwTierLabel(tier: String): String = when (tier.lowercase(Locale.US)) {
    "none" -> stringResource(R.string.rental_cdw_tier_none)
    "basic" -> stringResource(R.string.rental_cdw_tier_basic)
    "standard" -> stringResource(R.string.rental_cdw_tier_standard)
    "premium" -> stringResource(R.string.rental_cdw_tier_premium)
    else -> tier.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

@Composable
internal fun transmissionLabel(value: String?): String = when (value?.lowercase(Locale.US)) {
    "automatic" -> stringResource(R.string.rental_transmission_automatic)
    "manual" -> stringResource(R.string.rental_transmission_manual)
    else -> value?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }.orEmpty()
}

@Composable
internal fun bookingStatusLabel(status: RentalBookingStatus): String = when (status) {
    RentalBookingStatus.Pending -> stringResource(R.string.rental_status_pending)
    RentalBookingStatus.Active -> stringResource(R.string.rental_status_active)
    RentalBookingStatus.Completed -> stringResource(R.string.rental_status_completed)
    RentalBookingStatus.LateReturn -> stringResource(R.string.rental_status_late)
    RentalBookingStatus.CancelledByRenter, RentalBookingStatus.CancelledByCompany -> stringResource(R.string.rental_status_cancelled)
    RentalBookingStatus.Disputed -> stringResource(R.string.rental_status_disputed)
    RentalBookingStatus.Unknown -> stringResource(R.string.rental_status_unknown)
}

/** Resolve a VM sentinel ([RentalViewModel] CODE_*) to a localized message; otherwise pass through the text. */
@Composable
internal fun rentalMessageText(code: String?): String? {
    code ?: return null
    return when (code) {
        RentalViewModel.CODE_NOT_AVAILABLE -> stringResource(R.string.rental_err_not_available)
        RentalViewModel.CODE_PAYMENT_FAILED -> stringResource(R.string.rental_err_payment_failed)
        RentalViewModel.CODE_GUEST -> stringResource(R.string.rental_err_guest)
        RentalViewModel.CODE_INVALID_DATES -> stringResource(R.string.rental_err_invalid_dates)
        RentalViewModel.CODE_GENERIC -> stringResource(R.string.rental_err_generic)
        RentalViewModel.CODE_NAFATH_FAILED -> stringResource(R.string.rental_nafath_failed)
        RentalViewModel.CODE_CANCELLED -> stringResource(R.string.rental_cancelled)
        RentalViewModel.CODE_EXTENSION_REQUESTED -> stringResource(R.string.rental_extension_requested)
        RentalViewModel.CODE_RATED -> stringResource(R.string.rental_rated)
        else -> code
    }
}

@Composable
internal fun formatRentalDateTime(millis: Long?): String {
    millis ?: return "—"
    val fmt = SimpleDateFormat("MMM d, h:mm a", LocalConfiguration.current.locales[0])
    return fmt.format(Date(millis))
}

@Composable
internal fun formatRentalDate(millis: Long?): String {
    millis ?: return "—"
    val fmt = SimpleDateFormat("MMM d", LocalConfiguration.current.locales[0])
    return fmt.format(Date(millis))
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
internal fun RentalHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(DarrbiTheme.colors.surface)
                .border(1.dp, DarrbiTheme.colors.outline, CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_back), tint = DarrbiTheme.colors.onSurface)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
        if (action != null) action()
    }
}

// ---------------------------------------------------------------------------
// Small atoms
// ---------------------------------------------------------------------------

@Composable
internal fun RentalSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = DarrbiTheme.typography.title.copy(fontSize = 16.sp), color = DarrbiTheme.colors.onSurface, modifier = modifier)
}

@Composable
internal fun RentalKeyValueRow(label: String, value: String, emphasize: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (emphasize) DarrbiTheme.typography.title.copy(fontSize = 16.sp) else DarrbiTheme.typography.bodyMedium,
            color = if (emphasize) DarrbiTheme.colors.primary else DarrbiTheme.colors.onSurface,
        )
    }
}

/** Selectable rounded pill (filters / sort / CDW). */
@Composable
internal fun RentalChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) DarrbiTheme.colors.surface else DarrbiTheme.colors.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) DarrbiTheme.colors.primary else DarrbiTheme.colors.outline),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            style = DarrbiTheme.typography.bodyMedium,
            color = if (selected) DarrbiTheme.colors.primary else DarrbiTheme.colors.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun RatingPill(avg: Double?, count: Int?) {
    if (avg == null || avg <= 0.0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = DarrbiTheme.colors.warning, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.rental_rating_format, formatFare(avg), count ?: 0),
            style = DarrbiTheme.typography.caption,
            color = DarrbiTheme.colors.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Search home
// ---------------------------------------------------------------------------

@Composable
internal fun RentalSearchHome(
    state: RentalUiState,
    onEvent: (RentalEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(DarrbiTheme.colors.surfaceVariant)
            .statusBarsPadding().verticalScroll(rememberScrollState()),
    ) {
        // Brand row + My Bookings.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.rental_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DarrbiTheme.colors.surface,
                border = BorderStroke(1.dp, DarrbiTheme.colors.outline),
                modifier = Modifier.clickable { onEvent(RentalEvent.OpenBookings) },
            ) {
                Text(
                    stringResource(R.string.rental_my_bookings),
                    style = DarrbiTheme.typography.label,
                    color = DarrbiTheme.colors.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // Search card.
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarrbiTheme.colors.surface,
            shadowElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // City field (opens the city picker if filters provide cities).
                FieldRow(
                    icon = Icons.Filled.LocationOn,
                    text = state.selectedCity ?: stringResource(R.string.rental_location_hint),
                    placeholder = state.selectedCity == null,
                    onClick = { /* city is chosen below via chips */ },
                )
                val cities = state.filters?.cities.orEmpty()
                if (cities.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cities.forEach { city ->
                            RentalChip(text = city, selected = city == state.selectedCity, onClick = { onEvent(RentalEvent.SelectCity(city)) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Date & time.
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarrbiTheme.colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { onEvent(RentalEvent.OpenDateSheet) },
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(DarrbiTheme.colors.surface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.DateRange, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.rental_date_time), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                            Text(
                                if (state.datesValid) {
                                    "${formatRentalDateTime(state.pickupAtMillis)}  →  ${formatRentalDateTime(state.returnAtMillis)}"
                                } else {
                                    stringResource(R.string.rental_select_date_time)
                                },
                                style = DarrbiTheme.typography.bodyMedium,
                                color = DarrbiTheme.colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                DarrbiPrimaryButton(
                    text = stringResource(R.string.rental_search),
                    enabled = !state.isSearching,
                    onClick = { onEvent(RentalEvent.Search) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        // Fast pickup / Easy return.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile(stringResource(R.string.rental_fast_pickup_title), stringResource(R.string.rental_fast_pickup_sub), Modifier.weight(1f))
            InfoTile(stringResource(R.string.rental_easy_return_title), stringResource(R.string.rental_easy_return_sub), Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        RentalSectionTitle(stringResource(R.string.rental_how_it_works), Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        val steps = listOf(
            stringResource(R.string.rental_hiw_1),
            stringResource(R.string.rental_hiw_2),
            stringResource(R.string.rental_hiw_3),
            stringResource(R.string.rental_hiw_4),
            stringResource(R.string.rental_hiw_5),
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            steps.forEachIndexed { index, step -> HowItWorksStep(index + 1, step) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FieldRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, placeholder: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarrbiTheme.colors.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = DarrbiTheme.typography.bodyMedium,
                color = if (placeholder) DarrbiTheme.colors.onSurfaceVariant else DarrbiTheme.colors.onSurface,
            )
        }
    }
}

@Composable
private fun InfoTile(title: String, subtitle: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.surface, modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun HowItWorksStep(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).border(1.dp, DarrbiTheme.colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(String.format(Locale.US, "%02d", number), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------
// Date + time range picker
// ---------------------------------------------------------------------------

@Composable
internal fun RentalDateRangeSheet(
    initialPickup: Long?,
    initialReturn: Long?,
    onConfirm: (pickup: Long, ret: Long) -> Unit,
) {
    val nowMillis = remember { System.currentTimeMillis() }
    val minMillis = remember { nowMillis + RentalViewModel.MIN_LEAD_MINUTES * 60_000L }
    val seed = remember { Calendar.getInstance().apply { timeInMillis = initialPickup ?: (minMillis + 30 * 60_000L) } }

    var viewYear by remember { mutableIntStateOf(seed.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(seed.get(Calendar.MONTH)) }

    // Selected day-starts (epoch millis at 00:00) for pickup + return.
    var pickupDay by remember { mutableStateOf(initialPickup?.let(::dayStart)) }
    var returnDay by remember { mutableStateOf(initialReturn?.let(::dayStart)) }

    var pickupHour by remember { mutableIntStateOf(10) }
    var pickupPm by remember { mutableStateOf(false) }
    var returnHour by remember { mutableIntStateOf(10) }
    var returnPm by remember { mutableStateOf(false) }

    fun combine(dayStartMillis: Long, hour12: Int, pm: Boolean): Long = Calendar.getInstance().apply {
        timeInMillis = dayStartMillis
        set(Calendar.HOUR, hour12 % 12)
        set(Calendar.AM_PM, if (pm) Calendar.PM else Calendar.AM)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val pickupMillis = pickupDay?.let { combine(it, pickupHour, pickupPm) }
    val returnMillis = returnDay?.let { combine(it, returnHour, returnPm) }
    val valid = pickupMillis != null && returnMillis != null && returnMillis > pickupMillis && pickupMillis >= minMillis

    val locale = LocalConfiguration.current.locales[0]
    val monthLabel = remember(viewYear, viewMonth) {
        SimpleDateFormat("MMMM yyyy", locale).format(
            Calendar.getInstance().apply { set(Calendar.YEAR, viewYear); set(Calendar.MONTH, viewMonth); set(Calendar.DAY_OF_MONTH, 1) }.time,
        )
    }

    DarrbiCard(navigationBarPadding = true) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.rental_date_time),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CalArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = !monthAtOrBeforeNow(viewYear, viewMonth)) {
                    if (viewMonth == 0) { viewMonth = 11; viewYear -= 1 } else viewMonth -= 1
                }
                Text(monthLabel, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                CalArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = true) {
                    if (viewMonth == 11) { viewMonth = 0; viewYear += 1 } else viewMonth += 1
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayInitials(locale).forEach { d ->
                    Text(d, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            RentalCalendarGrid(
                year = viewYear,
                month = viewMonth,
                nowMillis = nowMillis,
                pickupDay = pickupDay,
                returnDay = returnDay,
                onPick = { dayMillis ->
                    val p = pickupDay
                    when {
                        p == null || returnDay != null -> { pickupDay = dayMillis; returnDay = null }
                        dayMillis < p -> pickupDay = dayMillis
                        dayMillis == p -> Unit
                        else -> returnDay = dayMillis
                    }
                },
            )
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.schedule_time_label), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column {
                    RentalTimeRow(stringResource(R.string.rental_pickup_at), pickupHour, pickupPm, { pickupHour = it }, { pickupPm = it })
                    HorizontalDivider(color = DarrbiTheme.colors.outline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    RentalTimeRow(stringResource(R.string.rental_return_at), returnHour, returnPm, { returnHour = it }, { returnPm = it })
                }
            }
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.rental_apply),
                enabled = valid,
                onClick = { if (pickupMillis != null && returnMillis != null) onConfirm(pickupMillis, returnMillis) },
            )
        }
    }
}

private fun dayStart(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun monthAtOrBeforeNow(year: Int, month: Int): Boolean {
    val now = Calendar.getInstance()
    return year < now.get(Calendar.YEAR) || (year == now.get(Calendar.YEAR) && month <= now.get(Calendar.MONTH))
}

private fun weekdayInitials(locale: Locale): List<String> {
    val fmt = SimpleDateFormat("EEEEE", locale)
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    return (0..6).map { fmt.format(cal.time).also { cal.add(Calendar.DAY_OF_WEEK, 1) } }
}

@Composable
private fun CalArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) DarrbiTheme.colors.onSurface else DarrbiTheme.colors.onSurfaceVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun RentalCalendarGrid(
    year: Int,
    month: Int,
    nowMillis: Long,
    pickupDay: Long?,
    returnDay: Long?,
    onPick: (Long) -> Unit,
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply { set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, 1) }
    }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leading = firstDow - Calendar.SUNDAY
    val rows = (leading + daysInMonth + 6) / 7
    val todayStart = remember(nowMillis) { dayStart(nowMillis) }
    Column(modifier = Modifier.fillMaxWidth()) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - leading + 1
                    if (day in 1..daysInMonth) {
                        val dayMillis = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.timeInMillis
                        val isPast = dayMillis < todayStart
                        val isPickup = dayMillis == pickupDay
                        val isReturn = dayMillis == returnDay
                        val inRange = pickupDay != null && returnDay != null && dayMillis > pickupDay && dayMillis < returnDay
                        val endpoint = isPickup || isReturn
                        Box(
                            modifier = Modifier.weight(1f).height(42.dp).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        endpoint -> DarrbiTheme.colors.primary
                                        inRange -> DarrbiTheme.colors.primary.copy(alpha = 0.14f)
                                        else -> DarrbiTheme.colors.surface
                                    },
                                )
                                .clickable(enabled = !isPast) { onPick(dayMillis) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.toString(),
                                style = DarrbiTheme.typography.body,
                                color = when {
                                    endpoint -> DarrbiTheme.colors.onPrimary
                                    isPast -> DarrbiTheme.colors.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> DarrbiTheme.colors.onSurface
                                },
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).height(42.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Date-of-birth picker — same calendar design as the rental date picker, but a
// single past-date selection (future days disabled) with a year stepper.
// ---------------------------------------------------------------------------

/** Default year the calendar opens on when no DOB is set yet (≈ a typical adult age). */
private const val DOB_DEFAULT_AGE = 20

@Composable
internal fun RentalDobSheet(
    initialMillis: Long?,
    onConfirm: (Long) -> Unit,
) {
    val nowMillis = remember { System.currentTimeMillis() }
    val seed = remember {
        Calendar.getInstance().apply {
            timeInMillis = initialMillis ?: nowMillis
            if (initialMillis == null) add(Calendar.YEAR, -DOB_DEFAULT_AGE)
        }
    }
    var viewYear by remember { mutableIntStateOf(seed.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(seed.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(initialMillis?.let(::dayStart)) }
    // Whether the tappable year is expanded into the year-list picker.
    var showYearList by remember { mutableStateOf(false) }

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val currentMonth = remember { Calendar.getInstance().get(Calendar.MONTH) }
    val locale = LocalConfiguration.current.locales[0]
    val monthName = remember(viewYear, viewMonth) {
        SimpleDateFormat("MMMM", locale).format(
            Calendar.getInstance().apply { set(Calendar.YEAR, viewYear); set(Calendar.MONTH, viewMonth); set(Calendar.DAY_OF_MONTH, 1) }.time,
        )
    }

    DarrbiCard(navigationBarPadding = true) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.rental_field_dob),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
            )
            // Header: tappable year (top-left, opens the year list) + month nav on the right.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                CalendarYearTab(year = viewYear, expanded = showYearList) { showYearList = !showYearList }
                Spacer(Modifier.weight(1f))
                if (!showYearList) {
                    CalArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = true) {
                        if (viewMonth == 0) { viewMonth = 11; viewYear -= 1 } else viewMonth -= 1
                    }
                    Text(monthName, style = DarrbiTheme.typography.title.copy(fontSize = 16.sp), color = DarrbiTheme.colors.onSurface, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 96.dp))
                    CalArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = !monthAtOrAfterNow(viewYear, viewMonth)) {
                        if (viewMonth == 11) { viewMonth = 0; viewYear += 1 } else viewMonth += 1
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (showYearList) {
                // Pick a year to jump the calendar; DOB can't be in the future, so cap at the current year.
                YearPickerGrid(
                    selectedYear = viewYear,
                    minYear = currentYear - 100,
                    maxYear = currentYear,
                    onPick = { picked ->
                        viewYear = picked
                        if (viewYear == currentYear && viewMonth > currentMonth) viewMonth = currentMonth
                        showYearList = false
                    },
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdayInitials(locale).forEach { d ->
                        Text(d, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                DobCalendarGrid(
                    year = viewYear,
                    month = viewMonth,
                    nowMillis = nowMillis,
                    selectedDay = selectedDay,
                    onPick = { selectedDay = it },
                )
            }
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.rental_apply),
                enabled = selectedDay != null && !showYearList,
                onClick = { selectedDay?.let(onConfirm) },
            )
        }
    }
}

private fun monthAtOrAfterNow(year: Int, month: Int): Boolean {
    val now = Calendar.getInstance()
    return year > now.get(Calendar.YEAR) || (year == now.get(Calendar.YEAR) && month >= now.get(Calendar.MONTH))
}

/**
 * Tappable year label with a caret, sized to sit in a calendar header's top-left corner. Tapping it is
 * meant to toggle a [YearPickerGrid]. Reusable across any calendar sheet that wants quick year jumps.
 */
@Composable
internal fun CalendarYearTab(year: Int, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (expanded) DarrbiTheme.colors.primary.copy(alpha = 0.12f) else DarrbiTheme.colors.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                year.toString(),
                style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
                color = if (expanded) DarrbiTheme.colors.primary else DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = if (expanded) DarrbiTheme.colors.primary else DarrbiTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/**
 * Reusable scrollable grid of selectable years (newest first), highlighting [selectedYear]. The list
 * opens already scrolled so the selected year sits near the top. Years span [minYear]..[maxYear].
 * Drop this into any calendar sheet to let the user jump years quickly.
 */
@Composable
internal fun YearPickerGrid(
    selectedYear: Int,
    minYear: Int,
    maxYear: Int,
    modifier: Modifier = Modifier,
    onPick: (Int) -> Unit,
) {
    val columns = 3
    val rowHeight = 52.dp
    val years = remember(minYear, maxYear) { (maxYear downTo minYear).toList() }
    val selRow = (years.indexOf(selectedYear).coerceAtLeast(0)) / columns
    val density = LocalDensity.current
    val initialScroll = with(density) { (rowHeight * (selRow - 1).coerceAtLeast(0)).roundToPx() }
    val scroll = rememberScrollState(initial = initialScroll)
    Column(modifier = modifier.fillMaxWidth().height(rowHeight * 5).verticalScroll(scroll)) {
        years.chunked(columns).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { y ->
                    val isSelected = y == selectedYear
                    Box(
                        modifier = Modifier.weight(1f).height(rowHeight).padding(4.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) DarrbiTheme.colors.primary else DarrbiTheme.colors.surfaceVariant)
                            .clickable { onPick(y) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            y.toString(),
                            style = if (isSelected) DarrbiTheme.typography.bodyMedium else DarrbiTheme.typography.body,
                            color = if (isSelected) DarrbiTheme.colors.onPrimary else DarrbiTheme.colors.onSurface,
                        )
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f).height(rowHeight)) }
            }
        }
    }
}

/** Single-select month grid for DOB; mirrors [RentalCalendarGrid] but disables FUTURE days. */
@Composable
private fun DobCalendarGrid(
    year: Int,
    month: Int,
    nowMillis: Long,
    selectedDay: Long?,
    onPick: (Long) -> Unit,
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply { set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, 1) }
    }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leading = firstDow - Calendar.SUNDAY
    val rows = (leading + daysInMonth + 6) / 7
    val todayStart = remember(nowMillis) { dayStart(nowMillis) }
    Column(modifier = Modifier.fillMaxWidth()) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - leading + 1
                    if (day in 1..daysInMonth) {
                        val dayMillis = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.timeInMillis
                        val isFuture = dayMillis > todayStart
                        val isSelected = dayMillis == selectedDay
                        Box(
                            modifier = Modifier.weight(1f).height(42.dp).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) DarrbiTheme.colors.primary else DarrbiTheme.colors.surface)
                                .clickable(enabled = !isFuture) { onPick(dayMillis) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.toString(),
                                style = DarrbiTheme.typography.body,
                                color = when {
                                    isSelected -> DarrbiTheme.colors.onPrimary
                                    isFuture -> DarrbiTheme.colors.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> DarrbiTheme.colors.onSurface
                                },
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).height(42.dp))
                    }
                }
            }
        }
    }
}

/** One form-style time row: label · hour stepper pill · AM/PM segmented toggle. */
@Composable
private fun RentalTimeRow(label: String, hour12: Int, pm: Boolean, onHour: (Int) -> Unit, onPm: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
        // − value + on a single rounded, bordered pill.
        Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surface, border = BorderStroke(1.dp, DarrbiTheme.colors.outline)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeStep(Icons.Filled.Remove) { onHour(if (hour12 == 1) 12 else hour12 - 1) }
                Text(
                    String.format(Locale.US, "%d:00", hour12),
                    style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
                    color = DarrbiTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = 50.dp),
                )
                TimeStep(Icons.Filled.Add) { onHour(if (hour12 == 12) 1 else hour12 + 1) }
            }
        }
        Spacer(Modifier.width(10.dp))
        AmPmToggle(pm = pm, onPm = onPm)
    }
}

/** Borderless circular chevron used inside the time pill (primary-tinted). */
@Composable
private fun TimeStep(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AmPmToggle(pm: Boolean, onPm: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surface, border = BorderStroke(1.dp, DarrbiTheme.colors.outline)) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            AmPmSeg(stringResource(R.string.schedule_am), selected = !pm) { onPm(false) }
            AmPmSeg(stringResource(R.string.schedule_pm), selected = pm) { onPm(true) }
        }
    }
}

@Composable
private fun AmPmSeg(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) DarrbiTheme.colors.primary else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.size(width = 40.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            Text(label, style = DarrbiTheme.typography.label, color = if (selected) DarrbiTheme.colors.onPrimary else DarrbiTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(DarrbiTheme.colors.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

@Composable
internal fun RentalResultsContent(
    state: RentalUiState,
    onEvent: (RentalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loading = state.isSearching && state.vehicles.isEmpty()
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // The count is unknown while searching, so leave it blank until results arrive.
            Text(
                if (loading) "" else stringResource(R.string.rental_total_count, state.total),
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconPillButton(Icons.Filled.DateRange, stringResource(R.string.rental_filter)) { onEvent(RentalEvent.OpenFilter) }
            Spacer(Modifier.width(8.dp))
            IconPillButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.rental_sort)) { onEvent(RentalEvent.OpenSort) }
        }
        // Note banner.
        Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.primary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.rental_results_note), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurface, modifier = Modifier.padding(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> RentalResultsSkeleton(modifier = Modifier.weight(1f))
            state.vehicles.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.rental_no_vehicles), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.vehicles, key = { it.id }) { vehicle ->
                    RentalVehicleCard(vehicle) { onEvent(RentalEvent.OpenVehicle(vehicle.id)) }
                }
            }
        }
    }
}

@Composable
private fun IconPillButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = DarrbiTheme.colors.surface, border = BorderStroke(1.dp, DarrbiTheme.colors.outline), modifier = Modifier.clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurface)
        }
    }
}

@Composable
internal fun RentalVehicleCard(vehicle: RentalVehicleSummary, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarrbiTheme.colors.surface,
        border = BorderStroke(1.dp, DarrbiTheme.colors.outline),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Hero photo.
            Box(
                modifier = Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(14.dp)).background(DarrbiTheme.colors.surfaceVariant),
            ) {
                if (vehicle.photo != null) {
                    AsyncImage(
                        model = vehicle.photo,
                        contentDescription = stringResource(R.string.rental_cd_vehicle_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                vehicle.category?.let { CategoryBadge(it) }
                Spacer(Modifier.weight(1f))
                vehicle.year?.let { Text(stringResource(R.string.rental_model_year, it), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant) }
            }
            Spacer(Modifier.height(8.dp))
            Text(vehicle.displayName, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            // Inline specs separated by dots.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val specs = listOfNotNull(
                    vehicle.transmission?.let { transmissionLabel(it) },
                    vehicle.seats?.let { stringResource(R.string.rental_seats_value, it) },
                )
                specs.forEachIndexed { index, spec ->
                    if (index > 0) SpecDot()
                    Text(spec, style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Company name (prominent) with its rating right beside it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                vehicle.companyName?.let {
                    Text(
                        it,
                        style = DarrbiTheme.typography.bodyMedium,
                        color = DarrbiTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if ((vehicle.ratingAvg ?: 0.0) > 0.0) {
                    Spacer(Modifier.width(8.dp))
                    RatingPill(vehicle.ratingAvg, vehicle.ratingCount)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))
            // Price + open affordance.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(money(vehicle.currency, vehicle.perDay), style = DarrbiTheme.typography.title.copy(fontSize = 18.sp), color = DarrbiTheme.colors.primary)
                Spacer(Modifier.width(2.dp))
                Text("/" + stringResource(R.string.rental_period_per_day), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(DarrbiTheme.colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DarrbiTheme.colors.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** A small round separator dot used between inline spec labels. */
@Composable
private fun SpecDot() {
    Box(
        modifier = Modifier.padding(horizontal = 6.dp).size(3.dp).clip(CircleShape).background(DarrbiTheme.colors.onSurfaceVariant),
    )
}

@Composable
private fun CategoryBadge(category: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = DarrbiTheme.colors.onSurface.copy(alpha = 0.08f)) {
        Text(
            category.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
            style = DarrbiTheme.typography.caption,
            color = DarrbiTheme.colors.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Loading skeleton (shimmer)
// ---------------------------------------------------------------------------

/** Shimmering placeholder list shown while a vehicle search is in flight (mirrors [RentalVehicleCard]). */
@Composable
internal fun RentalResultsSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberRentalShimmerBrush()
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(4) { SkeletonVehicleCard(brush) }
    }
}

@Composable
private fun SkeletonVehicleCard(brush: Brush) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarrbiTheme.colors.surface,
        border = BorderStroke(1.dp, DarrbiTheme.colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            RentalShimmerBox(brush, Modifier.fillMaxWidth().height(168.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RentalShimmerBox(brush, Modifier.width(64.dp).height(22.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.weight(1f))
                RentalShimmerBox(brush, Modifier.width(56.dp).height(12.dp))
            }
            Spacer(Modifier.height(12.dp))
            RentalShimmerBox(brush, Modifier.fillMaxWidth(0.6f).height(18.dp))
            Spacer(Modifier.height(8.dp))
            RentalShimmerBox(brush, Modifier.fillMaxWidth(0.4f).height(12.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RentalShimmerBox(brush, Modifier.fillMaxWidth(0.45f).height(14.dp))
                Spacer(Modifier.weight(1f))
                RentalShimmerBox(brush, Modifier.width(50.dp).height(14.dp))
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RentalShimmerBox(brush, Modifier.width(110.dp).height(18.dp))
                Spacer(Modifier.weight(1f))
                RentalShimmerBox(brush, Modifier.size(34.dp), CircleShape)
            }
        }
    }
}

/** Animated shimmer brush: a light highlight band sweeping across a muted base. */
@Composable
private fun rememberRentalShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "rentalShimmer")
    val x by transition.animateFloat(
        initialValue = -RENTAL_SHIMMER_BAND,
        targetValue = RENTAL_SHIMMER_TRAVEL,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rentalShimmerX",
    )
    val base = DarrbiTheme.colors.surfaceVariant
    val highlight = DarrbiTheme.colors.outline
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + RENTAL_SHIMMER_BAND, 0f),
    )
}

/** A rounded shimmer placeholder block. */
@Composable
private fun RentalShimmerBox(brush: Brush, modifier: Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.clip(shape).background(brush))
}

private const val RENTAL_SHIMMER_BAND = 280f
private const val RENTAL_SHIMMER_TRAVEL = 700f

// ---------------------------------------------------------------------------
// Filter + Sort sheets
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RentalFilterSheet(
    state: RentalUiState,
    onApply: (category: String?, seats: Int?, transmission: String?) -> Unit,
) {
    var category by remember { mutableStateOf(state.selectedCategory) }
    var seats by remember { mutableStateOf(state.selectedSeats) }
    var transmission by remember { mutableStateOf(state.selectedTransmission) }
    val categories = state.filters?.categories.orEmpty()
    val transmissions = state.filters?.transmissions.orEmpty()
    val seatOptions = listOf(2, 5, 7, 9)

    DarrbiCard(navigationBarPadding = true) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.rental_filter_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (categories.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RentalSectionTitle(stringResource(R.string.rental_filter_car_type))
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RentalChip(stringResource(R.string.rental_filter_all), selected = category == null) { category = null }
                    categories.forEach { c -> RentalChip(c.replaceFirstChar { it.titlecase(Locale.US) }, selected = category == c) { category = c } }
                }
            }
            Spacer(Modifier.height(16.dp))
            RentalSectionTitle(stringResource(R.string.rental_filter_seats))
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RentalChip(stringResource(R.string.rental_filter_all), selected = seats == null) { seats = null }
                seatOptions.forEach { n -> RentalChip(stringResource(R.string.rental_seats_value, n), selected = seats == n) { seats = n } }
            }
            if (transmissions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                RentalSectionTitle(stringResource(R.string.rental_filter_transmission))
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RentalChip(stringResource(R.string.rental_filter_all), selected = transmission == null) { transmission = null }
                    transmissions.forEach { t -> RentalChip(transmissionLabel(t), selected = transmission == t) { transmission = t } }
                }
            }
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.rental_filter_search_count, state.total),
                onClick = { onApply(category, seats, transmission) },
            )
        }
    }
}

@Composable
internal fun RentalSortSheet(
    current: RentalSort?,
    onSelect: (RentalSort?) -> Unit,
) {
    DarrbiCard(navigationBarPadding = true) {
        Text(stringResource(R.string.rental_sort_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        SortOption(stringResource(R.string.rental_sort_recommended), current == null) { onSelect(null) }
        SortOption(stringResource(R.string.rental_sort_rating), current == RentalSort.Rating) { onSelect(RentalSort.Rating) }
        SortOption(stringResource(R.string.rental_sort_price_high), current == RentalSort.PriceHigh) { onSelect(RentalSort.PriceHigh) }
        SortOption(stringResource(R.string.rental_sort_price_low), current == RentalSort.PriceLow) { onSelect(RentalSort.PriceLow) }
    }
}

@Composable
private fun SortOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape)
                .border(2.dp, if (selected) DarrbiTheme.colors.primary else DarrbiTheme.colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(DarrbiTheme.colors.primary))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}

// ---------------------------------------------------------------------------
// Vehicle detail
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RentalDetailContent(
    vehicle: RentalVehicleDetail,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    rentalDays: Int = 1,
) {
    val context = LocalContext.current
    // Which photo is the hero; tapping a thumbnail promotes it. Resets when the vehicle changes.
    var heroIndex by remember(vehicle.id) { mutableStateOf(0) }
    val photos = vehicle.photos
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Full-bleed hero image — spans the screen width with a softly rounded lower edge.
            photos.getOrNull(heroIndex)?.let { photo ->
                AsyncImage(
                    model = photo,
                    contentDescription = stringResource(R.string.rental_cd_vehicle_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                        .background(DarrbiTheme.colors.surfaceVariant),
                )
            }

            // Gallery strip — every photo as a thumbnail; tap to make it the hero.
            if (photos.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    photos.forEachIndexed { index, thumb ->
                        val selected = index == heroIndex
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 76.dp, height = 56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarrbiTheme.colors.surfaceVariant)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) DarrbiTheme.colors.primary else DarrbiTheme.colors.outline,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { heroIndex = index },
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(if (photos.size > 1) 4.dp else 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    vehicle.category?.let { CategoryBadge(it) }
                    Spacer(Modifier.weight(1f))
                    vehicle.year?.let { Text(stringResource(R.string.rental_model_year, it), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant) }
                }
                Spacer(Modifier.height(10.dp))
                Text(vehicle.displayName, style = DarrbiTheme.typography.titleLarge.copy(fontSize = 22.sp), color = DarrbiTheme.colors.onSurface)
                // Company and rating sit on one line for a cleaner header.
                val hasRating = (vehicle.ratingAvg ?: 0.0) > 0.0
                if (vehicle.company != null || hasRating) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        vehicle.company?.let {
                            Text(stringResource(R.string.rental_company_by, it.name), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                        }
                        if (hasRating) {
                            if (vehicle.company != null) {
                                Text("  •  ", style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.Star, null, tint = DarrbiTheme.colors.warning, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                stringResource(R.string.rental_rating_format, formatFare(vehicle.ratingAvg ?: 0.0), vehicle.ratingCount ?: 0),
                                style = DarrbiTheme.typography.caption,
                                color = DarrbiTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Quick stats strip — the key facts at a glance.
                Spacer(Modifier.height(16.dp))
                QuickStatsRow(vehicle)

                // Full spec sheet.
                Spacer(Modifier.height(16.dp))
                CarDetailsSection(vehicle)

                // CDW tiers (read-only here; chosen on the reservation step).
                if (vehicle.cdwTiers.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    DetailSectionCard(stringResource(R.string.rental_cdw_title), icon = Icons.Filled.Shield) {
                        vehicle.cdwTiers.forEach { tier ->
                            RentalKeyValueRow(
                                label = cdwTierLabel(tier.tier),
                                value = if (tier.dailyRate <= 0.0) stringResource(R.string.rental_cdw_included) else stringResource(R.string.rental_cdw_per_day, money(vehicle.currency, tier.dailyRate)),
                            )
                        }
                    }
                }

                // Pickup branches.
                if (vehicle.pickupBranches.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    DetailSectionCard(stringResource(R.string.rental_branches_title), icon = Icons.Filled.LocationOn) {
                        vehicle.pickupBranches.forEachIndexed { index, branch ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            PickupBranchCard(branch) { lat, lng -> openRentalNavigation(context, lat, lng) }
                        }
                    }
                }

                // Reviews.
                if (vehicle.reviews.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    DetailSectionCard(stringResource(R.string.rental_reviews_title), icon = Icons.Filled.Star) {
                        vehicle.reviews.take(5).forEachIndexed { index, review ->
                            if (index > 0) HorizontalDivider(color = DarrbiTheme.colors.outline.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, null, tint = DarrbiTheme.colors.warning, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(review.rating.toString(), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurface)
                                    review.renterName?.let {
                                        Spacer(Modifier.width(8.dp))
                                        Text(it, style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                                    }
                                }
                                review.text?.let { Text(it, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurface) }
                            }
                        }
                    }
                }

                // Rental rate — collapsible and placed at the very end (collapsed by default).
                if (vehicle.pricing.perDay != null || vehicle.pricing.perHour != null) {
                    Spacer(Modifier.height(12.dp))
                    RentalRateSection(vehicle)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
        // Total + Continue footer. The total scales with the selected number of rental days.
        Surface(color = DarrbiTheme.colors.surface, shadowElevation = 8.dp) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val days = rentalDays.coerceAtLeast(1)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.rental_total_label), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
                        vehicle.pricing.perDay?.let {
                            Text(
                                stringResource(R.string.rental_days_x_rate, days, money(vehicle.currency, it)),
                                style = DarrbiTheme.typography.caption,
                                color = DarrbiTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                    vehicle.pricing.perDay?.let {
                        Text(money(vehicle.currency, it * days), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 18.sp), color = DarrbiTheme.colors.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                DarrbiPrimaryButton(text = stringResource(R.string.rental_continue), onClick = onContinue)
            }
        }
    }
}

/** One column inside [QuickStatsRow]: an icon, a bold value, and a muted label. */
private data class QuickStat(val icon: androidx.compose.ui.graphics.vector.ImageVector, val value: String, val label: String)

/**
 * An elegant "stat strip" of the key vehicle facts (seats / doors / transmission / mileage), laid out
 * as evenly-weighted columns separated by hairline dividers. Replaces the old wrap of check-chips.
 */
@Composable
private fun QuickStatsRow(vehicle: RentalVehicleDetail) {
    val stats = buildList {
        vehicle.specs.seats?.let { add(QuickStat(Icons.Filled.EventSeat, it.toString(), stringResource(R.string.rental_stat_seats))) }
        vehicle.specs.doors?.let { add(QuickStat(Icons.Filled.SensorDoor, it.toString(), stringResource(R.string.rental_stat_doors))) }
        vehicle.transmission?.let { t ->
            transmissionLabel(t).takeIf { it.isNotBlank() }?.let { add(QuickStat(Icons.Filled.Settings, it, stringResource(R.string.rental_stat_transmission))) }
        }
        if (vehicle.mileagePolicy.isUnlimited) {
            add(QuickStat(Icons.Filled.Speed, stringResource(R.string.rental_stat_unlimited), stringResource(R.string.rental_stat_mileage)))
        } else vehicle.mileagePolicy.includedKmPerDay?.let {
            add(QuickStat(Icons.Filled.Speed, it.toString(), stringResource(R.string.rental_stat_km_day)))
        }
    }
    if (stats.isEmpty()) return
    Surface(shape = RoundedCornerShape(18.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    Box(Modifier.width(1.dp).height(38.dp).background(DarrbiTheme.colors.outline.copy(alpha = 0.5f)))
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(stat.icon, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(7.dp))
                    Text(
                        stat.value,
                        style = DarrbiTheme.typography.bodyMedium,
                        color = DarrbiTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        stat.label,
                        style = DarrbiTheme.typography.caption,
                        color = DarrbiTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A titled, rounded surface that groups a block of detail content, with an optional leading header icon. */
@Composable
private fun DetailSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(it, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                RentalSectionTitle(title)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** A spec row inside the Car Details card; renders nothing when the value is blank. */
@Composable
private fun DetailRow(label: String, value: String?, showDivider: Boolean) {
    if (value.isNullOrBlank()) return
    if (showDivider) HorizontalDivider(color = DarrbiTheme.colors.outline.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 2.dp))
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}

/** Localised fuel-type label; falls back to the title-cased server value. */
@Composable
private fun fuelTypeLabel(value: String?): String? = when (value?.lowercase(Locale.US)?.trim()) {
    null, "" -> null
    "petrol", "gasoline", "gas" -> stringResource(R.string.rental_fuel_petrol)
    "diesel" -> stringResource(R.string.rental_fuel_diesel)
    "electric", "ev" -> stringResource(R.string.rental_fuel_electric)
    "hybrid" -> stringResource(R.string.rental_fuel_hybrid)
    else -> value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

/** Structured spec sheet for the vehicle; only rows with data are shown. */
@Composable
private fun CarDetailsSection(vehicle: RentalVehicleDetail) {
    val luggage = listOfNotNull(
        vehicle.specs.smallLuggage?.takeIf { it > 0 }?.let { stringResource(R.string.rental_small_bag_value, it) },
        vehicle.specs.largeLuggage?.takeIf { it > 0 }?.let { stringResource(R.string.rental_large_bag_value, it) },
    ).joinToString(" · ").takeIf { it.isNotBlank() }
    val mileage = if (vehicle.mileagePolicy.isUnlimited) stringResource(R.string.rental_unlimited_km)
        else vehicle.mileagePolicy.includedKmPerDay?.let { stringResource(R.string.rental_limited_km, it) }

    val rows = listOf(
        stringResource(R.string.rental_detail_make) to vehicle.make.ifBlank { null },
        stringResource(R.string.rental_detail_model) to vehicle.model.ifBlank { null },
        stringResource(R.string.rental_detail_year) to vehicle.year?.toString(),
        stringResource(R.string.rental_detail_category) to vehicle.category,
        stringResource(R.string.rental_detail_color) to vehicle.color,
        stringResource(R.string.rental_detail_fuel) to fuelTypeLabel(vehicle.fuelType),
        stringResource(R.string.rental_detail_transmission) to vehicle.transmission?.let { transmissionLabel(it) }?.takeIf { it.isNotBlank() },
        stringResource(R.string.rental_detail_engine) to vehicle.engineCc?.let { stringResource(R.string.rental_engine_cc, it) },
        stringResource(R.string.rental_detail_seats) to vehicle.specs.seats?.toString(),
        stringResource(R.string.rental_detail_doors) to vehicle.specs.doors?.toString(),
        stringResource(R.string.rental_detail_luggage) to luggage,
        stringResource(R.string.rental_detail_mileage) to mileage,
    ).filter { !it.second.isNullOrBlank() }

    DetailSectionCard(stringResource(R.string.rental_details_title), icon = Icons.Filled.DirectionsCar) {
        rows.forEachIndexed { index, (label, value) -> DetailRow(label, value, showDivider = index > 0) }
    }
}

/**
 * One pickup branch presented as a self-contained card: a pinned avatar, name + address, optional
 * parking note, and a full-width "Get directions" action that launches Google Maps navigation.
 */
@Composable
private fun PickupBranchCard(branch: PickupBranch, onNavigate: (Double, Double) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarrbiTheme.colors.surface,
        border = BorderStroke(1.dp, DarrbiTheme.colors.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(DarrbiTheme.colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LocationOn, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(branch.name, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
                    listOfNotNull(branch.address, branch.city).joinToString(", ").takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                    }
                }
            }
            branch.parkingInstructions?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            }
            if (branch.latitude != null && branch.longitude != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarrbiTheme.colors.primary.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().clickable { onNavigate(branch.latitude, branch.longitude) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Navigation, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.rental_get_directions),
                            style = DarrbiTheme.typography.button.copy(fontSize = 14.sp),
                            color = DarrbiTheme.colors.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Opens turn-by-turn navigation to [lat]/[lng] in Google Maps (falls back to a generic geo: query). */
private fun openRentalNavigation(context: android.content.Context, lat: Double, lng: Double) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=$lat,$lng"))
                .setPackage("com.google.android.apps.maps")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Collapsible rental-rate breakdown (collapsed by default); the header keeps the per-day price visible. */
@Composable
private fun RentalRateSection(vehicle: RentalVehicleDetail) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rateChevron")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarrbiTheme.colors.surfaceVariant,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Payments, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    RentalSectionTitle(stringResource(R.string.rental_pricing_title))
                    vehicle.pricing.perDay?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(R.string.rental_per_day_format, money(vehicle.currency, it)), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                    }
                }
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.rotate(rotation))
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PricingTierRow(R.string.rental_period_per_hour, vehicle.pricing.perHour, vehicle.currency)
                    PricingTierRow(R.string.rental_period_h2to3, vehicle.pricing.h2to3, vehicle.currency)
                    PricingTierRow(R.string.rental_period_h4to5, vehicle.pricing.h4to5, vehicle.currency)
                    PricingTierRow(R.string.rental_period_h6to12, vehicle.pricing.h6to12, vehicle.currency)
                    PricingTierRow(R.string.rental_period_per_day, vehicle.pricing.perDay, vehicle.currency, highlighted = true)
                    PricingTierRow(R.string.rental_period_per_week, vehicle.pricing.perWeek, vehicle.currency)
                    PricingTierRow(R.string.rental_period_per_month, vehicle.pricing.perMonth, vehicle.currency)
                }
            }
        }
    }
}

/**
 * A single pricing tier inside the expanded rate breakdown — a soft pill with the period on the left
 * and the amount on the right. The per-day anchor is tinted and emphasised. Renders nothing if unpriced.
 */
@Composable
private fun PricingTierRow(labelRes: Int, value: Double?, currency: String, highlighted: Boolean = false) {
    if (value == null) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) DarrbiTheme.colors.primary.copy(alpha = 0.1f) else DarrbiTheme.colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(labelRes),
                style = if (highlighted) DarrbiTheme.typography.bodyMedium else DarrbiTheme.typography.body,
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                money(currency, value),
                style = DarrbiTheme.typography.bodyMedium,
                color = if (highlighted) DarrbiTheme.colors.primary else DarrbiTheme.colors.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Reservation / quote
// ---------------------------------------------------------------------------

@Composable
internal fun RentalReserveContent(
    state: RentalUiState,
    onEvent: (RentalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicle = state.vehicle ?: return
    val tier = state.effectiveCdwTier()
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.rental_reservation_info), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            Text(vehicle.displayName, style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(16.dp))

            // Renter fields.
            DarrbiTextField(
                value = state.renterNid,
                onValueChange = { onEvent(RentalEvent.SetNid(it)) },
                label = stringResource(R.string.rental_field_nid),
                keyboardType = KeyboardType.Number,
                isError = state.nidError,
                supportingText = if (state.nidError) stringResource(R.string.rental_err_nid) else null,
            )
            Spacer(Modifier.height(12.dp))
            DarrbiTextField(value = state.renterLicense, onValueChange = { onEvent(RentalEvent.SetLicense(it)) }, label = stringResource(R.string.rental_field_license))
            Spacer(Modifier.height(12.dp))
            DarrbiTextField(value = state.renterName, onValueChange = { onEvent(RentalEvent.SetName(it)) }, label = stringResource(R.string.rental_field_name))
            Spacer(Modifier.height(12.dp))
            // Date of birth is chosen on the calendar sheet (never typed); a transparent overlay captures
            // the tap since a read-only OutlinedTextField doesn't expose an onClick.
            Box {
                DarrbiTextField(
                    value = state.renterDob,
                    onValueChange = {},
                    readOnly = true,
                    label = stringResource(R.string.rental_field_dob),
                    supportingText = stringResource(R.string.rental_dob_note),
                    trailingContent = {
                        Icon(Icons.Filled.DateRange, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.padding(end = 8.dp))
                    },
                )
                Box(
                    modifier = Modifier.matchParentSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onEvent(RentalEvent.OpenDobSheet)
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            DarrbiTextField(
                value = state.renterPhone,
                onValueChange = { onEvent(RentalEvent.SetPhone(it)) },
                readOnly = state.phoneFromAccount,
                label = stringResource(R.string.rental_field_phone),
                keyboardType = KeyboardType.Phone,
                isError = state.phoneError,
                supportingText = if (state.phoneError) stringResource(R.string.rental_err_phone) else null,
                leadingContent = {
                    Text(
                        state.renterPhoneCc,
                        style = DarrbiTheme.typography.body,
                        color = DarrbiTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            DarrbiTextField(
                value = state.renterEmail,
                onValueChange = { onEvent(RentalEvent.SetEmail(it)) },
                label = stringResource(R.string.rental_field_email_optional),
                keyboardType = KeyboardType.Email,
                isError = state.emailError,
                supportingText = if (state.emailError) stringResource(R.string.rental_err_email) else null,
            )

            // Rental period.
            Spacer(Modifier.height(18.dp))
            RentalSectionTitle(stringResource(R.string.rental_rental_period))
            Spacer(Modifier.height(8.dp))
            RentalKeyValueRow(stringResource(R.string.rental_pickup_at), formatRentalDateTime(state.pickupAtMillis))
            RentalKeyValueRow(stringResource(R.string.rental_return_at), formatRentalDateTime(state.returnAtMillis))

            // CDW selection.
            if (vehicle.cdwTiers.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                RentalSectionTitle(stringResource(R.string.rental_cdw_title))
                Spacer(Modifier.height(8.dp))
                vehicle.cdwTiers.forEach { option ->
                    CdwTierCard(option = option, currency = vehicle.currency, selected = option.tier == tier) { onEvent(RentalEvent.SetCdwTier(option.tier)) }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Coupon.
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    DarrbiTextField(value = state.couponInput, onValueChange = { onEvent(RentalEvent.CouponChanged(it)) }, label = stringResource(R.string.rental_coupon_label))
                }
                Spacer(Modifier.width(10.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.clickable { onEvent(RentalEvent.ApplyCoupon) }) {
                    Text(stringResource(R.string.rental_apply), style = DarrbiTheme.typography.button.copy(fontSize = 14.sp), color = DarrbiTheme.colors.primary, modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
                }
            }

            // Quote breakdown.
            Spacer(Modifier.height(18.dp))
            val quote = state.quote
            if (state.isLoadingQuote && quote == null) {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarrbiTheme.colors.primary)
                }
            } else if (quote != null) {
                if (!quote.available) {
                    Text(stringResource(R.string.rental_quote_unavailable), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.error)
                    Spacer(Modifier.height(8.dp))
                }
                quote.breakdown.forEach { line -> RentalKeyValueRow(line.label, money(quote.currency, line.amount)) }
                if (quote.couponAmount > 0.0 && quote.breakdown.none { it.amount < 0 }) {
                    RentalKeyValueRow(stringResource(R.string.rental_coupon_applied), "-${money(quote.currency, quote.couponAmount)}")
                }
                HorizontalDivider(color = DarrbiTheme.colors.outline, modifier = Modifier.padding(vertical = 6.dp))
                RentalKeyValueRow(stringResource(R.string.rental_total_label), money(quote.currency, quote.totalAmount), emphasize = true)
                Spacer(Modifier.height(6.dp))
                // Deposit (held, not charged).
                Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        RentalKeyValueRow(stringResource(R.string.rental_deposit_label), money(quote.currency, quote.depositAmount))
                        Text(stringResource(R.string.rental_deposit_note), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            DarrbiCheckbox(checked = state.termsAccepted, onCheckedChange = { onEvent(RentalEvent.SetTerms(it)) }, label = stringResource(R.string.rental_terms_agree))
            Spacer(Modifier.height(16.dp))
        }
        // Reserve footer.
        Surface(color = DarrbiTheme.colors.surface, shadowElevation = 8.dp) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                state.quote?.let { q ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rental_pay_summary), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(money(q.currency, q.totalAmount), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 20.sp), color = DarrbiTheme.colors.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                DarrbiPrimaryButton(
                    text = stringResource(R.string.rental_reserve_now),
                    enabled = state.canReserve,
                    onClick = { onEvent(RentalEvent.ReserveNow) },
                )
            }
        }
    }
}

@Composable
private fun CdwTierCard(option: CdwTierOption, currency: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) DarrbiTheme.colors.primary.copy(alpha = 0.10f) else DarrbiTheme.colors.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, DarrbiTheme.colors.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cdwTierLabel(option.tier), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
                Text(
                    if (option.excess <= 0.0) stringResource(R.string.rental_cdw_zero_excess)
                    else stringResource(R.string.rental_cdw_excess, money(currency, option.excess)),
                    style = DarrbiTheme.typography.caption,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                )
                Text(stringResource(R.string.rental_cdw_deposit, money(currency, option.deposit)), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            }
            Text(
                if (option.dailyRate <= 0.0) stringResource(R.string.rental_cdw_included) else stringResource(R.string.rental_cdw_per_day, money(currency, option.dailyRate)),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.primary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Reservation success
// ---------------------------------------------------------------------------

@Composable
internal fun ReservationSuccessDialog(bookingRef: String?, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
        DarrbiCard(navigationBarPadding = true) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(84.dp).clip(CircleShape).background(DarrbiTheme.colors.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = DarrbiTheme.colors.onPrimary, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.rental_success_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.rental_success_desc), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            bookingRef?.let {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.rental_booking_id, it), style = DarrbiTheme.typography.title.copy(fontSize = 16.sp), color = DarrbiTheme.colors.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(text = stringResource(R.string.common_done), onClick = onDone)
        }
    }
}

// ---------------------------------------------------------------------------
// Bookings hub
// ---------------------------------------------------------------------------

@Composable
internal fun RentalBookingsContent(
    state: RentalUiState,
    onOpen: (String) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.bookings.isEmpty() && !state.isLoadingBookings) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.rental_bookings_empty), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                DarrbiPrimaryButton(text = stringResource(R.string.rental_book_a_car), onClick = onCreateNew, modifier = Modifier.fillMaxWidth(0.7f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.bookings, key = { it.bookingId }) { booking -> RentalBookingRow(booking) { onOpen(booking.bookingId) } }
                item {
                    Spacer(Modifier.height(4.dp))
                    DarrbiSecondaryButton(text = stringResource(R.string.rental_book_a_car), onClick = onCreateNew)
                }
            }
        }
    }
}

@Composable
internal fun RentalBookingRow(booking: RentalBooking, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (booking.vehicle?.photo != null) {
                AsyncImage(model = booking.vehicle.photo, contentDescription = stringResource(R.string.rental_cd_vehicle_photo), contentScale = ContentScale.Fit, modifier = Modifier.size(width = 90.dp, height = 56.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(booking.vehicle?.displayName ?: "", style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
                Text("${formatRentalDate(booking.pickupAtMillis)} → ${formatRentalDate(booking.returnAtMillis)}", style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                booking.bookingRef?.let { Text(stringResource(R.string.rental_booking_ref, it), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant) }
            }
            StatusPill(booking.status)
        }
    }
}

@Composable
private fun StatusPill(status: RentalBookingStatus) {
    val active = status.isActive
    Surface(shape = RoundedCornerShape(8.dp), color = if (active) DarrbiTheme.colors.primary else DarrbiTheme.colors.onSurface.copy(alpha = 0.08f)) {
        Text(
            bookingStatusLabel(status),
            style = DarrbiTheme.typography.caption,
            color = if (active) DarrbiTheme.colors.onPrimary else DarrbiTheme.colors.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Booking detail / track
// ---------------------------------------------------------------------------

@Composable
internal fun RentalBookingDetailContent(
    detail: RentalBookingDetail,
    isActionInFlight: Boolean,
    onCancel: () -> Unit,
    onExtend: () -> Unit,
    onRate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.vehicle?.displayName ?: "", style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
                detail.bookingRef?.let { Text(stringResource(R.string.rental_booking_ref, it), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant) }
            }
            StatusPill(detail.status)
        }
        detail.vehicle?.photo?.let {
            Spacer(Modifier.height(8.dp))
            AsyncImage(model = it, contentDescription = stringResource(R.string.rental_cd_vehicle_photo), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(150.dp))
        }

        // Live telemetry (active rentals).
        detail.tripData?.let { trip ->
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.primary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.rental_track_title), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
                    Spacer(Modifier.height(6.dp))
                    trip.distanceKm?.let { RentalKeyValueRow(stringResource(R.string.rental_period_per_day), stringResource(R.string.rental_trip_distance, formatFare(it))) }
                    trip.fuelNowPct?.let { RentalKeyValueRow(stringResource(R.string.rental_penalty_fuel), stringResource(R.string.rental_trip_fuel, it)) }
                    trip.tajeerContractId?.let { Text(stringResource(R.string.rental_tajeer_contract, it), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant) }
                }
            }
        }

        // Period.
        Spacer(Modifier.height(14.dp))
        RentalSectionTitle(stringResource(R.string.rental_rental_period))
        Spacer(Modifier.height(6.dp))
        RentalKeyValueRow(stringResource(R.string.rental_pickup_at), formatRentalDateTime(detail.pickupAtMillis))
        RentalKeyValueRow(stringResource(R.string.rental_return_at), formatRentalDateTime(detail.returnAtMillis))
        detail.pickupBranch?.let { branch ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(listOfNotNull(branch.name, branch.city).joinToString(", "), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            }
        }

        // Amounts.
        Spacer(Modifier.height(14.dp))
        RentalSectionTitle(stringResource(R.string.rental_total_label))
        Spacer(Modifier.height(6.dp))
        if (detail.couponAmount > 0.0) RentalKeyValueRow(stringResource(R.string.rental_coupon_applied), "-${money(detail.currency, detail.couponAmount)}")
        RentalKeyValueRow(stringResource(R.string.rental_total_label), money(detail.currency, detail.totalAmount), emphasize = true)
        RentalKeyValueRow(stringResource(R.string.rental_deposit_label), money(detail.currency, detail.depositAmount))
        if (detail.refundAmount > 0.0) RentalKeyValueRow(stringResource(R.string.rental_refund_format, money(detail.currency, detail.refundAmount)), "")

        // Penalties.
        detail.penalties?.takeIf { it.hasAny }?.let { p ->
            Spacer(Modifier.height(14.dp))
            RentalSectionTitle(stringResource(R.string.rental_penalties_title))
            Spacer(Modifier.height(6.dp))
            if (p.lateReturn > 0) RentalKeyValueRow(stringResource(R.string.rental_penalty_late), money(detail.currency, p.lateReturn))
            if (p.lowFuel > 0) RentalKeyValueRow(stringResource(R.string.rental_penalty_fuel), money(detail.currency, p.lowFuel))
            if (p.outOfZone > 0) RentalKeyValueRow(stringResource(R.string.rental_penalty_zone), money(detail.currency, p.outOfZone))
        }

        // Extension request status.
        detail.extensionRequest?.let { ext ->
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.rental_extension_pending, ext.hours), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurface, modifier = Modifier.padding(12.dp))
            }
        }

        detail.paymentRef?.let {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.rental_payment_refs, it), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
        }

        // Actions.
        Spacer(Modifier.height(18.dp))
        when {
            detail.status.isCancellable -> DarrbiSecondaryButton(text = stringResource(R.string.rental_cancel_booking), enabled = !isActionInFlight, onClick = onCancel)
            detail.status.isActive && detail.extensionRequest == null -> DarrbiPrimaryButton(text = stringResource(R.string.rental_extend), enabled = !isActionInFlight, onClick = onExtend)
            detail.status.isRateable -> DarrbiPrimaryButton(text = stringResource(R.string.rental_rate_title), enabled = !isActionInFlight, onClick = onRate)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Extend + Rate sheets
// ---------------------------------------------------------------------------

@Composable
internal fun RentalExtendSheet(onConfirm: (Int) -> Unit) {
    var hours by remember { mutableIntStateOf(24) }
    DarrbiCard(navigationBarPadding = true) {
        Text(stringResource(R.string.rental_extend_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rental_extend_hours), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
            StepperButton(Icons.Filled.Remove) { hours = (hours - 6).coerceAtLeast(6) }
            Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                Text(hours.toString(), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            }
            StepperButton(Icons.Filled.Add) { hours = (hours + 6).coerceAtMost(720) }
        }
        Spacer(Modifier.height(18.dp))
        DarrbiPrimaryButton(text = stringResource(R.string.rental_extend_request), onClick = { onConfirm(hours) })
    }
}

@Composable
internal fun RentalRateSheet(onSubmit: (Int, String?) -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    var text by remember { mutableStateOf("") }
    DarrbiCard(navigationBarPadding = true) {
        Text(stringResource(R.string.rental_rate_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { star ->
                Icon(
                    Icons.Filled.Star,
                    null,
                    tint = if (star <= rating) DarrbiTheme.colors.warning else DarrbiTheme.colors.outline,
                    modifier = Modifier.size(36.dp).padding(horizontal = 4.dp).clickable { rating = star },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        DarrbiTextField(value = text, onValueChange = { text = it }, label = stringResource(R.string.rental_rate_hint), singleLine = false)
        Spacer(Modifier.height(18.dp))
        DarrbiPrimaryButton(text = stringResource(R.string.common_submit), onClick = { onSubmit(rating, text.takeIf { it.isNotBlank() }) })
    }
}
