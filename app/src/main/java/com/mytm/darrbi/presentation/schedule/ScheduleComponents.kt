package com.mytm.darrbi.presentation.schedule

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiCard
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.domain.model.C2cCity
import com.mytm.darrbi.domain.model.UpcomingScheduledTrip
import com.mytm.darrbi.presentation.rider.CashGlyph
import com.mytm.darrbi.presentation.rider.ContactCircle
import com.mytm.darrbi.presentation.rider.OfferStepButton
import com.mytm.darrbi.presentation.rider.RouteHeader
import com.mytm.darrbi.presentation.rider.SearchField
import com.mytm.darrbi.presentation.rider.WideTonalButton
import com.mytm.darrbi.presentation.rider.formatFare
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Returns true when the current app locale is Arabic (used to pick the Arabic city name). */
@Composable
internal fun isArabicLocale(): Boolean = LocalConfiguration.current.locales[0].language == "ar"

/** City display name, Arabic when available + the locale is Arabic. */
internal fun C2cCity.displayName(arabic: Boolean): String =
    (if (arabic) nameArabic else null)?.takeIf { it.isNotBlank() } ?: name

/** Maps C2C machine error codes to friendly localized strings; non-codes pass through. */
@Composable
internal fun scheduleErrorText(code: String?): String? {
    code ?: return null
    return when (code) {
        "C2C_DISABLED" -> stringResource(R.string.c2c_err_disabled)
        "SAME_ORIGIN_DESTINATION" -> stringResource(R.string.c2c_err_same_od)
        "INVALID_CITY" -> stringResource(R.string.c2c_err_invalid_city)
        "DEPARTURE_TOO_SOON" -> stringResource(R.string.c2c_err_departure_too_soon)
        "DEPARTURE_TOO_FAR" -> stringResource(R.string.c2c_err_departure_too_far)
        "INVALID_SEATS" -> stringResource(R.string.c2c_err_invalid_seats)
        "INTERCITY_ROUTE_NOT_PRICED" -> stringResource(R.string.c2c_err_not_priced)
        "OFFER_OUT_OF_BAND", "BID_BELOW_FLOOR", "BID_ABOVE_CEILING" -> stringResource(R.string.c2c_err_offer_out_of_band)
        "BIDDING_CLOSED" -> stringResource(R.string.c2c_err_bidding_closed)
        "TRIP_NOT_CANCELLABLE" -> stringResource(R.string.c2c_err_not_cancellable)
        "CREATE_FAILED" -> stringResource(R.string.c2c_err_create_failed)
        ScheduleViewModel.INFO_CANCELLED_FREE -> stringResource(R.string.schedule_cancelled_free)
        ScheduleViewModel.INFO_CANCELLED_FEE -> stringResource(R.string.schedule_cancelled_fee)
        else -> code
    }
}

/** "Tomorrow, Wed 10 June at 8:00 AM"-style label for a departure timestamp. */
@Composable
internal fun formatScheduleWhen(millis: Long?): String {
    if (millis == null) return ""
    val locale = LocalConfiguration.current.locales[0]
    val datePart = SimpleDateFormat("EEE d MMMM", locale).format(Date(millis))
    val timePart = SimpleDateFormat("h:mm a", locale).format(Date(millis))
    val relative = relativeDayLabel(millis)
    val core = stringResource(R.string.schedule_when_full, datePart, timePart)
    return if (relative != null) "$relative, $core" else core
}

/** "Today"/"Tomorrow" when the day matches; otherwise null. */
@Composable
private fun relativeDayLabel(millis: Long): String? {
    val today = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    return when {
        sameDay(today, target) -> stringResource(R.string.schedule_today)
        sameDay(tomorrow, target) -> stringResource(R.string.schedule_tomorrow)
        else -> null
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
internal fun ScheduleHeader(title: String, onBack: () -> Unit) {
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
        Text(title, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
    }
}

/** "Set Location From Map" row shown under the point search field — opens the full-screen map picker. */
@Composable
internal fun ChooseOnMapRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = DarrbiTheme.colors.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Place, null, tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.rider_set_location_from_map), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
        }
    }
}

