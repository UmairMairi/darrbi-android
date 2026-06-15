package com.mytm.darrbi.presentation.rider

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.CabOption
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.mytm.darrbi.R
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.presentation.common.LocationPermissionDeniedDialog
import com.mytm.darrbi.presentation.common.ModeToggle
import com.mytm.darrbi.presentation.common.RideMode
import com.mytm.darrbi.presentation.common.openAppSettings
import com.mytm.darrbi.presentation.common.rememberLocationPermissionState

/**
 * Rider ride-booking flow (location selection): home map → destination search → pickup search →
 * confirm pickup → ride selection. Search is powered by the Google Places SDK; location by [MapService].
 * On entry the screen asks for location permission and, once granted, centres the map on the device.
 */
@Composable
fun RiderFlowScreen(
    onProfile: () -> Unit = {},
    onSwitchToCaptain: () -> Unit = {},
    modeSwitching: Boolean = false,
    viewModel: RiderBookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Surface transient errors (e.g. a failed cancel) and consume them so they show once.
    val errorContext = LocalContext.current
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            android.widget.Toast.makeText(errorContext, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.onEvent(RiderBookingEvent.ConsumeError)
        }
    }

    // Ask for location permission on arrival; once granted, centre the map on the current location.
    val locationPermission = rememberLocationPermissionState()
    LaunchedEffect(Unit) { locationPermission.requestIfNeeded() }
    LaunchedEffect(locationPermission.isGranted) {
        if (locationPermission.isGranted) viewModel.onEvent(RiderBookingEvent.LocateMe)
    }

    BackHandler(enabled = state.step != RiderStep.Home) { viewModel.onEvent(RiderBookingEvent.Back) }

    // One shared camera for every map-backed step, so the recenter button can move it.
    val defaultLocation = LatLng(24.7136, 46.6753)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultLocation, 14f) }
    // Follow the device location to the map as soon as it resolves (smooth pan, instant-move fallback).
    LaunchedEffect(state.myLocation) {
        state.myLocation?.let {
            val target = LatLng(it.latitude, it.longitude)
            runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 16f), 800) }
                .onFailure { cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 16f) }
        }
    }
    val recenter = {
        val target = state.myLocation?.let { LatLng(it.latitude, it.longitude) } ?: defaultLocation
        cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 16f)
    }

    // On confirm-pickup / select-ride, frame the pickup + destination + route on the map.
    val density = LocalDensity.current
    val showRoute = state.step == RiderStep.ConfirmPickup ||
        state.step == RiderStep.SelectRide ||
        state.step == RiderStep.Searching
    // Measured bottom-sheet height → reserve that area (plus the status bar) so the route frames ABOVE it.
    var routeSheetHeightPx by remember { mutableStateOf(0) }
    val statusTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val mapContentPadding = if (showRoute) {
        PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = statusTopDp + 16.dp,
            bottom = with(density) { routeSheetHeightPx.toDp() } + 16.dp,
        )
    } else {
        PaddingValues(0.dp)
    }
    LaunchedEffect(showRoute, state.pickup, state.destination, state.routePoints, routeSheetHeightPx) {
        val pickup = state.pickup
        val destination = state.destination
        if (showRoute && pickup != null && destination != null) {
            val pts = buildList {
                add(LatLng(pickup.latitude, pickup.longitude))
                add(LatLng(destination.latitude, destination.longitude))
                addAll(state.routePoints.map { LatLng(it.latitude, it.longitude) })
            }
            val bounds = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
            runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_BOUNDS_PADDING_PX), 700) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarrbiTheme.colors.surfaceVariant)) {
        // The map picker draws its own interactive map; every other step uses the static background map.
        if (state.step != RiderStep.MapPicker) {
            RiderMap(
                cameraPositionState = cameraPositionState,
                showMyLocation = locationPermission.isGranted,
                pickup = if (showRoute) state.pickup else null,
                destination = if (showRoute) state.destination else null,
                routePoints = if (showRoute) state.routePoints else emptyList(),
                contentPadding = mapContentPadding,
            )
        }
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
                // Open centred on the current location; fall back to the shared camera's last position.
                start = state.myLocation?.let { LatLng(it.latitude, it.longitude) } ?: cameraPositionState.position.target,
                showMyLocation = locationPermission.isGranted,
                onConfirm = { lat, lng -> viewModel.onEvent(RiderBookingEvent.ConfirmMapLocation(lat, lng)) },
            )
            RiderStep.ConfirmPickup -> ConfirmPickupOverlay(
                address = state.pickup?.address.orEmpty(),
                onEdit = { viewModel.onEvent(RiderBookingEvent.EditPickup) },
                onConfirm = { viewModel.onEvent(RiderBookingEvent.ProceedToRideSelection) },
                onHeight = { routeSheetHeightPx = it },
            )
            RiderStep.SelectRide -> SelectRideOverlay(
                state = state,
                onEvent = viewModel::onEvent,
                onHeight = { routeSheetHeightPx = it },
            )
            RiderStep.Searching -> SearchingOverlay(
                noCaptainFound = state.noCaptainFound,
                onTryAgain = { viewModel.onEvent(RiderBookingEvent.TryAgainSearch) },
                onCancel = { viewModel.onEvent(RiderBookingEvent.CancelRide) },
                onHeight = { routeSheetHeightPx = it },
            )
        }
        // RIDER/CAPTAIN mode toggle, top-start over the map (location-selection steps only).
        if (state.step == RiderStep.Home || state.step == RiderStep.DestinationSearch || state.step == RiderStep.PickupSearch) {
            ModeToggle(
                selected = RideMode.Rider,
                onSelect = { if (it == RideMode.Captain) onSwitchToCaptain() },
                enabled = !modeSwitching,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
            )
        }
        if (state.isResolving || state.isCancelling) {
            CircularProgressIndicator(color = DarrbiTheme.colors.primary, modifier = Modifier.align(Alignment.Center))
        }
    }

    // Location denied with "don't ask again" → instructions + a button into system Settings.
    if (locationPermission.isPermanentlyDenied) {
        val context = LocalContext.current
        LocationPermissionDeniedDialog(onOpenSettings = { context.openAppSettings() })
    }
}

