package com.mytm.darrbi.presentation.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiCard
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.presentation.common.rememberLocationPermissionState
import com.mytm.darrbi.presentation.rider.CurrentLocationRow
import com.mytm.darrbi.presentation.rider.RiderMap
import com.mytm.darrbi.presentation.rider.SearchField
import com.mytm.darrbi.presentation.rider.SelectOffersList
import com.mytm.darrbi.presentation.rider.SuggestionRow

/**
 * Scheduled City-to-City (C2C) rider flow — a self-contained step machine ([ScheduleStep]) over a background
 * map. The bid/select phase reuses the V2 bidding UI ([SelectOffersList]) and engine; after activation the
 * trip becomes a normal ongoing trip handled by the existing rider-home flow.
 *
 * @param onBack exit the flow back to the rider home.
 * @param onChat open the in-trip chat with the matched driver (id, name, image).
 * @param onCall dial the matched driver.
 */
@Composable
fun ScheduleFlowScreen(
    onBack: () -> Unit,
    onChat: (driverId: String, name: String, imageUrl: String?) -> Unit = { _, _, _ -> },
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Friendly error toasts (C2C codes → strings); consume once.
    val errorText = scheduleErrorText(state.errorMessage)
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            android.widget.Toast.makeText(context, errorText, android.widget.Toast.LENGTH_LONG).show()
            viewModel.onEvent(ScheduleEvent.ConsumeError)
        }
    }
    val infoText = scheduleErrorText(state.infoMessage)
    LaunchedEffect(state.infoMessage) {
        if (state.infoMessage != null) {
            android.widget.Toast.makeText(context, infoText, android.widget.Toast.LENGTH_LONG).show()
            viewModel.onEvent(ScheduleEvent.ConsumeInfo)
        }
    }
    // Exit requested by the VM (Done / back from the first step) → leave the flow.
    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) {
            viewModel.onEvent(ScheduleEvent.ConsumeExit)
            onBack()
        }
    }

    val locationPermission = rememberLocationPermissionState()
    LaunchedEffect(Unit) { locationPermission.requestIfNeeded() }
    LaunchedEffect(locationPermission.isGranted) {
        if (locationPermission.isGranted) viewModel.onEvent(ScheduleEvent.LocateMe)
    }

    BackHandler { viewModel.onEvent(ScheduleEvent.Back) }

    val showRoute = state.step == ScheduleStep.Review || state.step == ScheduleStep.Bidding || state.step == ScheduleStep.Waiting
    // The route map is hidden for the Upcoming hub and the full-screen interactive map picker.
    val showMap = state.step != ScheduleStep.Upcoming && state.step != ScheduleStep.MapPicker

    val defaultLocation = LatLng(24.7136, 46.6753)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 12f)
    }
    // Measured bottom-sheet height → reserve that area (plus the status bar + the floating back chip) so the
    // pickup/dropoff pins and the intercity route always frame ABOVE the sheet, in the visible map area.
    val density = LocalDensity.current
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val statusTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val mapContentPadding = if (showRoute) {
        PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = statusTopDp + 72.dp, // clears the floating back chip
            bottom = with(density) { sheetHeightPx.toDp() } + 16.dp,
        )
    } else {
        PaddingValues(0.dp)
    }
    // Frame pickup + dropoff + route within the padded (visible) region; else centre on a known point.
    LaunchedEffect(showRoute, state.pickup, state.dropoff, state.routePoints, sheetHeightPx) {
        val pickup = state.pickup
        val dropoff = state.dropoff
        if (showRoute && pickup != null && dropoff != null) {
            val pts = buildList {
                add(LatLng(pickup.latitude, pickup.longitude))
                add(LatLng(dropoff.latitude, dropoff.longitude))
                addAll(state.routePoints.map { LatLng(it.latitude, it.longitude) })
            }
            val bounds = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
            // Map contentPadding already insets for the sheet; a small extra keeps pins off the edges.
            runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 48), 700) }
        } else {
            val target = pickup?.let { LatLng(it.latitude, it.longitude) }
                ?: state.myLocation?.let { LatLng(it.latitude, it.longitude) }
            if (target != null) cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 11f)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarrbiTheme.colors.surfaceVariant)) {
        if (showMap) {
            RiderMap(
                cameraPositionState = cameraPositionState,
                showMyLocation = locationPermission.isGranted,
                pickup = if (showRoute) state.pickup else null,
                destination = if (showRoute) state.dropoff else null,
                routePoints = if (showRoute) state.routePoints else emptyList(),
                contentPadding = mapContentPadding,
            )
        }

        // Full-screen offers list takes over the whole screen when bids are present.
        val showOffersList = state.step == ScheduleStep.Bidding && state.bids.isNotEmpty()

        when (state.step) {
            ScheduleStep.DateTime -> Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                ScheduleDateTimeSheet(
                    initialMillis = state.departureAtMillis,
                    onConfirm = {
                        viewModel.onEvent(ScheduleEvent.SetDateTime(it))
                        viewModel.onEvent(ScheduleEvent.ConfirmDateTime)
                    },
                )
            }
            ScheduleStep.OriginCity -> Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                CityPickerSheet(
                    title = stringResource(R.string.schedule_origin_city_title),
                    cities = state.cities,
                    disabledCityId = null,
                    onSelect = { viewModel.onEvent(ScheduleEvent.SelectOriginCity(it)) },
                )
            }
            ScheduleStep.DestCity -> Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                CityPickerSheet(
                    title = stringResource(R.string.schedule_destination_city_title),
                    cities = state.cities,
                    disabledCityId = state.originCity?.id,
                    onSelect = { viewModel.onEvent(ScheduleEvent.SelectDestinationCity(it)) },
                )
            }
            ScheduleStep.OriginPoint -> PointSearchOverlay(
                title = stringResource(R.string.schedule_pickup_title),
                hint = stringResource(R.string.schedule_pickup_hint),
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            ScheduleStep.DestPoint -> PointSearchOverlay(
                title = stringResource(R.string.schedule_dropoff_title),
                hint = stringResource(R.string.schedule_dropoff_hint),
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            ScheduleStep.MapPicker -> MapPickerOverlay(
                start = state.pickup?.let { LatLng(it.latitude, it.longitude) }
                    ?: state.myLocation?.let { LatLng(it.latitude, it.longitude) }
                    ?: defaultLocation,
                showMyLocation = locationPermission.isGranted,
                onConfirm = { lat, lng -> viewModel.onEvent(ScheduleEvent.ConfirmMapLocation(lat, lng)) },
            )
            ScheduleStep.Review -> Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                FindOffersSheet(
                    state = state,
                    onSeats = { viewModel.onEvent(ScheduleEvent.SetSeats(it)) },
                    onSubmit = { viewModel.onEvent(ScheduleEvent.SubmitOffer(it)) },
                    onHeight = { sheetHeightPx = it },
                )
            }
            ScheduleStep.Confirmation -> Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                val arabic = isArabicLocale()
                RideScheduledSheet(
                    departureMillis = state.createdTrip?.scheduledDepartureAtMillis ?: state.departureAtMillis,
                    pickupCity = state.originCity?.displayName(arabic),
                    dropoffCity = state.destinationCity?.displayName(arabic),
                    onUpcoming = { viewModel.onEvent(ScheduleEvent.GoToUpcoming) },
                    onDone = { viewModel.onEvent(ScheduleEvent.Finish) },
                )
            }
            ScheduleStep.Upcoming -> Column(modifier = Modifier.fillMaxSize().background(DarrbiTheme.colors.surface).statusBarsPadding()) {
                ScheduleHeader(title = stringResource(R.string.schedule_upcoming_title), onBack = { viewModel.onEvent(ScheduleEvent.Back) })
                Box(modifier = Modifier.fillMaxSize()) {
                    UpcomingRidesContent(
                        state = state,
                        onOpen = { viewModel.onEvent(ScheduleEvent.OpenUpcoming(it)) },
                        onCancel = { viewModel.onEvent(ScheduleEvent.CancelTrip(it)) },
                        onCreateNew = { viewModel.onEvent(ScheduleEvent.CreateNew) },
                    )
                    if (state.isLoadingUpcoming) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DarrbiTheme.colors.primary)
                        }
                    }
                }
            }
            ScheduleStep.Bidding -> {
                // "Cancel Request" cancels the open scheduled trip (free before the window); the floating
                // back chip just leaves the screen with the request still open.
                val cancelRequest: () -> Unit = { state.biddingTripId?.let { viewModel.onEvent(ScheduleEvent.CancelTrip(it)) } }
                if (showOffersList) {
                    SelectOffersList(
                        bids = state.bids,
                        inFlight = state.isBidActionInFlight,
                        onAccept = { viewModel.onEvent(ScheduleEvent.SelectBid(it)) },
                        onDecline = { viewModel.onEvent(ScheduleEvent.RejectBid(it)) },
                        onCancel = cancelRequest,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        ScheduledBiddingOverlay(
                            state = state,
                            onRaise = { viewModel.onEvent(ScheduleEvent.RaiseOffer(it)) },
                            onCancel = cancelRequest,
                            onHeight = { sheetHeightPx = it },
                        )
                    }
                }
            }
            ScheduleStep.Waiting -> Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                val bid = state.matchedBid
                ScheduledWaitingSheet(
                    state = state,
                    onChat = {
                        bid?.let { onChat(it.driverId, it.driverName ?: "", it.driverImageUrl) }
                    },
                    onCall = {
                        bid?.driverMobile?.takeIf { it.isNotBlank() }?.let { dialNumber(context, it) }
                    },
                    onCancel = { state.matchedTripId?.let { viewModel.onEvent(ScheduleEvent.CancelTrip(it)) } },
                    onHeight = { sheetHeightPx = it },
                )
            }
        }

        // Floating back button over the map for sheet-based steps + the map picker (offers list + upcoming have their own).
        if ((showMap || state.step == ScheduleStep.MapPicker) && !showOffersList) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp).size(44.dp)
                    .clip(CircleShape).background(DarrbiTheme.colors.surface)
                    .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                    .clickable { viewModel.onEvent(ScheduleEvent.Back) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_back), tint = DarrbiTheme.colors.onSurface)
            }
        }

        // Blocking spinner while creating the request.
        if (state.isCreating || state.isCancelling) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarrbiTheme.colors.primary)
            }
        }
    }
}