// ---------------------------------------------------------------------------
// Date + time picker
// ---------------------------------------------------------------------------

@Composable
internal fun ScheduleDateTimeSheet(
    initialMillis: Long?,
    onConfirm: (Long) -> Unit,
) {
    val nowMillis = remember { System.currentTimeMillis() }
    val minMillis = remember { nowMillis + ScheduleViewModel.MIN_LEAD_MINUTES * 60_000L }
    val seed = remember { Calendar.getInstance().apply { timeInMillis = (initialMillis ?: (minMillis + 30 * 60_000L)) } }

    var viewYear by remember { mutableIntStateOf(seed.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(seed.get(Calendar.MONTH)) } // 0-based
    var selYear by remember { mutableIntStateOf(seed.get(Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(seed.get(Calendar.MONTH)) }
    var selDay by remember { mutableIntStateOf(seed.get(Calendar.DAY_OF_MONTH)) }

    val seedHour = seed.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    var hour12 by remember { mutableIntStateOf(seedHour) }
    var minute by remember { mutableIntStateOf(seed.get(Calendar.MINUTE)) }
    var isPm by remember { mutableStateOf(seed.get(Calendar.AM_PM) == Calendar.PM) }

    fun chosenMillis(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, selYear)
        c.set(Calendar.MONTH, selMonth)
        c.set(Calendar.DAY_OF_MONTH, selDay)
        c.set(Calendar.HOUR, hour12 % 12)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.AM_PM, if (isPm) Calendar.PM else Calendar.AM)
        return c.timeInMillis
    }

    val locale = LocalConfiguration.current.locales[0]
    val monthLabel = remember(viewYear, viewMonth) {
        SimpleDateFormat("MMMM yyyy", locale).format(Calendar.getInstance().apply {
            set(Calendar.YEAR, viewYear); set(Calendar.MONTH, viewMonth); set(Calendar.DAY_OF_MONTH, 1)
        }.time)
    }

    DarrbiCard(navigationBarPadding = true) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.schedule_title),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                textAlign = TextAlign.Center,
            )
            // Month navigation header.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = !isViewAtOrBeforeNow(viewYear, viewMonth)) {
                    if (viewMonth == 0) { viewMonth = 11; viewYear -= 1 } else viewMonth -= 1
                }
                Text(
                    monthLabel,
                    style = DarrbiTheme.typography.title,
                    color = DarrbiTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = true) {
                    if (viewMonth == 11) { viewMonth = 0; viewYear += 1 } else viewMonth += 1
                }
            }
            Spacer(Modifier.height(12.dp))
            // Weekday initials.
            val initials = stringArrayResource(R.array.schedule_weekday_initials)
            Row(modifier = Modifier.fillMaxWidth()) {
                initials.forEach { d ->
                    Text(d, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            CalendarGrid(
                year = viewYear,
                month = viewMonth,
                nowMillis = nowMillis,
                selectedYear = selYear,
                selectedMonth = selMonth,
                selectedDay = selDay,
                onPick = { d -> selYear = viewYear; selMonth = viewMonth; selDay = d },
            )
            Spacer(Modifier.height(18.dp))
            // Time picker: stacked-chevron hour/minute steppers + an AM/PM segmented control.
            Text(stringResource(R.string.schedule_time_label), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeStepperUnit(
                    value = String.format(Locale.US, "%02d", hour12),
                    onUp = { hour12 = if (hour12 == 12) 1 else hour12 + 1 },
                    onDown = { hour12 = if (hour12 == 1) 12 else hour12 - 1 },
                )
                Text(":", style = DarrbiTheme.typography.titleLarge.copy(fontSize = 30.sp), color = DarrbiTheme.colors.onSurface, modifier = Modifier.padding(horizontal = 10.dp))
                TimeStepperUnit(
                    value = String.format(Locale.US, "%02d", minute),
                    onUp = { minute = (minute + 1) % 60 },
                    onDown = { minute = (minute + 59) % 60 },
                )
                Spacer(Modifier.width(16.dp))
                AmPmSegmented(isPm = isPm) { isPm = it }
            }
            Spacer(Modifier.height(22.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.schedule_confirm_schedule),
                enabled = chosenMillis() >= minMillis,
                onClick = { onConfirm(chosenMillis()) },
            )
        }
    }
}