/** Extra breathing room (px) around the route bounds; the map's contentPadding does the main framing. */
private const val ROUTE_BOUNDS_PADDING_PX = 48

@Composable
private fun RiderMap(
    cameraPositionState: CameraPositionState,
    showMyLocation: Boolean,
    pickup: PlaceLocation? = null,
    destination: PlaceLocation? = null,
    routePoints: List<LatLngPoint> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val routeColor = DarrbiTheme.colors.primary
    val pickupLatLng = pickup?.let { LatLng(it.latitude, it.longitude) }
    val destLatLng = destination?.let { LatLng(it.latitude, it.longitude) }
    // Full route (real road route when available; otherwise a straight pickup→destination line).
    val fullRoute = remember(pickupLatLng, destLatLng, routePoints) {
        if (pickupLatLng != null && destLatLng != null) {
            if (routePoints.size >= 2) routePoints.map { LatLng(it.latitude, it.longitude) }
            else listOf(pickupLatLng, destLatLng)
        } else {
            emptyList()
        }
    }
    // Animate the polyline drawing from start → end whenever the route changes.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fullRoute) {
        if (fullRoute.size >= 2) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing))
        }
    }
    val drawnRoute = remember(fullRoute, progress.value) { partialPath(fullRoute, progress.value) }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        properties = MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = showMyLocation),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false, compassEnabled = false),
    ) {
        if (pickupLatLng != null && destLatLng != null && drawnRoute.size >= 2) {
            Polyline(points = drawnRoute, color = routeColor, width = 6f)
            // Same marker_1 pin for both the route start (pickup) and end (destination).
            // Anchor at the icon centre (default is bottom-centre) so its centre sits on the location.
            val markerIcon = rememberMarkerIcon()
            val markerAnchor = Offset(0.5f, 0.5f)
            Marker(
                state = rememberMarkerState(key = "pickup_${pickupLatLng.latitude},${pickupLatLng.longitude}", position = pickupLatLng),
                icon = markerIcon,
                anchor = markerAnchor,
            )
            Marker(
                state = rememberMarkerState(key = "dest_${destLatLng.latitude},${destLatLng.longitude}", position = destLatLng),
                icon = markerIcon,
                anchor = markerAnchor,
            )
        }
    }
}

/** The leading [fraction] (0..1) of [points], interpolating the head point for a smooth draw-on. */
private fun partialPath(points: List<LatLng>, fraction: Float): List<LatLng> {
    if (points.size < 2) return points
    if (fraction >= 1f) return points
    if (fraction <= 0f) return listOf(points.first())
    val position = (points.size - 1) * fraction
    val index = position.toInt()
    val segmentFraction = position - index
    val head = points.subList(0, index + 1).toMutableList()
    if (index < points.size - 1) {
        val a = points[index]
        val b = points[index + 1]
        head.add(
            LatLng(
                a.latitude + (b.latitude - a.latitude) * segmentFraction,
                a.longitude + (b.longitude - a.longitude) * segmentFraction,
            ),
        )
    }
    return head
}

