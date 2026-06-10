package com.mytm.darrbi.presentation.rider

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.CabOption
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.domain.model.PlaceSuggestion

/**
 * Rider ride-booking flow (location selection): home map → destination search → pickup search →
 * confirm pickup. Search is powered by the Google Places SDK; "Use Current Location" by device GPS.
 * [onPickupConfirmed] hands off to ride selection (next increment).
 */
@Composable
fun RiderFlowScreen(
    onProfile: () -> Unit = {},
    viewModel: RiderBookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    BackHandler(enabled = state.step != RiderStep.Home) { viewModel.onEvent(RiderBookingEvent.Back) }

    // One shared camera for every map-backed step, so the recenter button can move it.
    val defaultLocation = LatLng(24.7136, 46.6753)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultLocation, 14f) }
    val recenter = { cameraPositionState.position = CameraPosition.fromLatLngZoom(defaultLocation, 15f) }

    Box(modifier = Modifier.fillMaxSize().background(DarrbiTheme.colors.surfaceVariant)) {
        // The map picker draws its own interactive map; every other step uses the static background map.
        if (state.step != RiderStep.MapPicker) RiderMap(cameraPositionState)
        when (state.step) {
            RiderStep.Home -> HomeOverlay(onProfile = onProfile, onSearch = { viewModel.onEvent(RiderBookingEvent.OpenDestinationSearch) })
            RiderStep.DestinationSearch -> SearchOverlay(
                title = null,
                hint = stringResource(R.string.rider_where_to),
                state = state,
                onEvent = viewModel::onEvent,
                showCurrentLocation = false,
                onRecenter = recenter,
            )
            RiderStep.PickupSearch -> SearchOverlay(
                title = stringResource(R.string.rider_pickup_location),
                hint = stringResource(R.string.rider_enter_here),
                state = state,
                onEvent = viewModel::onEvent,
                showCurrentLocation = true,
                onRecenter = recenter,
            )
            RiderStep.MapPicker -> MapPickerOverlay(
                onConfirm = { lat, lng -> viewModel.onEvent(RiderBookingEvent.ConfirmMapLocation(lat, lng)) },
            )
            RiderStep.ConfirmPickup -> ConfirmPickupOverlay(
                address = state.pickup?.address.orEmpty(),
                onEdit = { viewModel.onEvent(RiderBookingEvent.EditPickup) },
                onConfirm = { viewModel.onEvent(RiderBookingEvent.ProceedToRideSelection) },
            )
            RiderStep.SelectRide -> SelectRideOverlay(state = state, onEvent = viewModel::onEvent)
            RiderStep.Searching -> SearchingOverlay(onCancel = { viewModel.onEvent(RiderBookingEvent.Back) })
        }
        if (state.isResolving) {
            CircularProgressIndicator(color = DarrbiTheme.colors.primary, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun RiderMap(cameraPositionState: CameraPositionState) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.NORMAL),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false, compassEnabled = false),
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.HomeOverlay(onProfile: () -> Unit, onSearch: () -> Unit) {
    // Profile avatar, top end.
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(16.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(DarrbiTheme.colors.surface)
            .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
            .clickable(onClick = onProfile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Person, stringResource(R.string.cd_profile), tint = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.size(26.dp))
    }
    // "Where to?" bar at the bottom.
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 4.dp,
        onClick = onSearch,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.rider_where_to), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SearchOverlay(
    title: String?,
    hint: String,
    state: RiderBookingUiState,
    onEvent: (RiderBookingEvent) -> Unit,
    showCurrentLocation: Boolean,
    onRecenter: () -> Unit,
) {
    // Recenter button floating over the map strip kept visible above the sheet.
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(16.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(DarrbiTheme.colors.surface)
            .clickable(onClick = onRecenter),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.MyLocation, stringResource(R.string.cd_recenter), tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(24.dp))
    }

    // Bottom sheet: search field → set-from-map → live results. Wraps its content; the map shows above.
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                Text(title, style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface, modifier = Modifier.padding(start = 4.dp))
            }
            SearchField(value = state.query, hint = hint, onValueChange = { onEvent(RiderBookingEvent.QueryChanged(it)) })
            SetLocationFromMapButton(onClick = { onEvent(RiderBookingEvent.OpenMapPicker) })
            val hasRows = showCurrentLocation || state.suggestions.isNotEmpty()
            if (hasRows) {
                // Results update live; the list caps its height and scrolls so the sheet stays compact.
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (showCurrentLocation) {
                        CurrentLocationRow(onClick = { onEvent(RiderBookingEvent.UseCurrentLocation) })
                        if (state.suggestions.isNotEmpty()) HorizontalDivider(color = DarrbiTheme.colors.outline)
                    }
                    state.suggestions.forEachIndexed { index, suggestion ->
                        SuggestionRow(suggestion) { onEvent(RiderBookingEvent.SelectSuggestion(suggestion)) }
                        if (index < state.suggestions.lastIndex) HorizontalDivider(color = DarrbiTheme.colors.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetLocationFromMapButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = DarrbiTheme.colors.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PushPin, null, tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.rider_set_location_from_map), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.MapPickerOverlay(onConfirm: (Double, Double) -> Unit) {
    val riyadh = LatLng(24.7136, 46.6753)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(riyadh, 16f) }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.NORMAL),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false, compassEnabled = false),
    )
    // Fixed centre marker — the map moves under it; its tip marks the chosen location.
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.marker_1),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.Center)
            .offset(y = (-24).dp)
            .size(48.dp),
    )
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Text(stringResource(R.string.rider_set_location_from_map), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(16.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.rider_confirm_location),
                onClick = {
                    val target = cameraPositionState.position.target
                    onConfirm(target.latitude, target.longitude)
                },
            )
        }
    }
}