private fun isViewAtOrBeforeNow(year: Int, month: Int): Boolean {
    val now = Calendar.getInstance()
    return year < now.get(Calendar.YEAR) || (year == now.get(Calendar.YEAR) && month <= now.get(Calendar.MONTH))
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) DarrbiTheme.colors.onSurface else DarrbiTheme.colors.onSurfaceVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    nowMillis: Long,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    onPick: (Int) -> Unit,
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply { set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, 1) }
    }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) // Sunday = 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leading = firstDow - Calendar.SUNDAY // 0..6
    val cells = leading + daysInMonth
    val rows = (cells + 6) / 7
    val todayStart = remember(nowMillis) {
        Calendar.getInstance().apply {
            timeInMillis = nowMillis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val day = index - leading + 1
                    if (day in 1..daysInMonth) {
                        val dayMillis = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.timeInMillis
                        val isPast = dayMillis < todayStart
                        val isSelected = year == selectedYear && month == selectedMonth && day == selectedDay
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) DarrbiTheme.colors.primary else DarrbiTheme.colors.surface)
                                .clickable(enabled = !isPast) { onPick(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.toString(),
                                style = DarrbiTheme.typography.body,
                                color = when {
                                    isSelected -> DarrbiTheme.colors.onPrimary
                                    isPast -> DarrbiTheme.colors.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> DarrbiTheme.colors.onSurface
                                },
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueStepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OfferStepButton("−", accent = false, enabled = true, onClick = onMinus)
        Box(modifier = Modifier.width(46.dp), contentAlignment = Alignment.Center) {
            Text(value, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
        }
        OfferStepButton("+", accent = false, enabled = true, onClick = onPlus)
    }
}

/** A single hour/minute unit: chevron-up, the value in a rounded box, chevron-down. */
@Composable
private fun TimeStepperUnit(value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StepChevron(Icons.Filled.KeyboardArrowUp, onUp)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarrbiTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
        ) {
            Box(modifier = Modifier.size(width = 62.dp, height = 54.dp), contentAlignment = Alignment.Center) {
                Text(value, style = DarrbiTheme.typography.titleLarge.copy(fontSize = 26.sp), color = DarrbiTheme.colors.onSurface)
            }
        }
        StepChevron(Icons.Filled.KeyboardArrowDown, onDown)
    }
}

/** Small circular chevron button used by the time stepper. */
@Composable
private fun StepChevron(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(DarrbiTheme.colors.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Vertical AM / PM segmented control (selected segment filled green). */
@Composable
private fun AmPmSegmented(isPm: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.surfaceVariant) {
        Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AmPmSegment(stringResource(R.string.schedule_am), selected = !isPm) { onChange(false) }
            AmPmSegment(stringResource(R.string.schedule_pm), selected = isPm) { onChange(true) }
        }
    }
}

