package com.mytm.darrbi.presentation.rider

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
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
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.mytm.darrbi.R
import com.mytm.darrbi.domain.model.AcceptedTrip
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.repository.NearbyDriver
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.presentation.components.CancelReasonsSheet
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.presentation.common.LocationPermissionDeniedDialog
import com.mytm.darrbi.presentation.common.openAppSettings
import com.mytm.darrbi.presentation.common.rememberLocationPermissionState
import androidx.core.graphics.scale

/**
 * Rider ride-booking flow (location selection): home map → destination search → pickup search →
 * confirm pickup → ride selection. Search is powered by the Google Places SDK; location by [MapService].
 * On entry the screen asks for location permission and, once granted, centres the map on the device.
 */
@Composable
fun RiderFlowScreen(
    onProfile: () -> Unit = {},
    onChat: (com.mytm.darrbi.domain.model.AcceptedTrip) -> Unit = {},
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
    // "New drop-off location changed successfully." (shown once after a confirmed drop change).
    val dropChangedMsg = stringResource(R.string.rider_drop_changed)
    LaunchedEffect(state.dropChangeSucceeded) {
        if (state.dropChangeSucceeded) {
            android.widget.Toast.makeText(errorContext, dropChangedMsg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.onEvent(RiderBookingEvent.ConsumeInfo)
        }
    }
    // "The captain cancelled the ride." (shown once when the driver cancels → back to ride selection).
    val driverCancelledMsg = stringResource(R.string.rider_driver_cancelled)
    LaunchedEffect(state.cancelledByDriver) {
        if (state.cancelledByDriver) {
            android.widget.Toast.makeText(errorContext, driverCancelledMsg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.onEvent(RiderBookingEvent.ConsumeInfo)
        }
    }
    // V2 bidding notices (no bids yet / window timed out / payment hold failed at selection).
    val noBidsMsg = stringResource(R.string.rider_bid_no_bids)
    val bidTimeoutMsg = stringResource(R.string.rider_bid_timeout)
    val paymentHoldMsg = stringResource(R.string.rider_bid_payment_hold)
    LaunchedEffect(state.bidNotice) {
        state.bidNotice?.let {
            val msg = when (it) {
                "TIMEOUT" -> bidTimeoutMsg
                "PAYMENT_HOLD" -> paymentHoldMsg
                else -> noBidsMsg
            }
            android.widget.Toast.makeText(errorContext, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.onEvent(RiderBookingEvent.ConsumeBidNotice)
        }
    }

    // Ask for location permission on arrival; once granted, centre the map on the current location.
    val locationPermission = rememberLocationPermissionState()
    LaunchedEffect(Unit) { locationPermission.requestIfNeeded() }
    LaunchedEffect(locationPermission.isGranted) {
        if (locationPermission.isGranted) viewModel.onEvent(RiderBookingEvent.LocateMe)
    }

    BackHandler(enabled = state.step != RiderStep.Home) { viewModel.onEvent(RiderBookingEvent.Back) }
    // While the Help sheet is open, system-back closes it (takes precedence over the step handler).
    BackHandler(enabled = state.showHelp) { viewModel.onEvent(RiderBookingEvent.CloseHelp) }
    // While the cancellation sheet is open, system-back closes it instead of leaving the trip.
    BackHandler(enabled = state.showCancelSheet) { viewModel.onEvent(RiderBookingEvent.DismissCancelSheet) }

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
    val onWay = state.step == RiderStep.DriverOnWay
    // Arrived / in-trip / completed: the map shows the trip itself (pickup → destination).
    val tripRoute = state.step == RiderStep.DriverArrived ||
        state.step == RiderStep.TripStarted ||
        state.step == RiderStep.TripCompleted
    // Change-drop: search shows the current route; confirm previews pickup → the new drop.
    val changingDrop = state.step == RiderStep.ChangeDropSearch || state.step == RiderStep.ChangeDropConfirm
    // Any active-ride view (suppresses the scattered nearby-captain markers).
    val activeTrip = onWay || tripRoute || changingDrop
    val showRoute = state.step == RiderStep.ConfirmPickup ||
        state.step == RiderStep.SelectRide ||
        state.step == RiderStep.ProposeFare ||
        state.step == RiderStep.Bidding ||
        state.step == RiderStep.Searching ||
        onWay || tripRoute || changingDrop
    // The destination + route the map should frame for the current step.
    val mapDestination: PlaceLocation? = when {
        onWay -> null
        state.step == RiderStep.ChangeDropConfirm -> state.changeDropDestination
        showRoute -> state.destination
        else -> null
    }
    val mapRoutePoints: List<LatLngPoint> = when {
        onWay -> state.driverRoutePoints
        state.step == RiderStep.ChangeDropConfirm -> emptyList() // straight-line preview to the new drop
        showRoute -> state.routePoints
        else -> emptyList()
    }
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
    LaunchedEffect(
        showRoute, onWay, state.pickup, mapDestination,
        mapRoutePoints, state.driverRoutePoints, routeSheetHeightPx,
    ) {
        val pickup = state.pickup
        // On the way the route runs pickup → captain; otherwise pickup → the framed destination. For the
        // on-the-way case fit to the (static) route's far end so the camera frames the trip once and does
        // NOT chase the captain's moving marker on every location update.
        val routePts = if (onWay) state.driverRoutePoints else mapRoutePoints
        val end = if (onWay) routePts.lastOrNull() else mapDestination?.let { LatLngPoint(it.latitude, it.longitude) }
        if (showRoute && pickup != null && end != null) {
            val pts = buildList {
                add(LatLng(pickup.latitude, pickup.longitude))
                add(LatLng(end.latitude, end.longitude))
                addAll(routePts.map { LatLng(it.latitude, it.longitude) })
            }
            val bounds = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
            runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_BOUNDS_PADDING_PX), 700) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarrbiTheme.colors.surfaceVariant)) {
        // Home is a full-screen dashboard (no map); the map picker draws its own interactive map; every
        // other step uses the static background map.
        if (state.step != RiderStep.MapPicker && state.step != RiderStep.Home) {
            RiderMap(
                cameraPositionState = cameraPositionState,
                showMyLocation = locationPermission.isGranted,
                pickup = if (showRoute) state.pickup else null,
                destination = mapDestination,
                routePoints = mapRoutePoints,
                driverLocation = if (onWay) state.driverLocation else null,
                // In-trip: the live captain car rides the pickup→destination route (the destination stays a pin).
                liveCarLocation = if (state.step == RiderStep.TripStarted) state.driverLocation else null,
                nearbyDrivers = if (activeTrip) emptyList() else state.nearbyDrivers,
                contentPadding = mapContentPadding,
            )
        }
        when (state.step) {
            RiderStep.Home -> RiderHomeDashboard(
                state = state,
                onEvent = viewModel::onEvent,
                onProfile = onProfile,
                modifier = Modifier.fillMaxSize(),
            )
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
            RiderStep.ProposeFare -> ProposeFareOverlay(
                state = state,
                onSubmit = { fare -> viewModel.onEvent(RiderBookingEvent.SubmitOffer(fare)) },
                onHeight = { routeSheetHeightPx = it },
            )
            RiderStep.Bidding -> if (state.bids.isEmpty()) {
                BiddingOverlay(
                    state = state,
                    onRaise = { fare -> viewModel.onEvent(RiderBookingEvent.RaiseOffer(fare)) },
                    onCancel = { viewModel.onEvent(RiderBookingEvent.CancelBidding) },
                    onHeight = { routeSheetHeightPx = it },
                )
            } else {
                // Offers arriving → full-screen "Select Offers" list (Accept/Decline per driver).
                SelectOffersList(
                    bids = state.bids,
                    inFlight = state.isBidActionInFlight,
                    onAccept = { bidId -> viewModel.onEvent(RiderBookingEvent.SelectBid(bidId)) },
                    onDecline = { bidId -> viewModel.onEvent(RiderBookingEvent.RejectBid(bidId)) },
                    onCancel = { viewModel.onEvent(RiderBookingEvent.CancelBidding) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            RiderStep.Searching -> SearchingOverlay(
                noCaptainFound = state.noCaptainFound,
                onTryAgain = { viewModel.onEvent(RiderBookingEvent.TryAgainSearch) },
                onCancel = { viewModel.onEvent(RiderBookingEvent.CancelRide) },
                onHeight = { routeSheetHeightPx = it },
            )
            RiderStep.DriverOnWay -> state.acceptedTrip?.let { trip ->
                when {
                    state.rideCancelled -> RideCancelledOverlay(onDone = { viewModel.onEvent(RiderBookingEvent.CancelDone) })
                    state.cancelSubmitting -> CancellingOverlay()
                    state.showCancelSheet -> CancelReasonsSheet(
                        reasons = state.cancelReasons,
                        selectedId = state.selectedCancelReasonId,
                        isLoading = state.cancelReasonsLoading,
                        isSubmitting = state.cancelSubmitting,
                        confirmText = stringResource(R.string.cancel_ride_confirm_fee, formatFare(trip.cancellationFee)),
                        onSelect = { viewModel.onEvent(RiderBookingEvent.SelectCancelReason(it)) },
                        onSubmit = { viewModel.onEvent(RiderBookingEvent.SubmitCancel) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    else -> DriverOnWayOverlay(
                        trip = trip,
                        onCall = { trip.driverMobile?.let { dialNumber(errorContext, it) } },
                        onChat = { onChat(trip) },
                        onCancel = { viewModel.onEvent(RiderBookingEvent.OpenCancelSheet) },
                        onHeight = { routeSheetHeightPx = it },
                    )
                }
            }
            RiderStep.DriverArrived -> state.acceptedTrip?.let { trip ->
                DriverArrivedOverlay(
                    arrivedAtMillis = state.arrivedAtMillis,
                    onImComing = { viewModel.onEvent(RiderBookingEvent.ImComing) },
                    onHeight = { routeSheetHeightPx = it },
                )
            }
            RiderStep.TripStarted -> state.acceptedTrip?.let { trip ->
                if (state.showHelp) {
                    HelpOverlay(
                        onBack = { viewModel.onEvent(RiderBookingEvent.CloseHelp) },
                        onCallCenter = { dialNumber(errorContext, CUSTOMER_CARE_NUMBER) },
                        onShare = { shareRideDetails(errorContext, trip, state.pickup, state.destination) },
                        onCallPolice = { dialNumber(errorContext, POLICE_NUMBER) },
                        onHeight = { routeSheetHeightPx = it },
                    )
                } else {
                    TripStartedOverlay(
                        trip = trip,
                        pickupAddress = state.pickup?.address?.takeIf { it.isNotBlank() } ?: state.pickup?.name.orEmpty(),
                        destinationAddress = state.destination?.address?.takeIf { it.isNotBlank() } ?: state.destination?.name.orEmpty(),
                        onChange = { viewModel.onEvent(RiderBookingEvent.OpenChangeDrop) },
                        onHelp = { viewModel.onEvent(RiderBookingEvent.OpenHelp) },
                        onHeight = { routeSheetHeightPx = it },
                    )
                }
            }
            RiderStep.ChangeDropSearch -> SearchOverlay(
                title = stringResource(R.string.rider_change_dropoff),
                hint = stringResource(R.string.rider_change_dropoff),
                state = state,
                onEvent = viewModel::onEvent,
                showCurrentLocation = false,
                onRecenter = recenter,
            )
            RiderStep.ChangeDropConfirm -> state.acceptedTrip?.let { trip ->
                ChangeDropConfirmOverlay(
                    pickupAddress = state.pickup?.address?.takeIf { it.isNotBlank() } ?: state.pickup?.name.orEmpty(),
                    destinationAddress = state.changeDropDestination?.address?.takeIf { it.isNotBlank() }
                        ?: state.changeDropDestination?.name.orEmpty(),
                    newFare = state.changeDropQuote?.newFare,
                    remaining = state.changeDropRemaining,
                    balance = state.balance,
                    arrivalMinutes = state.changeDropQuote?.arrivalMinutes,
                    isConfirming = state.isChangingDrop,
                    onConfirm = { viewModel.onEvent(RiderBookingEvent.ConfirmChangeDrop) },
                    onHeight = { routeSheetHeightPx = it },
                )
            }
            RiderStep.TripCompleted -> state.acceptedTrip?.let { trip ->
                RatingOverlay(
                    driverName = trip.driverName,
                    driverImageUrl = trip.driverImageUrl,
                    rating = state.rating,
                    totalPayment = trip.originalFare,
                    balance = state.balance,
                    loyaltyPoints = trip.loyaltyPoints,
                    isSubmitting = state.isSubmittingRating,
                    onRate = { stars -> viewModel.onEvent(RiderBookingEvent.SelectRating(stars)) },
                    onDownloadInvoice = { viewModel.invoiceUrl()?.let { openUrl(errorContext, it) } },
                    onSubmit = { viewModel.onEvent(RiderBookingEvent.SubmitRating) },
                    onHeight = { routeSheetHeightPx = it },
                )
            }
        }
        // Profile avatar, top-end — persistent over the map steps (Home has its own header avatar; the
        // full-screen map picker hides it). The RIDER/CAPTAIN switch now lives in the profile screen.
        if (state.step != RiderStep.MapPicker && state.step != RiderStep.Home) {
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

/** In-trip Help: customer-care line and the Saudi emergency police number. */
private const val CUSTOMER_CARE_NUMBER = "+966500000000"
private const val POLICE_NUMBER = "999"

@Composable
private fun RiderMap(
    cameraPositionState: CameraPositionState,
    showMyLocation: Boolean,
    pickup: PlaceLocation? = null,
    destination: PlaceLocation? = null,
    routePoints: List<LatLngPoint> = emptyList(),
    driverLocation: LatLngPoint? = null,
    /** The live captain car shown ALONGSIDE the destination pin (in-trip), independent of the route end. */
    liveCarLocation: LatLngPoint? = null,
    nearbyDrivers: List<NearbyDriver> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val routeColor = DarrbiTheme.colors.primary
    val pickupLatLng = pickup?.let { LatLng(it.latitude, it.longitude) }
    // On the way the route ends at the captain (car marker); otherwise at the destination (pin).
    val endIsDriver = driverLocation != null
    val endLatLng = driverLocation?.let { LatLng(it.latitude, it.longitude) }
        ?: destination?.let { LatLng(it.latitude, it.longitude) }
    val destinationLatLng = destination?.let { LatLng(it.latitude, it.longitude) }
    // Route polyline is STABLE: it uses the fetched route points (drawn once) — or a straight
    // pickup→destination line for the non-driver case — and never depends on the captain's live position,
    // so it is not recreated/redrawn as the captain moves.
    val fullRoute = remember(pickupLatLng, destinationLatLng, routePoints, endIsDriver) {
        when {
            routePoints.size >= 2 -> routePoints.map { LatLng(it.latitude, it.longitude) }
            !endIsDriver && pickupLatLng != null && destinationLatLng != null ->
                listOf(pickupLatLng, destinationLatLng)
            else -> emptyList()
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
        // Nearby captains from the `find-drivers` socket event.
        val carIcon = rememberCarMarkerIcon()
        nearbyDrivers.forEach { driver ->
            val pos = LatLng(driver.latitude, driver.longitude)
            Marker(
                state = rememberMarkerState(key = "drv_${driver.id}_${driver.latitude},${driver.longitude}", position = pos),
                icon = carIcon,
                anchor = Offset(0.5f, 0.5f),
                flat = true,
            )
        }
        // Anchor at the icon centre (default is bottom-centre) so its centre sits on the location.
        val markerIcon = rememberMarkerIcon()
        val markerAnchor = Offset(0.5f, 0.5f)
        if (drawnRoute.size >= 2) {
            Polyline(points = drawnRoute, color = routeColor, width = 6f)
        }
        // Pickup pin.
        if (pickupLatLng != null && (endIsDriver || drawnRoute.size >= 2)) {
            Marker(
                state = rememberMarkerState(key = "pickup_${pickupLatLng.latitude},${pickupLatLng.longitude}", position = pickupLatLng),
                icon = markerIcon,
                anchor = markerAnchor,
            )
        }
        if (endIsDriver && endLatLng != null) {
            // On-the-way: the captain's car IS the route end — a single animated marker.
            AnimatedCarMarker(target = endLatLng, icon = carIcon, key = "driver_car")
        } else if (endLatLng != null && drawnRoute.size >= 2) {
            // Destination pin (not on the way).
            Marker(
                state = rememberMarkerState(key = "end_${endLatLng.latitude},${endLatLng.longitude}", position = endLatLng),
                icon = markerIcon,
                anchor = markerAnchor,
            )
        }
        // In-trip: the captain's car glides along the route while the destination pin stays put.
        liveCarLocation?.let { car ->
            AnimatedCarMarker(target = LatLng(car.latitude, car.longitude), icon = carIcon, key = "trip_car")
        }
    }
}

/**
 * A single stable car marker that glides from its previous position to [target] (over
 * [DRIVER_MARKER_ANIM_MS]) and rotates to the travel bearing — only the marker moves, never the polyline.
 */
@Composable
@com.google.maps.android.compose.GoogleMapComposable
internal fun AnimatedCarMarker(target: LatLng, icon: BitmapDescriptor?, key: String) {
    // The position the marker is currently drawn at — animated toward each new [target]. We feed this into
    // rememberUpdatedMarkerState (the maps-compose 6.x pattern) instead of mutating MarkerState.position
    // directly, which no longer moves the marker reliably in 6.x.
    var current by remember(key) { mutableStateOf(target) }
    var bearing by remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(target) {
        val start = current
        if (start.latitude != target.latitude || start.longitude != target.longitude) {
            val computed = bearingBetween(start, target)
            if (!computed.isNaN()) bearing = computed
            Animatable(0f).animateTo(1f, tween(durationMillis = DRIVER_MARKER_ANIM_MS, easing = LinearEasing)) {
                current = lerpLatLng(start, target, value)
            }
            current = target
        }
    }
    val markerState = rememberUpdatedMarkerState(position = current)
    Marker(state = markerState, icon = icon, anchor = Offset(0.5f, 0.5f), flat = true, rotation = bearing)
}

/** Duration of the captain-marker glide between two live locations. */
private const val DRIVER_MARKER_ANIM_MS = 1500

/** Initial bearing (degrees, 0=N clockwise) from [start] to [end] — used to rotate the car marker. */
private fun bearingBetween(start: LatLng, end: LatLng): Float {
    val lat1 = Math.toRadians(start.latitude)
    val lat2 = Math.toRadians(end.latitude)
    val dLng = Math.toRadians(end.longitude - start.longitude)
    val y = kotlin.math.sin(dLng) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
        kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLng)
    val deg = Math.toDegrees(kotlin.math.atan2(y, x))
    return ((deg + 360.0) % 360.0).toFloat()
}

/** Linearly interpolates between two coordinates, taking the short way across the 180° meridian. */
private fun lerpLatLng(a: LatLng, b: LatLng, t: Float): LatLng {
    val lat = a.latitude + (b.latitude - a.latitude) * t
    var dLng = b.longitude - a.longitude
    if (kotlin.math.abs(dLng) > 180) dLng -= Math.signum(dLng) * 360
    val lng = a.longitude + dLng * t
    return LatLng(lat, lng)
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
internal fun rememberMarkerIcon(): BitmapDescriptor? {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember {
        runCatching {
            val heightPx = with(density) { 46.dp.roundToPx() }
            val widthPx = (heightPx * 156f / 184f).toInt()
            val source = (ContextCompat.getDrawable(context, R.drawable.marker_1) as BitmapDrawable).bitmap
            BitmapDescriptorFactory.fromBitmap(source.scale(widthPx, heightPx))
        }.getOrNull()
    }
}

/** Scaled `icon_car2` top-down car used for nearby-driver markers (aspect ratio preserved). */
@Composable
internal fun rememberCarMarkerIcon(): BitmapDescriptor? {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember {
        runCatching {
            val source = (ContextCompat.getDrawable(context, R.drawable.icon_car) as BitmapDrawable).bitmap
            val heightPx = with(density) { 40.dp.roundToPx() }
            val widthPx = (heightPx * source.width.toFloat() / source.height).toInt()
            BitmapDescriptorFactory.fromBitmap(source.scale(widthPx, heightPx))
        }.getOrNull()
    }
}

/**
 * Full-screen rider home dashboard (no map), matching the "HomePage Simple" reference: header (logo +
 * current location + profile), a time-based greeting, an API-driven service-category grid where a lone
 * last tile spans full width, a promo banner, the "Where to and for how much?" search bar, and recent
 * quick-picks. Selecting a category remembers it; the search bar continues with the default category.
 */
@Composable
private fun RiderHomeDashboard(
    state: RiderBookingUiState,
    onEvent: (RiderBookingEvent) -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(DarrbiTheme.colors.surface)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(12.dp))
        HomeHeader(
            locationText = state.myLocation?.address?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.rider_your_location),
            userImageUrl = state.userImageUrl,
            onLocation = { onEvent(RiderBookingEvent.OpenDestinationSearch) },
            onProfile = onProfile,
        )
        Spacer(Modifier.height(24.dp))
        HomeGreeting(userName = state.userName)
        Spacer(Modifier.height(20.dp))
        CategoriesGrid(
            categories = state.categories,
            isLoading = state.isLoadingCategories,
            nearbyCount = state.nearbyDrivers.size,
            onCategory = { onEvent(RiderBookingEvent.OpenCategory(it)) },
            onRetry = { onEvent(RiderBookingEvent.RetryCategories) },
        )
        Spacer(Modifier.height(20.dp))
        HomePromoBanner()
        Spacer(Modifier.height(20.dp))
        HomeSearchBar(onClick = { onEvent(RiderBookingEvent.OpenDestinationSearch) })
        when {
            state.recentLocations.isNotEmpty() -> {
                Spacer(Modifier.height(8.dp))
                RecentLocations(
                    locations = state.recentLocations,
                    onSelect = { onEvent(RiderBookingEvent.SelectRecent(it)) },
                )
            }
            state.isLoadingRecents -> {
                Spacer(Modifier.height(8.dp))
                RecentsSkeleton()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** DERRBI wordmark + "Your location ›" stacked on the left, profile avatar on the right (per reference). */
@Composable
private fun HomeHeader(
    locationText: String,
    userImageUrl: String?,
    onLocation: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onLocation)) {
            Image(
                painter = painterResource(R.drawable.icon_darrbi_black),
                contentDescription = null,
                modifier = Modifier.height(22.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = locationText,
                    style = DarrbiTheme.typography.caption,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = DarrbiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AsyncImage(
            model = userImageUrl,
            contentDescription = stringResource(R.string.cd_profile),
            placeholder = painterResource(R.drawable.user_placeholder),
            error = painterResource(R.drawable.user_placeholder),
            fallback = painterResource(R.drawable.user_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                .clickable(onClick = onProfile),
        )
    }
}

/** Small greeting line, then the bold "What do you want today?" headline (per reference). */
@Composable
private fun HomeGreeting(userName: String?) {
    val hour = remember { LocalTime.now().hour }
    val greetingRes = when {
        hour < 12 -> R.string.rider_greeting_morning
        hour < 17 -> R.string.rider_greeting_afternoon
        else -> R.string.rider_greeting_evening
    }
    val name = userName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.rider_default_user)
    Text(
        text = stringResource(greetingRes, name),
        style = DarrbiTheme.typography.body,
        color = DarrbiTheme.colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(R.string.rider_what_today),
        style = DarrbiTheme.typography.titleLarge,
        color = DarrbiTheme.colors.onSurface,
    )
}

/**
 * 2-column service grid with a pastel tint per tile (cycled by index). Categories are laid out in pairs;
 * a lone last category spans the full width (matches the reference, e.g. 5 categories → 5th is full width).
 */
@Composable
private fun CategoriesGrid(
    categories: List<com.mytm.darrbi.domain.model.RideCategory>,
    isLoading: Boolean,
    nearbyCount: Int,
    onCategory: (com.mytm.darrbi.domain.model.RideCategory) -> Unit,
    onRetry: () -> Unit,
) {
    val tints = DarrbiTheme.colors.categoryTints
    fun tint(i: Int) = tints[i % tints.size]
    when {
        isLoading && categories.isEmpty() -> CategoriesSkeleton()
        categories.isEmpty() -> Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onRetry),
            shape = RoundedCornerShape(16.dp),
            color = DarrbiTheme.colors.surfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.rider_categories_retry),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.primary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.chunked(2).forEachIndexed { rowIndex, row ->
                if (row.size == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryTile(row[0], tint(rowIndex * 2), nearbyCount, false, onCategory, Modifier.weight(1f))
                        CategoryTile(row[1], tint(rowIndex * 2 + 1), nearbyCount, false, onCategory, Modifier.weight(1f))
                    }
                } else {
                    CategoryTile(row[0], tint(rowIndex * 2), nearbyCount, true, onCategory, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: com.mytm.darrbi.domain.model.RideCategory,
    tint: Color,
    nearbyCount: Int,
    fullWidth: Boolean,
    onCategory: (com.mytm.darrbi.domain.model.RideCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTaxi = category.key.contains("taxi")
    val title = if (isArabic()) category.nameArabic?.takeIf { it.isNotBlank() } ?: category.name else category.name
    val subtitle = categorySubtitle(category)
    Surface(
        modifier = modifier.height(if (fullWidth) 104.dp else 150.dp).clickable { onCategory(category) },
        shape = RoundedCornerShape(16.dp),
        color = tint,
    ) {
        if (fullWidth) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryImage(category.imageUrl, Modifier.size(60.dp))
                Spacer(Modifier.width(16.dp))
                CategoryText(title, subtitle)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    CategoryImage(category.imageUrl, Modifier.size(72.dp))
                    Spacer(Modifier.width(10.dp))
                    CategoryText(title, subtitle)
                }
                if (isTaxi && nearbyCount > 0) NearbyCaptainsBadge(nearbyCount)
            }
        }
    }
}

/**
 * The category subtitle: the API description when present, else a localized tagline matched by service
 * type (the live API returns no description). Unknown services get no subtitle.
 */
@Composable
private fun categorySubtitle(category: com.mytm.darrbi.domain.model.RideCategory): String? {
    category.subtitle?.takeIf { it.isNotBlank() }?.let { return it }
    val res = when {
        category.key.contains("taxi") -> R.string.cat_sub_taxi
        category.key.contains("rental") -> R.string.cat_sub_rental
        category.key.contains("cargo") -> R.string.cat_sub_cargo
        category.key.contains("delivery") -> R.string.cat_sub_delivery
        category.key.contains("schedul") -> R.string.cat_sub_scheduled
        else -> return null
    }
    return stringResource(res)
}

@Composable
private fun CategoryImage(imageUrl: String?, modifier: Modifier) {
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        placeholder = painterResource(R.drawable.placeholder_select_car),
        error = painterResource(R.drawable.placeholder_select_car),
        fallback = painterResource(R.drawable.placeholder_select_car),
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CategoryText(title: String, subtitle: String?) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title,
            style = DarrbiTheme.typography.title,
            color = DarrbiTheme.colors.onSurface,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = it,
                style = DarrbiTheme.typography.label,
                color = DarrbiTheme.colors.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** Solid-green pill with white text, shown on the taxi tile (per reference). */
@Composable
private fun NearbyCaptainsBadge(count: Int) {
    Surface(shape = RoundedCornerShape(50), color = DarrbiTheme.colors.primary) {
        Text(
            text = stringResource(R.string.rider_captains_near, count),
            style = DarrbiTheme.typography.caption,
            color = DarrbiTheme.colors.onPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** Promo banner image (from drawable) with a static 4-dot carousel indicator (3rd active), per reference. */
@Composable
private fun HomePromoBanner() {
    Column {
        Image(
            painter = painterResource(R.drawable.banner_1),
            contentDescription = stringResource(R.string.rider_home_promo_title),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.FillWidth,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(PROMO_DOT_COUNT) { index ->
                val active = index == PROMO_ACTIVE_DOT
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) DarrbiTheme.colors.primary else DarrbiTheme.colors.outline),
                )
            }
        }
    }
}

@Composable
private fun HomeSearchBar(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surfaceVariant,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.rider_where_to_fare),
                style = DarrbiTheme.typography.body,
                color = DarrbiTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** Plain recent-address rows (clock icon + label + address) directly on the page, per reference. */
@Composable
private fun RecentLocations(
    locations: List<com.mytm.darrbi.domain.model.RecentLocation>,
    onSelect: (com.mytm.darrbi.domain.model.RecentLocation) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        locations.forEach { recent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(recent) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_recent_clock),
                    contentDescription = null,
                    tint = DarrbiTheme.colors.onSurface,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val label = recent.label
                    if (label != null) {
                        Text(
                            text = label,
                            style = DarrbiTheme.typography.bodyMedium,
                            color = DarrbiTheme.colors.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            text = recent.place.address,
                            style = DarrbiTheme.typography.caption,
                            color = DarrbiTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = recent.place.address,
                            style = DarrbiTheme.typography.bodyMedium,
                            color = DarrbiTheme.colors.onSurface,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---- Shimmer / skeleton loading (categories + recents) ----

/** Animated shimmer brush: a light highlight band sweeping across a muted base. */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -SHIMMER_BAND,
        targetValue = SHIMMER_TRAVEL,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "shimmerX",
    )
    val base = DarrbiTheme.colors.surfaceVariant
    val highlight = DarrbiTheme.colors.outline
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + SHIMMER_BAND, 0f),
    )
}

/** A rounded shimmer placeholder block. */
@Composable
private fun ShimmerBox(
    brush: Brush,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
) {
    Box(modifier.clip(shape).background(brush))
}

/** Skeleton mirroring the 2×2 category grid while it loads. */
@Composable
private fun CategoriesSkeleton() {
    val brush = rememberShimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonCategoryTile(brush, Modifier.weight(1f))
                SkeletonCategoryTile(brush, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkeletonCategoryTile(brush: Brush, modifier: Modifier) {
    Surface(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(brush, Modifier.size(60.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(brush, Modifier.fillMaxWidth(0.7f).height(14.dp))
                ShimmerBox(brush, Modifier.fillMaxWidth(0.45f).height(12.dp))
            }
        }
    }
}

/** Skeleton mirroring the recent-address rows while they load. */
@Composable
private fun RecentsSkeleton() {
    val brush = rememberShimmerBrush()
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(brush, Modifier.size(24.dp), CircleShape)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(brush, Modifier.fillMaxWidth(0.4f).height(13.dp))
                    ShimmerBox(brush, Modifier.fillMaxWidth(0.75f).height(11.dp))
                }
            }
        }
    }
}

/** Pixel travel of the shimmer highlight band (covers wide tiles across common densities). */
private const val SHIMMER_BAND = 280f
private const val SHIMMER_TRAVEL = 1200f

/** Static promo carousel indicator (the banner image is a single slide for now). */
private const val PROMO_DOT_COUNT = 4
private const val PROMO_ACTIVE_DOT = 2

/** True when the active app locale is Arabic — used to prefer the Arabic category name. */
@Composable
private fun isArabic(): Boolean =
    LocalContext.current.resources.configuration.locales[0].language == "ar"

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SearchOverlay(
    title: String?,
    hint: String,
    state: RiderBookingUiState,
    onEvent: (RiderBookingEvent) -> Unit,
    showCurrentLocation: Boolean,
    onRecenter: () -> Unit,
) {
    // Recenter button, top-end — sits BELOW the persistent profile avatar so they don't overlap.
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 72.dp, end = 16.dp)
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

// ---------------------------------------------------------------------------------------------
// V2 bidding: propose-fare + live-bids overlays (over the pickup→destination route map).
// ---------------------------------------------------------------------------------------------

/** Propose-fare sheet: a stepper around the recommended fare, bounded to the allowed range. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ProposeFareOverlay(
    state: RiderBookingUiState,
    onSubmit: (Double) -> Unit,
    onHeight: (Int) -> Unit,
) {
    val recommended = state.selectedCab?.fare ?: state.offeredFare ?: 0.0
    val minFare = recommended * V2_MIN_FACTOR
    val maxFare = recommended * V2_MAX_FACTOR
    val step = if (recommended >= 50.0) 5f else 1f
    var offer by remember(recommended) { mutableFloatStateOf((state.offeredFare ?: recommended).toFloat()) }
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().wrapContentHeight().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            RouteHeader(
                pickup = state.pickup?.address?.takeIf { it.isNotBlank() } ?: stringResource(R.string.rider_current_location),
                destination = state.destination?.address.orEmpty(),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.rider_set_fare), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.rider_recommended_fare, formatFare(recommended)),
                style = DarrbiTheme.typography.label,
                color = DarrbiTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                StepperButton("−", enabled = offer > minFare) { offer = (offer - step).coerceAtLeast(minFare.toFloat()) }
                Text(
                    text = stringResource(R.string.rider_fare_sar, formatFare(offer.toDouble())),
                    style = DarrbiTheme.typography.titleLarge.copy(fontSize = 26.sp),
                    color = DarrbiTheme.colors.onSurface,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
                StepperButton("+", enabled = offer < maxFare) { offer = (offer + step).coerceAtMost(maxFare.toFloat()) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.rider_fare_range, formatFare(minFare), formatFare(maxFare)),
                style = DarrbiTheme.typography.caption,
                color = DarrbiTheme.colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.rider_request_ride),
                onClick = { onSubmit(offer.toDouble()) },
                enabled = !state.isCreatingBidTrip,
            )
        }
    }
    if (state.isCreatingBidTrip) {
        CircularProgressIndicator(color = DarrbiTheme.colors.primary, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) DarrbiTheme.colors.surfaceVariant else DarrbiTheme.colors.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
        }
    }
}

/**
 * "Searching For Drivers Nearby" sheet (per the reference): the rider's offer with a +/- stepper and a
 * Change Price (raise) button, the payment row, the pickup→drop route, and Cancel Request. Competing bids
 * (when they arrive) appear as a selectable list above the payment row.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.BiddingOverlay(
    state: RiderBookingUiState,
    onRaise: (Double) -> Unit,
    onCancel: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    val offered = state.offeredFare ?: state.bidTrip?.fareRange?.riderOfferedFare ?: 0.0
    val maxFare = state.bidTrip?.fareRange?.max ?: Double.MAX_VALUE
    val step = if (offered >= 50.0) 5f else 1f
    val inFlight = state.isBidActionInFlight
    var target by remember(offered) { mutableFloatStateOf(offered.toFloat()) }
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().wrapContentHeight().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                stringResource(R.string.rider_searching_drivers),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.rider_your_offer_label), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.rider_sar_label), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 24.sp), color = DarrbiTheme.colors.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatFare(target.toDouble()), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 32.sp), color = DarrbiTheme.colors.onSurface)
                }
                OfferStepButton("−", accent = false, enabled = !inFlight && target > offered) { target = (target - step).coerceAtLeast(offered.toFloat()) }
                Spacer(Modifier.width(10.dp))
                OfferStepButton("+", accent = true, enabled = !inFlight && target < maxFare) { target = (target + step).coerceAtMost(maxFare.toFloat()) }
            }
            Spacer(Modifier.height(14.dp))
            WideTonalButton(
                text = stringResource(R.string.rider_change_price),
                textColor = DarrbiTheme.colors.onSurfaceVariant,
                enabled = !inFlight && target.toDouble() > offered,
                onClick = { onRaise(target.toDouble()) },
            )
            Spacer(Modifier.height(16.dp))
            // Payment row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                CashGlyph()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.rider_sar_amount, formatFare(offered)), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
                    Text(stringResource(R.string.rider_pays_for_ride), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = DarrbiTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline)) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    RouteHeader(
                        pickup = state.pickup?.address?.takeIf { it.isNotBlank() } ?: stringResource(R.string.rider_current_location),
                        destination = state.destination?.address.orEmpty(),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            WideTonalButton(
                text = stringResource(R.string.rider_cancel_request),
                textColor = DarrbiTheme.colors.onSurface,
                enabled = !inFlight,
                onClick = onCancel,
            )
        }
    }
}

/** Rounded +/- stepper button; [accent] = the filled-green increment per the reference. */
@Composable
private fun OfferStepButton(symbol: String, accent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (accent) DarrbiTheme.colors.primary else DarrbiTheme.colors.surface,
        border = if (accent) null else androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = DarrbiTheme.typography.titleLarge, color = if (accent) DarrbiTheme.colors.onPrimary else DarrbiTheme.colors.onSurface)
        }
    }
}

/** Full-width light-grey (tonal) button used for Change Price / Cancel Request. */
@Composable
private fun WideTonalButton(text: String, textColor: Color, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = DarrbiTheme.colors.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = DarrbiTheme.typography.button, color = if (enabled) textColor else DarrbiTheme.colors.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/** Small banknote glyph for the payment row (no icon dependency). */
@Composable
private fun CashGlyph() {
    Box(
        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).border(1.5.dp, DarrbiTheme.colors.onSurfaceVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(DarrbiTheme.colors.onSurfaceVariant))
    }
}

/**
 * Full-screen "Select Offers" list (per the reference): a back + Cancel Request top bar over the dimmed
 * route map, then a scroll of driver offer cards with a live TTL bar and Decline / Accept.
 */
@Composable
private fun SelectOffersList(
    bids: List<com.mytm.darrbi.domain.model.Bid>,
    inFlight: Boolean,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(Color.Black.copy(alpha = 0.18f)).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(DarrbiTheme.colors.surface).clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_back), tint = DarrbiTheme.colors.onSurface)
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(50),
                color = DarrbiTheme.colors.surface,
                shadowElevation = 3.dp,
                onClick = onCancel,
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Close, null, tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rider_cancel_request), style = DarrbiTheme.typography.button.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface)
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp)) // balances the profile avatar drawn at the top-end
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            bids.forEach { bid ->
                OfferCard(bid = bid, enabled = !inFlight, onAccept = { onAccept(bid.bidId) }, onDecline = { onDecline(bid.bidId) })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OfferCard(
    bid: com.mytm.darrbi.domain.model.Bid,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = bid.driverImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.user_placeholder),
                    error = painterResource(R.drawable.user_placeholder),
                    fallback = painterResource(R.drawable.user_placeholder),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bid.driverName ?: stringResource(R.string.rider_captain_fallback),
                        style = DarrbiTheme.typography.title.copy(fontSize = 17.sp),
                        color = DarrbiTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    bid.driverCar?.let {
                        Text(it, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                bid.driverRating?.let { rating ->
                    val ratingText = String.format(Locale.US, "%.1f/5", rating)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = DarrbiTheme.colors.warning, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = bid.driverTotalReviews?.takeIf { it > 0 }
                                ?.let { stringResource(R.string.rider_rating_reviews, ratingText, it) } ?: ratingText,
                            style = DarrbiTheme.typography.label,
                            color = DarrbiTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TtlBar(bidId = bid.bidId)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.rider_sar_amount, formatFare(bid.fare)),
                    style = DarrbiTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = DarrbiTheme.colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                val eta = bid.etaToPickupSec?.let { stringResource(R.string.rider_eta_min, (it / 60).coerceAtLeast(1)) }
                val dist = bid.pickupDistanceKm?.let { "${formatFare(it)} km" }
                val sub = listOfNotNull(dist, eta).joinToString(" • ")
                if (sub.isNotBlank()) Text(sub, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onDecline),
                    shape = RoundedCornerShape(14.dp),
                    color = DarrbiTheme.colors.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.rider_decline), style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onSurface)
                    }
                }
                DarrbiPrimaryButton(
                    text = stringResource(R.string.rider_accept),
                    onClick = onAccept,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A green bar that depletes over the bid TTL (client-side, from when the offer first appears). */
@Composable
private fun TtlBar(bidId: String) {
    val progress = remember(bidId) { Animatable(1f) }
    LaunchedEffect(bidId) { progress.animateTo(0f, tween(durationMillis = BID_TTL_MS, easing = LinearEasing)) }
    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(DarrbiTheme.colors.outline.copy(alpha = 0.4f))) {
        Box(modifier = Modifier.fillMaxWidth(progress.value.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(DarrbiTheme.colors.primary))
    }
}

/** Bid TTL (mirror of the server `SETTING_BID_TTL_SECONDS` default) for the countdown bar. */
private const val BID_TTL_MS = 45_000

/** V2 fare-range factors (mirror the server `SETTING_BID_*` defaults) for the client-side hint. */
private const val V2_MIN_FACTOR = 0.80
private const val V2_MAX_FACTOR = 2.00

/**
 * Renders a plate so each Arabic letter shows as a standalone glyph (Saudi plates display the letters
 * individually). A space between consecutive Arabic letters breaks the connecting ligature, e.g.
 * "3002-بعا" → "3002-ب ع ا"; digits and separators are left untouched.
 */
private fun formatPlate(plate: String): String {
    fun isArabicLetter(c: Char) = c in '؀'..'ۿ'
    return buildString {
        plate.forEachIndexed { i, c ->
            append(c)
            val next = plate.getOrNull(i + 1)
            if (isArabicLetter(c) && next != null && isArabicLetter(next)) append(' ')
        }
    }
}

/** Opens the phone dialer pre-filled with [number]. */
private fun dialNumber(context: android.content.Context, number: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")),
        )
    }
}