@Composable
private fun SearchField(value: String, hint: String, onValueChange: (String) -> Unit) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(hint, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = DarrbiTheme.typography.body.copy(color = DarrbiTheme.colors.onSurface),
                    cursorBrush = SolidColor(DarrbiTheme.colors.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
        }
    }
}

@Composable
private fun CurrentLocationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.MyLocation, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.rider_use_current_location), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}

@Composable
private fun SuggestionRow(suggestion: PlaceSuggestion, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.icon_marker_history),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(suggestion.primaryText, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, maxLines = 1)
            if (suggestion.secondaryText.isNotBlank()) {
                Text(suggestion.secondaryText, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ConfirmPickupOverlay(address: String, onEdit: () -> Unit, onConfirm: () -> Unit) {
    // Centre pickup pin over the map.
    Icon(
        imageVector = Icons.Filled.Place,
        contentDescription = null,
        tint = DarrbiTheme.colors.onSurface,
        modifier = Modifier.align(Alignment.Center).size(40.dp),
    )
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rider_pickup_location), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.rider_edit), style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.primary, modifier = Modifier.clickable(onClick = onEdit))
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(address, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface, maxLines = 2)
            }
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(text = stringResource(R.string.rider_confirm_pickup), onClick = onConfirm)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Ride selection (cab list + fare + promo + balance), then searching for a captain.
// ---------------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SelectRideOverlay(
    state: RiderBookingUiState,
    onEvent: (RiderBookingEvent) -> Unit,
) {
    var showPromo by remember { mutableStateOf(false) }
    var showAddBalance by remember { mutableStateOf(false) }
    val insufficient = state.isBalanceInsufficient

    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().wrapContentHeight(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            RouteHeader(
                pickup = state.pickup?.address?.takeIf { it.isNotBlank() } ?: stringResource(R.string.rider_current_location),
                destination = state.destination?.address.orEmpty(),
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.rider_recommended), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(8.dp))
            // Cab list — caps its height and scrolls only when the cabs overflow, so the sheet wraps tightly.
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                when {
                    state.isLoadingCabs -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DarrbiTheme.colors.primary)
                    }
                    else -> state.cabs.forEachIndexed { index, cab ->
                        CabRow(
                            cab = cab,
                            selected = cab.id == state.selectedCabId,
                            promo = state.promo,
                            onClick = { onEvent(RiderBookingEvent.SelectCab(cab.id)) },
                        )
                        if (index < state.cabs.lastIndex) HorizontalDivider(color = DarrbiTheme.colors.outline)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.rider_more_available_rides), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(12.dp))
            // Balance + promo row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.rider_balance), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
                    Text(
                        text = "${stringResource(R.string.profile_currency_sar)} ${formatFare(state.balance ?: 0.0)}",
                        style = DarrbiTheme.typography.title,
                        color = if (insufficient) DarrbiTheme.colors.error else DarrbiTheme.colors.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.rider_promo),
                    style = DarrbiTheme.typography.button,
                    color = DarrbiTheme.colors.primary,
                    modifier = Modifier.clickable { showPromo = true }.padding(8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            DarrbiPrimaryButton(
                text = stringResource(if (insufficient) R.string.rider_add_balance else R.string.rider_confirm_ride),
                onClick = { if (insufficient) showAddBalance = true else onEvent(RiderBookingEvent.ConfirmRide) },
                enabled = state.selectedCab != null && !state.isBooking,
            )
        }
    }
    if (state.isBooking) {
        CircularProgressIndicator(color = DarrbiTheme.colors.primary, modifier = Modifier.align(Alignment.Center))
    }
    if (showPromo) {
        PromoSheet(
            applied = state.promo,
            loading = state.isPromoLoading,
            onApply = { code -> onEvent(RiderBookingEvent.ApplyPromo(code)); showPromo = false },
            onDismiss = { showPromo = false },
        )
    }
    if (showAddBalance) {
        com.mytm.darrbi.presentation.topup.AddBalanceSheet(onDismiss = { showAddBalance = false })
    }
}