@Composable
private fun AmPmSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) DarrbiTheme.colors.primary else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.size(width = 54.dp, height = 34.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = DarrbiTheme.typography.bodyMedium,
                color = if (selected) DarrbiTheme.colors.onPrimary else DarrbiTheme.colors.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// City picker
// ---------------------------------------------------------------------------

@Composable
internal fun CityPickerSheet(
    title: String,
    cities: List<C2cCity>,
    disabledCityId: String?,
    onSelect: (C2cCity) -> Unit,
) {
    val arabic = isArabicLocale()
    var query by remember { mutableStateOf("") }
    // Client-side filter over the in-memory list (matches English + Arabic name, case-insensitive).
    val filtered = remember(query, cities) {
        val q = query.trim()
        if (q.isBlank()) cities
        else cities.filter { c ->
            c.name.contains(q, ignoreCase = true) || (c.nameArabic?.contains(q, ignoreCase = true) == true)
        }
    }
    // imePadding lifts the city picker above the keyboard so the search field + city list stay visible.
    DarrbiCard(modifier = Modifier.imePadding(), navigationBarPadding = true) {
        Text(title, style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
        Spacer(Modifier.height(12.dp))
        SearchField(value = query, hint = stringResource(R.string.schedule_select_city_hint), onValueChange = { query = it })
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.schedule_no_city_match), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                filtered.forEachIndexed { index, city ->
                    val disabled = city.id == disabledCityId
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !disabled) { onSelect(city) }.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            city.displayName(arabic),
                            style = DarrbiTheme.typography.bodyMedium,
                            color = if (disabled) DarrbiTheme.colors.onSurfaceVariant.copy(alpha = 0.5f) else DarrbiTheme.colors.onSurface,
                        )
                    }
                    if (index < filtered.lastIndex) HorizontalDivider(color = DarrbiTheme.colors.outline)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Find Offers (review: cab + quote band + propose fare)
// ---------------------------------------------------------------------------

@Composable
internal fun FindOffersSheet(
    state: ScheduleUiState,
    onSeats: (Int) -> Unit,
    onSubmit: (Double) -> Unit,
    onHeight: (Int) -> Unit,
) {
    val arabic = isArabicLocale()
    val quote = state.quote
    val recommended = quote?.recommendedFare ?: 0.0
    val min = quote?.minFare ?: 0.0
    val max = quote?.maxFare ?: Double.MAX_VALUE
    val offered = state.offeredFare ?: recommended
    val stepSize = if (offered >= 50.0) 5.0 else 1.0
    var target by remember(offered) { mutableStateOf(offered) }

    Surface(
        modifier = Modifier.fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.schedule_find_offers_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            // "Scheduled for ..." banner.
            Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.schedule_scheduled_for, formatScheduleWhen(state.departureAtMillis)),
                    style = DarrbiTheme.typography.label,
                    color = DarrbiTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            // Route card (point, city → point, city).
            Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.surface, border = BorderStroke(1.dp, DarrbiTheme.colors.outline)) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    RouteHeader(
                        pickup = pointCity(state.pickup?.address ?: state.pickup?.name, state.originCity?.displayName(arabic)),
                        destination = pointCity(state.dropoff?.address ?: state.dropoff?.name, state.destinationCity?.displayName(arabic)),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // Seats stepper.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.schedule_seats_label), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
                ValueStepper(
                    value = state.seats.toString(),
                    onMinus = { onSeats(state.seats - 1) },
                    onPlus = { onSeats(state.seats + 1) },
                )
            }
            Spacer(Modifier.height(14.dp))
            // Recommended fare + propose stepper.
            Text(stringResource(R.string.schedule_recommended_fare), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.rider_sar_label), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 22.sp), color = DarrbiTheme.colors.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatFare(target), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 30.sp), color = DarrbiTheme.colors.onSurface)
                }
                OfferStepButton("−", accent = false, enabled = state.quote != null && target > min) { target = (target - stepSize).coerceAtLeast(min) }
                Spacer(Modifier.width(10.dp))
                OfferStepButton("+", accent = true, enabled = state.quote != null && target < max) { target = (target + stepSize).coerceAtMost(max) }
            }
            if (quote != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.schedule_fare_band, fareWithCurrency(quote.minFare), fareWithCurrency(quote.maxFare)),
                    style = DarrbiTheme.typography.caption,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            // Payment (wallet).
            Row(verticalAlignment = Alignment.CenterVertically) {
                CashGlyph()
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.schedule_pay_via_wallet), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.schedule_find_offers),
                enabled = state.quote != null && !state.isCreating && !state.isLoadingQuote,
                onClick = { onSubmit(target) },
            )
        }
    }
}