/** Pickup/dropoff point search (reuses the rider search atoms), driving the schedule flow. */
@Composable
private fun PointSearchOverlay(
    title: String,
    hint: String,
    state: ScheduleUiState,
    onEvent: (ScheduleEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // imePadding lifts the whole sheet above the keyboard so the search field + suggestions stay visible.
    DarrbiCard(modifier = modifier.imePadding(), navigationBarPadding = true) {
        Text(title, style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
        Spacer(Modifier.height(12.dp))
        SearchField(value = state.query, hint = hint, onValueChange = { onEvent(ScheduleEvent.QueryChanged(it)) })
        Spacer(Modifier.height(8.dp))
        ChooseOnMapRow(onClick = { onEvent(ScheduleEvent.OpenMapPicker) })
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            CurrentLocationRow(onClick = { onEvent(ScheduleEvent.UseCurrentLocation) })
            state.suggestions.forEach { suggestion ->
                SuggestionRow(suggestion = suggestion, onClick = { onEvent(ScheduleEvent.SelectSuggestion(suggestion)) })
            }
        }
    }
}

/**
 * Full-screen interactive map picker for a pickup/dropoff point: a fixed centre pin marks the chosen spot
 * while the map pans under it; "Confirm Location" reverse-geocodes the centre. Mirrors the rider-home picker.
 */
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
    Image(
        painter = painterResource(R.drawable.marker_1),
        contentDescription = null,
        modifier = Modifier.align(Alignment.Center).offset(y = (-24).dp).size(48.dp),
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

/** Opens the phone dialer pre-filled with [number]. */
private fun dialNumber(context: android.content.Context, number: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_DIAL,
        android.net.Uri.parse("tel:$number"),
    )
    context.startActivity(intent)
}