/** Scaled `marker_1` pin used for the route endpoints (aspect ratio 156×184 preserved). */
@Composable
private fun rememberMarkerIcon(): BitmapDescriptor? {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember {
        runCatching {
            val heightPx = with(density) { 46.dp.roundToPx() }
            val widthPx = (heightPx * 156f / 184f).toInt()
            val source = (ContextCompat.getDrawable(context, R.drawable.marker_1) as BitmapDrawable).bitmap
            BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(source, widthPx, heightPx, true))
        }.getOrNull()
    }
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
    // imePadding on the card (not its content) lifts the whole card above the keyboard with no inner gap.
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .wrapContentHeight()
            .imePadding(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
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
private fun androidx.compose.foundation.layout.BoxScope.MapPickerOverlay(
    start: LatLng,
    showMyLocation: Boolean,
    onConfirm: (Double, Double) -> Unit,
) {
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(start, 16f) }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = showMyLocation),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false, compassEnabled = false),
    )
    // Fixed centre marker — the map moves under it; its tip marks the chosen location.
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.marker_1),
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
            painter = painterResource(R.drawable.icon_marker_history),
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
private fun androidx.compose.foundation.layout.BoxScope.ConfirmPickupOverlay(
    address: String,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    // The pickup + destination markers and route are drawn on the map itself (see RiderMap).
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rider_pickup_location), style = DarrbiTheme.typography.title.copy(fontSize = 16.sp), color = DarrbiTheme.colors.onSurface, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.rider_edit), style = DarrbiTheme.typography.button.copy(fontSize = 14.sp), color = DarrbiTheme.colors.primary, modifier = Modifier.clickable(onClick = onEdit))
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(address, style = DarrbiTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = DarrbiTheme.colors.onSurface, maxLines = 2)
            }
            Spacer(Modifier.height(14.dp))
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
    onHeight: (Int) -> Unit,
) {
    var showPromo by remember { mutableStateOf(false) }
    var showAddBalance by remember { mutableStateOf(false) }
    val insufficient = state.isBalanceInsufficient

    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().wrapContentHeight().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            RouteHeader(
                pickup = state.pickup?.address?.takeIf { it.isNotBlank() } ?: stringResource(R.string.rider_current_location),
                destination = state.destination?.address.orEmpty(),
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.rider_recommended), style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(6.dp))
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
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.rider_more_available_rides), style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(8.dp))
            // Balance + promo row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.rider_balance), style = DarrbiTheme.typography.label.copy(fontSize = 12.sp), color = DarrbiTheme.colors.onSurfaceVariant)
                    Text(
                        text = "${stringResource(R.string.profile_currency_sar)} ${formatFare(state.balance ?: 0.0)}",
                        style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
                        color = if (insufficient) DarrbiTheme.colors.error else DarrbiTheme.colors.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.rider_promo),
                    style = DarrbiTheme.typography.button.copy(fontSize = 14.sp),
                    color = DarrbiTheme.colors.primary,
                    modifier = Modifier.clickable { showPromo = true }.padding(8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
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
        com.mytm.darrbi.presentation.topup.AddBalanceSheet(
            onDismiss = { showAddBalance = false },
            onTopUpSuccess = { onEvent(RiderBookingEvent.RefreshBalance) },
        )
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
            Text(pickup, style = DarrbiTheme.typography.title.copy(fontSize = 14.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(8.dp))
            Text(destination, style = DarrbiTheme.typography.title.copy(fontSize = 14.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = cab.imageUrl,
            contentDescription = null,
            modifier = Modifier.width(60.dp).height(40.dp),
            contentScale = ContentScale.Fit,
            placeholder = painterResource(R.drawable.placeholder_select_car),
            error = painterResource(R.drawable.placeholder_select_car),
            fallback = painterResource(R.drawable.placeholder_select_car),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cab.name, style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
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
                    style = DarrbiTheme.typography.title.copy(fontSize = 15.sp),
                    color = DarrbiTheme.colors.onSurface,
                )
            } else {
                Text("$currency ${formatFare(cab.fare)}", style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface)
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
private fun androidx.compose.foundation.layout.BoxScope.SearchingOverlay(
    noCaptainFound: Boolean,
    onTryAgain: () -> Unit,
    onCancel: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    // The pickup + destination markers and route are drawn on the map (same as confirm-pickup / select-ride).
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Car illustration with a badge: a clock while waiting, an info "i" once no captain was found.
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.image_car),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(0.55f),
                    contentScale = ContentScale.Fit,
                )
                if (noCaptainFound) {
                    // Icons.Filled.Info is a solid disc with a cut-out "i"; tinted green it reads as a
                    // green circle with a white "i" over the white card.
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = DarrbiTheme.colors.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).size(34.dp),
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.icon_rider_wait),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomEnd).size(34.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(if (noCaptainFound) R.string.rider_no_captain_title else R.string.rider_searching_title),
                style = DarrbiTheme.typography.titleLarge.copy(fontSize = 19.sp),
                color = DarrbiTheme.colors.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            if (noCaptainFound) {
                DarrbiPrimaryButton(text = stringResource(R.string.rider_try_again), onClick = onTryAgain)
                Spacer(Modifier.height(12.dp))
                DarrbiSecondaryButton(text = stringResource(R.string.rider_searching_cancel), onClick = onCancel)
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.width(120.dp).clip(CircleShape),
                    color = DarrbiTheme.colors.onSurface,
                    trackColor = DarrbiTheme.colors.outline,
                )
                Spacer(Modifier.height(20.dp))
                DarrbiSecondaryButton(text = stringResource(R.string.rider_searching_cancel), onClick = onCancel)
            }
        }
    }
}

/** Rounds to ≤2 decimals and drops trailing zeros (ride-android's roundWith). */
private fun formatFare(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