private fun pointCity(point: String?, city: String?): String =
    listOfNotNull(point?.takeIf { it.isNotBlank() }, city?.takeIf { it.isNotBlank() }).joinToString(", ")

// ---------------------------------------------------------------------------
// Ride Scheduled confirmation
// ---------------------------------------------------------------------------

@Composable
internal fun RideScheduledSheet(
    departureMillis: Long?,
    pickupCity: String?,
    dropoffCity: String?,
    onUpcoming: () -> Unit,
    onDone: () -> Unit,
) {
    DarrbiCard(navigationBarPadding = true) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(DarrbiTheme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, null, tint = DarrbiTheme.colors.onPrimary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.schedule_confirmed_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(formatScheduleWhen(departureMillis), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        if (!pickupCity.isNullOrBlank() && !dropoffCity.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.surface, border = BorderStroke(1.dp, DarrbiTheme.colors.outline), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { RouteHeader(pickup = pickupCity, destination = dropoffCity) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.schedule_confirmed_notify), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DarrbiSecondaryButton(text = stringResource(R.string.schedule_upcoming), onClick = onUpcoming, modifier = Modifier.weight(1f))
            DarrbiPrimaryButton(text = stringResource(R.string.common_done), onClick = onDone, modifier = Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------
// Upcoming rides list
// ---------------------------------------------------------------------------

@Composable
internal fun UpcomingRidesContent(
    state: ScheduleUiState,
    onOpen: (UpcomingScheduledTrip) -> Unit,
    onCancel: (String) -> Unit,
    onCreateNew: () -> Unit,
) {
    val arabic = isArabicLocale()
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // Loading is handled by the screen's overlay spinner; render nothing yet.
            state.upcoming.isEmpty() && state.isLoadingUpcoming -> Unit
            state.upcoming.isEmpty() -> EmptyUpcoming()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 104.dp),
            ) {
                items(state.upcoming, key = { it.tripId }) { trip ->
                    UpcomingRideCard(
                        trip = trip,
                        originName = state.city(trip.originCityId)?.displayName(arabic) ?: state.cityName(trip.originCityId),
                        destName = state.city(trip.destinationCityId)?.displayName(arabic) ?: state.cityName(trip.destinationCityId),
                        isCancelling = state.isCancelling,
                        onOpen = { onOpen(trip) },
                        onCancel = { onCancel(trip.tripId) },
                    )
                }
            }
        }
        // "Create a New Ride" bottom CTA, pinned to the bottom over a subtle scrim so the last card reads cleanly.
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)) {
            DarrbiPrimaryButton(text = stringResource(R.string.schedule_create_new), onClick = onCreateNew)
        }
    }
}

/** Centered illustration + copy for the no-scheduled-rides state. */
@Composable
private fun EmptyUpcoming() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(104.dp).clip(CircleShape).background(DarrbiTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.DateRange, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.schedule_no_upcoming),
            style = DarrbiTheme.typography.titleLarge,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.schedule_no_upcoming_subtitle),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Balance the bottom CTA so the content reads as vertically centered above it.
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun UpcomingRideCard(
    trip: UpcomingScheduledTrip,
    originName: String?,
    destName: String?,
    isCancelling: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatScheduleWhen(trip.scheduledDepartureAtMillis), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
                ScheduledBadge(trip.scheduledState)
            }
            Spacer(Modifier.height(12.dp))
            RouteHeader(pickup = originName.orEmpty(), destination = destName.orEmpty())
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rider_sar_amount, formatFare(trip.riderOfferedFare)), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.schedule_pay_label), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            WideTonalButton(text = stringResource(R.string.common_cancel), textColor = DarrbiTheme.colors.onSurface, enabled = !isCancelling, onClick = onCancel)
        }
    }
}