@Composable
private fun RouteHeader(pickup: String, destination: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(14.dp).clip(CircleShape).border(3.dp, DarrbiTheme.colors.primary, CircleShape))
            Box(Modifier.width(1.dp).height(28.dp).background(DarrbiTheme.colors.outline))
            Box(Modifier.size(14.dp).clip(CircleShape).border(3.dp, DarrbiTheme.colors.error, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(pickup, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(10.dp))
            Text(destination, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun CabRow(cab: CabOption, selected: Boolean, promo: AppliedPromo?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) DarrbiTheme.colors.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = cab.imageUrl,
            contentDescription = null,
            modifier = Modifier.width(68.dp).height(46.dp),
            contentScale = ContentScale.Fit,
            placeholder = painterResource(R.drawable.placeholder_select_car),
            error = painterResource(R.drawable.placeholder_select_car),
            fallback = painterResource(R.drawable.placeholder_select_car),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cab.name, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface, maxLines = 1)
                if (cab.seats > 0) {
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Person, null, tint = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(cab.seats.toString(), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(etaLine(cab), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        val currency = stringResource(R.string.profile_currency_sar)
        val discount = promo?.discount ?: 0.0
        Column(horizontalAlignment = Alignment.End) {
            if (selected && discount > 0.0) {
                Text(
                    "$currency ${formatFare(cab.fare)}",
                    style = DarrbiTheme.typography.label,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                )
                Text(
                    "$currency ${formatFare((cab.fare - discount).coerceAtLeast(0.0))}",
                    style = DarrbiTheme.typography.title,
                    color = DarrbiTheme.colors.onSurface,
                )
            } else {
                Text("$currency ${formatFare(cab.fare)}", style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            }
        }
    }
}

/** "3 min away · 3:09pm", or the no-captain line when no ETA is available. */
@Composable
private fun etaLine(cab: CabOption): String {
    val eta = cab.etaMinutes ?: return stringResource(R.string.rider_no_captain)
    val minutes = eta.filter { it.isDigit() }.toIntOrNull()
    val clock = minutes?.let {
        runCatching {
            LocalTime.now().plusMinutes(it.toLong()).format(DateTimeFormatter.ofPattern("h:mma", Locale.US)).lowercase(Locale.US)
        }.getOrNull()
    }
    val away = "$eta ${stringResource(R.string.rider_min_away)}"
    return if (clock != null) "$away · $clock" else away
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PromoSheet(applied: AppliedPromo?, loading: Boolean, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var code by remember { mutableStateOf(applied?.code.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarrbiTheme.colors.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Text(stringResource(R.string.rider_promo_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    if (code.isEmpty()) {
                        Text(stringResource(R.string.rider_promo_hint), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = code,
                        onValueChange = { code = it },
                        singleLine = true,
                        textStyle = DarrbiTheme.typography.body.copy(color = DarrbiTheme.colors.onSurface),
                        cursorBrush = SolidColor(DarrbiTheme.colors.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.rider_promo_apply),
                onClick = { onApply(code.trim()) },
                enabled = code.isNotBlank() && !loading,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SearchingOverlay(onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = DarrbiTheme.colors.primary)
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.rider_searching_title),
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.rider_searching_cancel),
                style = DarrbiTheme.typography.button,
                color = DarrbiTheme.colors.error,
                modifier = Modifier.clickable(onClick = onCancel).padding(8.dp),
            )
        }
    }
}

/** Rounds to ≤2 decimals and drops trailing zeros (ride-android's roundWith). */
private fun formatFare(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
