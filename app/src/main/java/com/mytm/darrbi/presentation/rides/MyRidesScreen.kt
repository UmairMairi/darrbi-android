package com.mytm.darrbi.presentation.rides

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.domain.model.Ride

/** My Rides: "Rides Taken" / "Rides Given" tabs with ride cards; the Given tab adds an earnings chart. */
@Composable
fun MyRidesScreen(
    onBack: () -> Unit,
    onRideClick: (Ride) -> Unit,
    viewModel: MyRidesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            BackButton(onBack, Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp))
            Text(
                text = stringResource(R.string.my_rides_title),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            RidesTabs(state.tab, viewModel::selectTab)
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.tab == RidesTab.Given) EarningsChartCard()
                state.rides.forEach { ride ->
                    RideCard(ride) { onRideClick(ride) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(DarrbiTheme.colors.surface)
            .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.cd_back),
            tint = DarrbiTheme.colors.onSurface,
        )
    }
}

@Composable
private fun RidesTabs(selected: RidesTab, onSelect: (RidesTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarrbiTheme.colors.surfaceVariant)
            .padding(4.dp),
    ) {
        TabPill(stringResource(R.string.rides_taken), selected == RidesTab.Taken, Modifier.weight(1f)) { onSelect(RidesTab.Taken) }
        TabPill(stringResource(R.string.rides_given), selected == RidesTab.Given, Modifier.weight(1f)) { onSelect(RidesTab.Given) }
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DarrbiTheme.colors.buttonContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = DarrbiTheme.typography.bodyMedium,
            color = if (selected) DarrbiTheme.colors.onButton else DarrbiTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun RideCard(ride: Ride, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(DarrbiTheme.colors.surfaceVariant),
            ) {
                if (ride.mapImageUrl != null) {
                    AsyncImage(
                        model = ride.mapImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
                RatingBadge(Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatRideDateTime(ride.timestampIso),
                    style = DarrbiTheme.typography.bodyMedium,
                    color = DarrbiTheme.colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${stringResource(R.string.profile_currency_sar)} ${amountText(ride.amount)}",
                    style = DarrbiTheme.typography.bodyMedium,
                    color = DarrbiTheme.colors.onSurface,
                )
                Spacer(Modifier.size(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = DarrbiTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RatingBadge(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = DarrbiTheme.colors.surface) {
        Text(
            text = stringResource(R.string.ride_rating_good),
            style = DarrbiTheme.typography.label,
            color = DarrbiTheme.colors.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EarningsChartCard() {
    val periods = listOf(
        R.string.chart_24h, R.string.chart_1w, R.string.chart_1m,
        R.string.chart_3m, R.string.chart_1y, R.string.chart_all,
    )
    var selectedIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(4) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarrbiTheme.colors.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                periods.forEachIndexed { i, res ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (i == selectedIndex) DarrbiTheme.colors.buttonContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { selectedIndex = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(res),
                            style = DarrbiTheme.typography.caption,
                            color = if (i == selectedIndex) DarrbiTheme.colors.onButton else DarrbiTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            EarningsChart(Modifier.fillMaxWidth().height(160.dp))
        }
    }
}

@Composable
private fun EarningsChart(modifier: Modifier = Modifier) {
    val labels = listOf("1000", "500", "250", "100", "50")
    val line = DarrbiTheme.colors.primary
    val grid = DarrbiTheme.colors.outline
    Row(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().weight(0.12f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach {
                Text(it, style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
            }
        }
        Canvas(modifier = Modifier.fillMaxSize().weight(0.88f)) {
            // Horizontal gridlines aligned to the y labels.
            val rows = labels.size
            for (i in 0 until rows) {
                val y = size.height * i / (rows - 1)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            // Decorative earnings curve (no analytics API exists in ride-android).
            val points = listOf(0.55f, 0.7f, 0.5f, 0.78f, 0.62f, 0.85f, 0.45f, 0.6f, 0.35f, 0.5f, 0.2f, 0.4f, 0.08f)
            val path = Path()
            points.forEachIndexed { i, frac ->
                val x = size.width * i / (points.size - 1)
                val y = size.height * frac
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        }
    }
}

private fun amountText(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()

/** ISO → "20 - 09 - 2022, 12:13pm". */
fun formatRideDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val inputs = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm")
    for (p in inputs) {
        runCatching {
            val date = java.text.SimpleDateFormat(p, java.util.Locale.ENGLISH).parse(iso) ?: return@runCatching
            val out = java.text.SimpleDateFormat("dd - MM - yyyy, h:mm", java.util.Locale.ENGLISH).format(date)
            val ampm = java.text.SimpleDateFormat("a", java.util.Locale.ENGLISH).format(date).lowercase()
            return "$out$ampm"
        }
    }
    return ""
}

// silence unused FontWeight import warning if optimized away
private val unusedFontWeight = FontWeight.Bold