/** "We are cancelling your ride" — shown while the rider-cancelled request is in flight (per the reference). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CancellingOverlay() {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.image_car), contentDescription = null, modifier = Modifier.height(72.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.cancel_in_progress),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = DarrbiTheme.colors.primary, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
        }
    }
}

/** "Your ride is cancelled" + Done — the final cancellation confirmation (per the reference). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.RideCancelledOverlay(onDone: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Image(painterResource(R.drawable.image_car), contentDescription = null, modifier = Modifier.height(72.dp), contentScale = ContentScale.Fit)
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(DarrbiTheme.colors.buttonContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = DarrbiTheme.colors.onButton, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.cancel_done_title),
                style = DarrbiTheme.typography.titleLarge,
                color = DarrbiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onDone),
                shape = RoundedCornerShape(14.dp),
                color = DarrbiTheme.colors.buttonContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_done), style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onButton)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Captain on the way (after driver_accepted): PIN, ETA, cab + driver card, cancellation, cancel.
// ---------------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DriverOnWayOverlay(
    trip: AcceptedTrip,
    onCall: () -> Unit,
    onChat: () -> Unit,
    onCancel: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            // PIN to share with the captain.
            Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.primary.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.rider_share_pin),
                        style = DarrbiTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = DarrbiTheme.colors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(trip.pin, style = DarrbiTheme.typography.titleLarge.copy(fontSize = 20.sp), color = DarrbiTheme.colors.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = trip.etaMinutes?.let { stringResource(R.string.rider_onway_message, it) }
                    ?: stringResource(R.string.rider_onway_message_no_eta),
                style = DarrbiTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            DriverCabCard(trip = trip, onChat = onChat, onCall = onCall)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.rider_cancellation_fee, formatFare(trip.cancellationFee)),
                style = DarrbiTheme.typography.label,
                color = DarrbiTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            DarrbiSecondaryButton(text = stringResource(R.string.rider_searching_cancel), onClick = onCancel)
        }
    }
}

/** The cab row (image · name + seats · description · plate) shared by the active-ride cards. */
@Composable
private fun CabHeaderRow(trip: AcceptedTrip) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.placeholder_select_car),
            contentDescription = null,
            modifier = Modifier.width(64.dp).height(42.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.cabName, style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
                if (trip.seats > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Person, null, tint = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(trip.seats.toString(), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                }
            }
            if (trip.cabDescription.isNotBlank()) {
                Text(trip.cabDescription, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatPlate(trip.plateNo), style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface)
    }
}

