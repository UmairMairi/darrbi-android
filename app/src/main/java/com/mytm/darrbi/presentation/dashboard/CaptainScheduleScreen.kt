package com.mytm.darrbi.presentation.dashboard

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.domain.model.UpcomingScheduledTrip
import com.mytm.darrbi.presentation.schedule.displayName
import com.mytm.darrbi.presentation.schedule.formatScheduleWhen
import com.mytm.darrbi.presentation.schedule.isArabicLocale

/**
 * DRIVER-side City-to-City scheduled rides screen — the driver's matched/upcoming scheduled rides only.
 * Open intercity requests to bid on are surfaced on the captain dashboard. See [CaptainScheduleViewModel].
 *
 * @param onBack return to the captain dashboard.
 */
@Composable
fun CaptainScheduleScreen(
    onBack: () -> Unit,
    viewModel: CaptainScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val arabic = isArabicLocale()

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().background(DarrbiTheme.colors.surface)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_back), onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.captain_scheduled_title), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Filled.Refresh, stringResource(R.string.captain_scheduled_refresh), onClick = { viewModel.onEvent(CaptainScheduleEvent.Refresh) })
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            ) {
                Text(stringResource(R.string.captain_scheduled_mine), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
                Spacer(Modifier.height(10.dp))
                if (state.upcoming.isEmpty() && !state.isLoading) {
                    Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.captain_scheduled_empty_mine), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                    }
                } else {
                    state.upcoming.forEach { trip ->
                        UpcomingRideCard(
                            trip = trip,
                            originName = state.city(trip.originCityId)?.displayName(arabic) ?: trip.originCityId,
                            destName = state.city(trip.destinationCityId)?.displayName(arabic) ?: trip.destinationCityId,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (state.isLoading && state.upcoming.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarrbiTheme.colors.primary)
            }
        }
    }
}

@Composable
private fun CircleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(DarrbiTheme.colors.surface)
            .border(1.dp, DarrbiTheme.colors.outline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, cd, tint = DarrbiTheme.colors.onSurface)
    }
}

@Composable
private fun UpcomingRideCard(
    trip: UpcomingScheduledTrip,
    originName: String,
    destName: String,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { C2cRouteLine(originName, destName) }
                ScheduledStateBadge(trip.scheduledState)
            }
            Spacer(Modifier.height(6.dp))
            Text(formatScheduleWhen(trip.scheduledDepartureAtMillis), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(c2cMoney(trip.currency, trip.riderOfferedFare), style = DarrbiTheme.typography.title.copy(fontSize = 16.sp), color = DarrbiTheme.colors.onSurface)
        }
    }
}
