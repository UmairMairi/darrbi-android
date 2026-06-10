package com.mytm.darrbi.presentation.rides

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.domain.model.Ride

/** Ride Details: route map + addresses, driver/rider card, Report A Problem, Download VAT Invoice. */
@Composable
fun RideDetailsScreen(
    ride: Ride,
    given: Boolean,
    onBack: () -> Unit,
    onReportProblem: () -> Unit,
    onDownloadInvoice: () -> Unit = {},
) {
    val placeholder = "—"
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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = DarrbiTheme.colors.onSurface,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ride_details_title),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
            )
            Text(
                text = formatRideDateTime(ride.timestampIso),
                style = DarrbiTheme.typography.body,
                color = DarrbiTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            RouteMap(ride)
            Spacer(Modifier.height(16.dp))
            DriverCard(ride, given, placeholder)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.ride_report_problem),
                style = DarrbiTheme.typography.button,
                color = DarrbiTheme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onReportProblem),
            )
            Spacer(Modifier.height(20.dp))
            DarrbiSecondaryButton(text = stringResource(R.string.ride_download_invoice), onClick = onDownloadInvoice)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RouteMap(ride: Ride) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarrbiTheme.colors.surfaceVariant),
    ) {
        if (ride.mapImageUrl != null) {
            AsyncImage(
                model = ride.mapImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        ride.pickupAddress?.let {
            AddressChip(it, Modifier.align(Alignment.TopStart).padding(16.dp))
        }
        ride.dropoffAddress?.let {
            AddressChip(it, Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }
        RatingPill(Modifier.align(Alignment.TopEnd).padding(12.dp))
    }
}

@Composable
private fun AddressChip(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = DarrbiTheme.colors.surface) {
        Text(
            text = text,
            style = DarrbiTheme.typography.label,
            color = DarrbiTheme.colors.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun RatingPill(modifier: Modifier = Modifier) {
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
private fun DriverCard(ride: Ride, given: Boolean, placeholder: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(DarrbiTheme.colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ride.personImageUrl != null) {
                        AsyncImage(
                            model = ride.personImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Filled.Person, null, tint = DarrbiTheme.colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(if (given) R.string.ride_your_rider else R.string.ride_your_driver),
                        style = DarrbiTheme.typography.body,
                        color = DarrbiTheme.colors.onSurfaceVariant,
                    )
                    Text(
                        text = ride.personName ?: placeholder,
                        style = DarrbiTheme.typography.title,
                        color = DarrbiTheme.colors.onSurface,
                    )
                }
            }
            DetailRow(stringResource(R.string.ride_car_type), ride.carType ?: placeholder)
            DetailRow(stringResource(R.string.ride_car_plate), placeholder)
            DetailRow(stringResource(R.string.ride_total_payment), "${stringResource(R.string.profile_currency_sar)} ${amountInt(ride.amount)}")
            DetailRow(stringResource(R.string.ride_loyalty_points), placeholder)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = DarrbiTheme.colors.outline)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}

private fun amountInt(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