/** The green-bordered cab + driver card (cab + driver, chat/call) — used on the on-the-way view. */
@Composable
private fun DriverCabCard(trip: AcceptedTrip, onChat: () -> Unit, onCall: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            CabHeaderRow(trip)
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = trip.driverImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.user_placeholder),
                    error = painterResource(R.drawable.user_placeholder),
                    fallback = painterResource(R.drawable.user_placeholder),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(trip.driverName, style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
                    Text(stringResource(R.string.profile_status_good), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
                }
                ContactCircle(iconRes = R.drawable.icon_chat, contentDescription = stringResource(R.string.cd_chat), onClick = onChat)
                Spacer(Modifier.width(12.dp))
                ContactCircle(iconRes = R.drawable.icon_phone, contentDescription = stringResource(R.string.cd_call), onClick = onCall)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Captain arrived at pickup (driver_reached): car image, "your ride has arrived", wait timer, I am coming.
// ---------------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DriverArrivedOverlay(
    arrivedAtMillis: Long?,
    onImComing: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.placeholder_select_car),
                contentDescription = null,
                modifier = Modifier.width(180.dp).height(110.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.rider_arrived_title),
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            ArrivalTimer(arrivedAtMillis = arrivedAtMillis ?: 0L)
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(text = stringResource(R.string.rider_im_coming), onClick = onImComing)
        }
    }
}

