package com.mytm.darrbi.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.domain.model.C2cOpenScheduledTrip
import com.mytm.darrbi.domain.model.C2cQuote
import com.mytm.darrbi.domain.model.ScheduledTripState
import com.mytm.darrbi.presentation.rider.OfferStepButton
import com.mytm.darrbi.presentation.rider.formatFare
import com.mytm.darrbi.presentation.schedule.formatScheduleWhen

/**
 * Shared City-to-City (C2C) captain-side composables, used by both the dashboard (open requests + bid sheet)
 * and the scheduled-rides screen (state badge, route line). See [CaptainScheduleViewModel] /
 * [CaptainDashboardViewModel].
 */

/** Sentinels surfaced to the UI as toasts (mapped by [captainScheduleMessage]). */
internal const val C2C_BID_PLACED = "C2C_BID_PLACED"
internal const val C2C_BID_FAILED = "C2C_BID_FAILED"

@Composable
internal fun c2cMoney(currency: String, value: Double): String = "$currency ${formatFare(value)}"

/** Route line: "Origin → Destination". */
@Composable
internal fun C2cRouteLine(originName: String, destName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(originName, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(18.dp).padding(horizontal = 2.dp))
        Text(destName, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}

@Composable
internal fun ScheduledStateBadge(stateValue: ScheduledTripState) {
    val (labelRes, isActive) = when (stateValue) {
        ScheduledTripState.Open -> R.string.captain_sched_open to false
        ScheduledTripState.Matched -> R.string.captain_sched_matched to false
        ScheduledTripState.Reminded -> R.string.captain_sched_reminded to false
        ScheduledTripState.Activated -> R.string.captain_sched_activated to true
        else -> R.string.captain_sched_open to false
    }
    Surface(shape = RoundedCornerShape(8.dp), color = if (isActive) DarrbiTheme.colors.primary else DarrbiTheme.colors.onSurface.copy(alpha = 0.08f)) {
        Text(
            stringResource(labelRes),
            style = DarrbiTheme.typography.caption,
            color = if (isActive) DarrbiTheme.colors.onPrimary else DarrbiTheme.colors.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** An open intercity request card (driver can bid). */
@Composable
internal fun C2cOpenRequestCard(
    trip: C2cOpenScheduledTrip,
    originName: String,
    destName: String,
    onBid: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            C2cRouteLine(originName, destName)
            Spacer(Modifier.height(6.dp))
            Text(formatScheduleWhen(trip.scheduledDepartureAtMillis), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            Text(stringResource(R.string.captain_scheduled_seats, trip.seatsRequested), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.captain_scheduled_offered_label), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                    Text(c2cMoney(trip.currency, trip.riderOfferedFare), style = DarrbiTheme.typography.title.copy(fontSize = 18.sp), color = DarrbiTheme.colors.primary)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.buttonContainer, modifier = Modifier.clickable(onClick = onBid)) {
                    Text(stringResource(R.string.captain_scheduled_bid), style = DarrbiTheme.typography.button.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onButton, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
                }
            }
        }
    }
}

/** Bottom-sheet to bid on an open C2C request: accept the offered fare, or counter within the quote band. */
@Composable
internal fun C2cBidSheet(
    trip: C2cOpenScheduledTrip,
    quote: C2cQuote?,
    isLoadingQuote: Boolean,
    isPlacing: Boolean,
    originName: String,
    destName: String,
    onAccept: () -> Unit,
    onCounter: (Double) -> Unit,
) {
    val min = quote?.minFare ?: trip.riderOfferedFare
    val max = quote?.maxFare ?: trip.riderOfferedFare
    val step = if (trip.riderOfferedFare >= 50.0) 5.0 else 1.0
    var counter by remember(quote, trip.tripId) { mutableStateOf(trip.riderOfferedFare.coerceIn(min, max)) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Text(stringResource(R.string.captain_scheduled_place_bid), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            C2cRouteLine(originName, destName)
            Spacer(Modifier.height(4.dp))
            Text(formatScheduleWhen(trip.scheduledDepartureAtMillis), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            DarrbiPrimaryButton(
                text = stringResource(R.string.captain_scheduled_accept_fare, c2cMoney(trip.currency, trip.riderOfferedFare)),
                enabled = !isPlacing,
                onClick = onAccept,
            )

            if (quote != null && max > min) {
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.captain_scheduled_counter_label), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text(trip.currency, style = DarrbiTheme.typography.titleLarge.copy(fontSize = 20.sp), color = DarrbiTheme.colors.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(formatFare(counter), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 28.sp), color = DarrbiTheme.colors.onSurface)
                    }
                    OfferStepButton("−", accent = false, enabled = counter > min) { counter = (counter - step).coerceAtLeast(min) }
                    Spacer(Modifier.width(10.dp))
                    OfferStepButton("+", accent = true, enabled = counter < max) { counter = (counter + step).coerceAtMost(max) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.captain_scheduled_fare_band, c2cMoney(trip.currency, min), c2cMoney(trip.currency, max)),
                    style = DarrbiTheme.typography.caption,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                DarrbiSecondaryButton(
                    text = stringResource(R.string.captain_scheduled_counter_bid),
                    enabled = !isPlacing,
                    onClick = { onCounter(counter) },
                )
            } else if (isLoadingQuote) {
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarrbiTheme.colors.primary, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** Map a VM sentinel / V2 machine code to a localized message; otherwise pass the text through. */
@Composable
internal fun captainScheduleMessage(code: String?): String? {
    code ?: return null
    return when (code) {
        C2C_BID_PLACED -> stringResource(R.string.captain_scheduled_bid_placed)
        "DRIVER_INELIGIBLE" -> stringResource(R.string.captain_bid_err_ineligible)
        "CAB_TYPE_MISMATCH" -> stringResource(R.string.captain_bid_err_cab)
        "BIDDING_CLOSED" -> stringResource(R.string.captain_bid_err_closed)
        "BID_BELOW_FLOOR", "OFFER_OUT_OF_BAND" -> stringResource(R.string.captain_bid_err_band)
        C2C_BID_FAILED -> stringResource(R.string.captain_bid_err_generic)
        else -> code
    }
}