@Composable
private fun ScheduledBadge(stateValue: com.mytm.darrbi.domain.model.ScheduledTripState) {
    val label = when (stateValue) {
        com.mytm.darrbi.domain.model.ScheduledTripState.Open -> stringResource(R.string.schedule_badge_finding)
        com.mytm.darrbi.domain.model.ScheduledTripState.Matched,
        com.mytm.darrbi.domain.model.ScheduledTripState.Reminded -> stringResource(R.string.schedule_badge_confirmed)
        else -> stringResource(R.string.schedule_badge_scheduled)
    }
    Surface(shape = RoundedCornerShape(50), color = DarrbiTheme.colors.primary) {
        Text(label, style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

// ---------------------------------------------------------------------------
// Matched / waiting card
// ---------------------------------------------------------------------------

@Composable
internal fun ScheduledWaitingSheet(
    state: ScheduleUiState,
    onChat: () -> Unit,
    onCall: () -> Unit,
    onCancel: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    val bid = state.matchedBid
    Surface(
        modifier = Modifier.fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                stringResource(R.string.schedule_waiting_title, formatScheduleWhen(state.matchedDepartureMillis)),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = DarrbiTheme.colors.surface, border = BorderStroke(1.dp, DarrbiTheme.colors.primary)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rider_sar_amount, formatFare(state.matchedFare)), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
                        bid?.driverCar?.let { Text(it, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant) }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = DarrbiTheme.colors.outline)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = bid?.driverImageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.user_placeholder),
                            error = painterResource(R.drawable.user_placeholder),
                            fallback = painterResource(R.drawable.user_placeholder),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bid?.driverName ?: stringResource(R.string.rider_captain_fallback), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            bid?.driverRating?.let { Text(String.format(Locale.US, "%.1f/5", it), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant) }
                        }
                        ContactCircle(iconRes = R.drawable.icon_chat, contentDescription = stringResource(R.string.cd_chat), onClick = onChat)
                        Spacer(Modifier.width(10.dp))
                        ContactCircle(iconRes = R.drawable.icon_phone, contentDescription = stringResource(R.string.cd_call), onClick = onCall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            WideTonalButton(text = stringResource(R.string.schedule_cancel_ride), textColor = DarrbiTheme.colors.onSurface, enabled = !state.isCancelling, onClick = onCancel)
        }
    }
}

// ---------------------------------------------------------------------------
// Scheduled bidding overlay (no bids yet)
// ---------------------------------------------------------------------------

@Composable
internal fun ScheduledBiddingOverlay(
    state: ScheduleUiState,
    onRaise: (Double) -> Unit,
    onCancel: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    val offered = state.biddingOfferedFare
    val maxFare = state.biddingMaxFare
    val step = if (offered >= 50.0) 5.0 else 1.0
    val inFlight = state.isBidActionInFlight
    var target by remember(offered) { mutableStateOf(offered) }
    Surface(
        modifier = Modifier.fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.rider_searching_drivers), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.rider_your_offer_label), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.rider_sar_label), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 22.sp), color = DarrbiTheme.colors.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatFare(target), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 30.sp), color = DarrbiTheme.colors.onSurface)
                }
                OfferStepButton("−", accent = false, enabled = !inFlight && target > offered) { target = (target - step).coerceAtLeast(offered) }
                Spacer(Modifier.width(10.dp))
                OfferStepButton("+", accent = true, enabled = !inFlight && target < maxFare) { target = (target + step).coerceAtMost(maxFare) }
            }
            Spacer(Modifier.height(14.dp))
            WideTonalButton(text = stringResource(R.string.rider_change_price), textColor = DarrbiTheme.colors.onSurfaceVariant, enabled = !inFlight && target > offered, onClick = { onRaise(target) })
            Spacer(Modifier.height(16.dp))
            // Departure line.
            Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.schedule_scheduled_for, formatScheduleWhen(state.biddingDepartureMillis)),
                    style = DarrbiTheme.typography.label,
                    color = DarrbiTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            WideTonalButton(text = stringResource(R.string.rider_cancel_request), textColor = DarrbiTheme.colors.onSurface, enabled = !inFlight, onClick = onCancel)
        }
    }
}

@Composable
internal fun fareWithCurrency(value: Double): String = stringResource(R.string.rider_sar_amount, formatFare(value))