/** Count-up "M:SS" wait timer since the captain reached pickup. */
@Composable
private fun ArrivalTimer(arrivedAtMillis: Long) {
    var elapsedSeconds by remember(arrivedAtMillis) { mutableStateOf(0L) }
    LaunchedEffect(arrivedAtMillis) {
        if (arrivedAtMillis <= 0L) return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - arrivedAtMillis).coerceAtLeast(0L)) / 1000L
            kotlinx.coroutines.delay(1000L)
        }
    }
    Text(
        text = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
        style = DarrbiTheme.typography.titleLarge.copy(fontSize = 34.sp),
        color = DarrbiTheme.colors.onSurface,
    )
}

// ---------------------------------------------------------------------------------------------
// Trip started (trip_started): in-trip header + cab/driver card; map shows pickup → destination.
// ---------------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TripStartedOverlay(
    trip: AcceptedTrip,
    pickupAddress: String,
    destinationAddress: String,
    onChange: () -> Unit,
    onHelp: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            // Pickup (green) → destination (red) addresses, with a Change action on the destination.
            TripRouteAddresses(pickupAddress = pickupAddress, destinationAddress = destinationAddress, onChange = onChange)
            Spacer(Modifier.height(16.dp))
            // Cab + "you are riding with <driver>" card.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarrbiTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.primary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    CabHeaderRow(trip)
                    HorizontalDivider(color = DarrbiTheme.colors.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = trip.driverImageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.user_placeholder),
                            error = painterResource(R.drawable.user_placeholder),
                            fallback = painterResource(R.drawable.user_placeholder),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.rider_riding_with), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
                            Text(trip.driverName, style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Help chip (green tonal), start-aligned.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarrbiTheme.colors.primary.copy(alpha = 0.12f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onHelp),
            ) {
                Text(
                    stringResource(R.string.rider_help),
                    style = DarrbiTheme.typography.bodyMedium,
                    color = DarrbiTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Pickup (green dot) → destination (red dot) address rows, connected by a line, with a Change action. */
@Composable
private fun TripRouteAddresses(pickupAddress: String, destinationAddress: String, onChange: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Dot rail: green dot, connecting line, red dot.
        Column(
            modifier = Modifier.padding(top = 6.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(DarrbiTheme.colors.primary))
            Box(modifier = Modifier.width(2.dp).height(28.dp).background(DarrbiTheme.colors.outline))
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(DarrbiTheme.colors.error))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pickupAddress,
                style = DarrbiTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = DarrbiTheme.colors.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    destinationAddress,
                    style = DarrbiTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = DarrbiTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.rider_change_destination),
                    style = DarrbiTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = DarrbiTheme.colors.primary,
                    modifier = Modifier.clickable(onClick = onChange),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Change drop-off: new-fare preview + Pay Remaining (after picking a new drop in the search).
// ---------------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChangeDropConfirmOverlay(
    pickupAddress: String,
    destinationAddress: String,
    newFare: Double?,
    remaining: Double,
    balance: Double?,
    arrivalMinutes: Int?,
    isConfirming: Boolean,
    onConfirm: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            TripRouteAddresses(pickupAddress = pickupAddress, destinationAddress = destinationAddress, onChange = {})
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.rider_your_new_fare), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(12.dp))
            // New-fare card: original ride + new fare.
            Surface(shape = RoundedCornerShape(16.dp), color = DarrbiTheme.colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.placeholder_select_car),
                            contentDescription = null,
                            modifier = Modifier.width(64.dp).height(42.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.rider_original_ride), style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface)
                            if (arrivalMinutes != null) {
                                Text(
                                    stringResource(R.string.rider_arrival_at, arrivalClock(arrivalMinutes)),
                                    style = DarrbiTheme.typography.label,
                                    color = DarrbiTheme.colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = DarrbiTheme.colors.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.rider_new_fare), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(
                            if (newFare != null) stringResource(R.string.rider_fare_sar, formatFare(newFare)) else "…",
                            style = DarrbiTheme.typography.title.copy(fontSize = 15.sp),
                            color = DarrbiTheme.colors.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_balance), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
                    Text(stringResource(R.string.rider_fare_sar, formatFare(balance ?: 0.0)), style = DarrbiTheme.typography.title.copy(fontSize = 15.sp), color = DarrbiTheme.colors.onSurface)
                }
            }
            Spacer(Modifier.height(16.dp))
            val label = if (remaining > 0.0) {
                stringResource(R.string.rider_pay_remaining, formatFare(remaining))
            } else {
                stringResource(R.string.common_confirm)
            }
            DarrbiPrimaryButton(text = label, onClick = onConfirm, enabled = newFare != null && !isConfirming)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// In-trip Help sheet: call center, share ride details, emergency "call the police".
// ---------------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.HelpOverlay(
    onBack: () -> Unit,
    onCallCenter: () -> Unit,
    onShare: () -> Unit,
    onCallPolice: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, DarrbiTheme.colors.outline, CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_back), tint = DarrbiTheme.colors.onSurface)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.rider_help), style = DarrbiTheme.typography.titleLarge.copy(fontSize = 24.sp), color = DarrbiTheme.colors.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = DarrbiTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline), modifier = Modifier.fillMaxWidth()) {
                Column {
                    HelpRow(iconRes = R.drawable.icon_phone, label = stringResource(R.string.rider_help_call_center), onClick = onCallCenter)
                    HorizontalDivider(color = DarrbiTheme.colors.outline)
                    HelpRow(icon = Icons.AutoMirrored.Filled.Send, label = stringResource(R.string.rider_help_share_ride), onClick = onShare)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.rider_emergency_help), style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = DarrbiTheme.colors.error,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onCallPolice),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = DarrbiTheme.colors.onError, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rider_call_police), style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onError)
                }
            }
        }
    }
}

/** A single Help action row (icon + label). Pass either [iconRes] (drawable) or [icon] (vector). */
@Composable
private fun HelpRow(
    label: String,
    onClick: () -> Unit,
    @androidx.annotation.DrawableRes iconRes: Int? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            iconRes != null -> Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
            icon != null -> Icon(icon, contentDescription = null, tint = DarrbiTheme.colors.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = DarrbiTheme.typography.bodyMedium.copy(fontSize = 16.sp), color = DarrbiTheme.colors.onSurface)
    }
}

/** A clock time [minutes] from now, formatted like "3:09 pm" (used for the new-fare arrival preview). */
private fun arrivalClock(minutes: Int): String {
    val target = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, minutes) }.time
    return java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(target)
}

/** Shares the active ride's details (driver, plate, route) via the Android share sheet. */
private fun shareRideDetails(
    context: android.content.Context,
    trip: AcceptedTrip,
    pickup: PlaceLocation?,
    destination: PlaceLocation?,
) {
    val text = buildString {
        append(context.getString(R.string.rider_share_ride_details_title))
        append("\n")
        append("${trip.driverName} · ${trip.cabName} · ${trip.plateNo}")
        pickup?.address?.takeIf { it.isNotBlank() }?.let { append("\n${it}") }
        destination?.address?.takeIf { it.isNotBlank() }?.let { append("\n→ ${it}") }
    }
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, null))
    }
}

/** Opens [url] in the browser (used for the VAT invoice, like ride-android). */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Trip completed (trip_completed): rate the captain, payment/balance/loyalty, VAT invoice.
// ---------------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.RatingOverlay(
    driverName: String,
    driverImageUrl: String?,
    rating: Int,
    totalPayment: Double,
    balance: Double?,
    loyaltyPoints: Int,
    isSubmitting: Boolean,
    onRate: (Int) -> Unit,
    onDownloadInvoice: () -> Unit,
    onSubmit: () -> Unit,
    onHeight: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DarrbiTheme.colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            // Driver + "Rate Your Captain".
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = driverImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.user_placeholder),
                    error = painterResource(R.drawable.user_placeholder),
                    fallback = painterResource(R.drawable.user_placeholder),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.rider_rate_captain), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurfaceVariant)
                    Text(driverName, style = DarrbiTheme.typography.title.copy(fontSize = 18.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
                }
            }
            Spacer(Modifier.height(16.dp))
            // Star selector.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (star in 1..5) {
                    val filled = star <= rating
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(DarrbiTheme.colors.surfaceVariant)
                            .clickable { onRate(star) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null,
                            tint = if (filled) DarrbiTheme.colors.warning else DarrbiTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            RatingStatRow(label = stringResource(R.string.rider_total_payment), value = stringResource(R.string.rider_fare_sar, formatFare(totalPayment)))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            RatingStatRow(label = stringResource(R.string.profile_balance), value = stringResource(R.string.rider_fare_sar, formatFare(balance ?: 0.0)))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            RatingStatRow(label = stringResource(R.string.rider_loyalty_points), value = loyaltyPoints.toString())
            Spacer(Modifier.height(16.dp))
            DarrbiSecondaryButton(text = stringResource(R.string.rider_download_invoice), onClick = onDownloadInvoice)
            Spacer(Modifier.height(12.dp))
            DarrbiPrimaryButton(text = stringResource(R.string.rider_submit_rating), onClick = onSubmit, enabled = rating >= 1 && !isSubmitting)
        }
    }
}

/** A label-over-value row used on the rating screen (Total Payment / Balance / Loyalty Points). */
@Composable
private fun RatingStatRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = DarrbiTheme.typography.title.copy(fontSize = 18.sp), color = DarrbiTheme.colors.onSurface)
    }
}

/** Chat/call button on the on-the-way card — the drawable is shown as-is (no background, no tint). */
@Composable
internal fun ContactCircle(@androidx.annotation.DrawableRes iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(44.dp),
        )
    }
}
