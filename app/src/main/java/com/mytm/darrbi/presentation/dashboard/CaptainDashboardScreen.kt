package com.mytm.darrbi.presentation.dashboard

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiCard
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField
import com.mytm.darrbi.presentation.components.CancelReasonsSheet
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.OpenTrip
import com.mytm.darrbi.domain.model.RideRequest
import com.mytm.darrbi.presentation.common.LocationPermissionDeniedDialog
import com.mytm.darrbi.presentation.rider.AnimatedCarMarker
import com.mytm.darrbi.presentation.rider.ContactCircle
import com.mytm.darrbi.presentation.rider.rememberCarMarkerIcon
import com.mytm.darrbi.presentation.rider.rememberMarkerIcon
import com.mytm.darrbi.presentation.common.openAppSettings
import com.mytm.darrbi.presentation.common.rememberLocationPermissionState

/**
 * Captain dashboard: a full-screen map with a bottom card whose content is driven by `GET /captains`
 * (mirrors ride-android's MyDashboard.verifyWSLSubStatus):
 * under review → approved (Start Now) → enter IBAN → no riders around.
 */
@Composable
fun CaptainDashboardScreen(
    onSeeDetails: () -> Unit,
    onProfile: () -> Unit = {},
    onChat: (com.mytm.darrbi.domain.model.RideRequest) -> Unit = {},
    viewModel: CaptainDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Default camera over Riyadh until the device location resolves.
    val riyadh = LatLng(24.7136, 46.6753)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(riyadh, 14f)
    }

    // Once the captain is fully set up (WASL approved + IBAN saved → "no riders" map), ask for location
    // permission and, when granted, centre the map on the device location.
    val isReady = state.stage == CaptainStage.NoRiders
    val locationPermission = rememberLocationPermissionState()
    LaunchedEffect(isReady) { if (isReady) locationPermission.requestIfNeeded() }
    LaunchedEffect(isReady, locationPermission.isGranted) {
        if (isReady && locationPermission.isGranted) {
            viewModel.locateMe()
            // Verified captain on the dashboard → stream live location to the server over the socket.
            viewModel.startLocationStreaming()
        }
    }
    LaunchedEffect(state.myLocation, state.incomingRequest == null, state.activeTrip == null) {
        // While a request/trip is showing, the bounds-fit below frames the route instead of centring on me.
        if (state.incomingRequest != null || state.activeTrip != null) return@LaunchedEffect
        state.myLocation?.let {
            val target = LatLng(it.latitude, it.longitude)
            // Smoothly pan to the current location; fall back to an instant move if animation isn't ready.
            runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 16f), 800) }
                .onFailure { cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 16f) }
        }
    }
    // Incoming request → frame pickup + destination + the captain on the map.
    LaunchedEffect(state.incomingRequest?.tripId, state.requestRoutePoints, state.myLocation) {
        val request = state.incomingRequest ?: return@LaunchedEffect
        val pts = buildList {
            add(LatLng(request.pickup.latitude, request.pickup.longitude))
            add(LatLng(request.destination.latitude, request.destination.longitude))
            state.myLocation?.let { add(LatLng(it.latitude, it.longitude)) }
            addAll(state.requestRoutePoints.map { LatLng(it.latitude, it.longitude) })
        }
        val bounds = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
        runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, REQUEST_BOUNDS_PADDING_PX), 700) }
    }
    // Accepted trip → frame the route: driver→destination while dropping, else captain→pickup.
    LaunchedEffect(state.activeTrip?.tripId, state.activeRoutePoints, state.myLocation, state.tripInProgress) {
        val active = state.activeTrip ?: return@LaunchedEffect
        val anchor = if (state.tripInProgress) active.destination else active.pickup
        val pts = buildList {
            add(LatLng(anchor.latitude, anchor.longitude))
            state.myLocation?.let { add(LatLng(it.latitude, it.longitude)) }
            addAll(state.activeRoutePoints.map { LatLng(it.latitude, it.longitude) })
        }
        if (pts.size < 2) return@LaunchedEffect
        val bounds = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
        runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, REQUEST_BOUNDS_PADDING_PX), 700) }
    }
    // Open-trip detail → frame pickup + destination (+ route + captain).
    LaunchedEffect(state.biddingTrip?.tripId, state.biddingRoutePoints, state.myLocation) {
        val bidding = state.biddingTrip ?: return@LaunchedEffect
        val pts = buildList {
            add(LatLng(bidding.pickup.latitude, bidding.pickup.longitude))
            add(LatLng(bidding.dropoff.latitude, bidding.dropoff.longitude))
            state.myLocation?.let { add(LatLng(it.latitude, it.longitude)) }
            addAll(state.biddingRoutePoints.map { LatLng(it.latitude, it.longitude) })
        }
        val bounds = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
        runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, REQUEST_BOUNDS_PADDING_PX), 700) }
    }
    // Toasts: accepted confirmation + transient errors.
    val ctx = LocalContext.current
    val acceptedMsg = stringResource(R.string.captain_request_accepted)
    LaunchedEffect(state.tripAccepted) {
        if (state.tripAccepted) {
            android.widget.Toast.makeText(ctx, acceptedMsg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.onEvent(CaptainDashboardEvent.ConsumeAccepted)
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.onEvent(CaptainDashboardEvent.ConsumeError)
        }
    }
    // Trip started after a correct OTP.
    val tripStartedMsg = stringResource(R.string.captain_trip_started)
    LaunchedEffect(state.tripStarted) {
        if (state.tripStarted) {
            android.widget.Toast.makeText(ctx, tripStartedMsg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.onEvent(CaptainDashboardEvent.ConsumeTripStarted)
        }
    }
    // V2: you lost / the trip closed before you could win.
    val bidLostMsg = stringResource(R.string.captain_bid_lost)
    LaunchedEffect(state.bidLostReason) {
        if (state.bidLostReason != null) {
            android.widget.Toast.makeText(ctx, bidLostMsg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.onEvent(CaptainDashboardEvent.ConsumeBidLost)
        }
    }
    // Rider cancelled the accepted trip → toast and return to the trips list.
    val riderCancelledMsg = stringResource(R.string.captain_rider_cancelled)
    LaunchedEffect(state.riderCancelledNotice) {
        if (state.riderCancelledNotice) {
            android.widget.Toast.makeText(ctx, riderCancelledMsg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.onEvent(CaptainDashboardEvent.ConsumeRiderCancelled)
        }
    }

    // System-back closes an open request detail (back to the list) instead of leaving the dashboard.
    BackHandler(enabled = state.biddingTrip != null) { viewModel.onEvent(CaptainDashboardEvent.DismissBidSheet) }
    BackHandler(enabled = state.showCancelSheet) { viewModel.onEvent(CaptainDashboardEvent.DismissCancelSheet) }

    Box(modifier = Modifier.fillMaxSize()) {
        val request = state.incomingRequest
        val active = state.activeTrip
        val bidding = state.biddingTrip
        val newDrop = state.destinationChanged
        val mapBusy = request != null || active != null || bidding != null
        // Verified + idle (no request open) → the full-screen broadcast list (reference).
        val showList = state.stage == CaptainStage.NoRiders && !mapBusy
        if (showList) {
            DriverOpenTripsView(
                state = state,
                onEvent = viewModel::onEvent,
                onProfile = onProfile,
                modifier = Modifier.fillMaxSize(),
            )
            if (isReady && locationPermission.isPermanentlyDenied) {
                LocationPermissionDeniedDialog(onOpenSettings = { ctx.openAppSettings() })
            }
            return@Box
        }
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            // Hide the blue dot during a request/active trip (we draw the captain's car marker instead).
            properties = MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = isReady && locationPermission.isGranted && !mapBusy),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false,
            ),
        ) {
            val pinIcon = rememberMarkerIcon()
            val carIcon = rememberCarMarkerIcon()
            val routeColor = DarrbiTheme.colors.primary
            // Incoming request → pickup→destination route + pickup/destination pins + the captain's car.
            if (request != null) {
                val route = remember(state.requestRoutePoints) {
                    state.requestRoutePoints.map { LatLng(it.latitude, it.longitude) }
                }
                if (route.size >= 2) Polyline(points = route, color = routeColor, width = 6f)
                Marker(
                    state = rememberMarkerState(key = "req_pickup", position = LatLng(request.pickup.latitude, request.pickup.longitude)),
                    icon = pinIcon, anchor = Offset(0.5f, 0.5f),
                )
                Marker(
                    state = rememberMarkerState(key = "req_dest", position = LatLng(request.destination.latitude, request.destination.longitude)),
                    icon = pinIcon, anchor = Offset(0.5f, 0.5f),
                )
                state.carMarker?.let {
                    AnimatedCarMarker(target = LatLng(it.latitude, it.longitude), icon = carIcon, key = "req_driver")
                }
            } else if (active != null) {
                // Navigate (captain→pickup) or dropping (pickup→destination) route + the relevant pin + car.
                val route = remember(state.activeRoutePoints) {
                    state.activeRoutePoints.map { LatLng(it.latitude, it.longitude) }
                }
                if (route.size >= 2) Polyline(points = route, color = routeColor, width = 6f)
                if (state.ratingRider) {
                    // Completed → show both ends of the trip (no live car).
                    Marker(state = rememberMarkerState(key = "rate_pickup", position = LatLng(active.pickup.latitude, active.pickup.longitude)), icon = pinIcon, anchor = Offset(0.5f, 0.5f))
                    Marker(state = rememberMarkerState(key = "rate_dest", position = LatLng(active.destination.latitude, active.destination.longitude)), icon = pinIcon, anchor = Offset(0.5f, 0.5f))
                } else {
                    val pinTarget = if (state.tripInProgress) active.destination else active.pickup
                    Marker(
                        state = rememberMarkerState(key = if (state.tripInProgress) "act_dest" else "act_pickup", position = LatLng(pinTarget.latitude, pinTarget.longitude)),
                        icon = pinIcon, anchor = Offset(0.5f, 0.5f),
                    )
                    state.carMarker?.let {
                        AnimatedCarMarker(target = LatLng(it.latitude, it.longitude), icon = carIcon, key = "act_driver")
                    }
                }
            } else if (bidding != null) {
                // Open-trip detail → pickup→destination route + pickup/destination pins + the captain's car.
                val route = remember(state.biddingRoutePoints) {
                    state.biddingRoutePoints.map { LatLng(it.latitude, it.longitude) }
                }
                if (route.size >= 2) Polyline(points = route, color = routeColor, width = 6f)
                Marker(
                    state = rememberMarkerState(key = "bid_pickup", position = LatLng(bidding.pickup.latitude, bidding.pickup.longitude)),
                    icon = pinIcon, anchor = Offset(0.5f, 0.5f),
                )
                Marker(
                    state = rememberMarkerState(key = "bid_dest", position = LatLng(bidding.dropoff.latitude, bidding.dropoff.longitude)),
                    icon = pinIcon, anchor = Offset(0.5f, 0.5f),
                )
                state.carMarker?.let {
                    AnimatedCarMarker(target = LatLng(it.latitude, it.longitude), icon = carIcon, key = "bid_driver")
                }
            }
        }

        // Idle captain marker (centre dot) — hidden while a request/trip frames the route instead.
        if (!mapBusy) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(DarrbiTheme.colors.primary)
                    .border(3.dp, DarrbiTheme.colors.onPrimary, CircleShape),
            )
        }

        // Top-start: the 3-dot menu (Cancel Ride) while navigating to a rider. (The RIDER/CAPTAIN switch
        // now lives in the profile screen.)
        if (active != null) {
            CaptainMenu(
                showMenu = state.showMenu,
                onToggle = { viewModel.onEvent(CaptainDashboardEvent.ToggleMenu) },
                onCancel = { viewModel.onEvent(CaptainDashboardEvent.CancelTrip) },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
            )
        }

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
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = stringResource(R.string.cd_profile),
                tint = DarrbiTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }

        val navContext = LocalContext.current
        if (request != null) {
            // An incoming ride request takes over the bottom card (over the route map).
            BottomCard {
                RideRequestContent(
                    request = request,
                    pickupDistanceKm = state.requestPickupDistanceKm,
                    pickupTimeMinutes = state.requestPickupTimeMinutes,
                    isHandling = state.isHandlingRequest,
                    onAccept = { viewModel.onEvent(CaptainDashboardEvent.AcceptRequest) },
                    onDecline = { viewModel.onEvent(CaptainDashboardEvent.DeclineRequest) },
                )
            }
        } else if (active != null && state.showCancelSheet) {
            // Cancel Ride → reasons picker over the route map (driver-cancelled with the chosen reason).
            CancelReasonsSheet(
                reasons = state.cancelReasons,
                selectedId = state.selectedCancelReasonId,
                isLoading = state.cancelReasonsLoading,
                isSubmitting = state.isCancelling,
                confirmText = stringResource(R.string.cancel_ride_confirm),
                onSelect = { viewModel.onEvent(CaptainDashboardEvent.SelectCancelReason(it)) },
                onSubmit = { viewModel.onEvent(CaptainDashboardEvent.SubmitCancel) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (active != null && newDrop != null) {
            // Rider changed the drop-off mid-trip → "Drop-off Address Changed!" overlay (over the route map).
            BottomCard {
                DestinationChangedContent(
                    address = newDrop.address,
                    onNavigate = {
                        openNavigation(navContext, newDrop.latitude, newDrop.longitude)
                        viewModel.onEvent(CaptainDashboardEvent.DismissDestinationChange)
                    },
                )
            }
        } else if (active != null && state.ratingRider) {
            // Completed → rate the rider.
            BottomCard {
                RateRiderContent(
                    riderName = active.riderName,
                    riderImageUrl = active.riderImageUrl,
                    stars = state.riderStars,
                    earning = active.estimateEarning,
                    paymentMethod = active.paymentMethod,
                    loyaltyKm = state.dropDistanceKm ?: active.destDistanceKm,
                    isSubmitting = state.isSubmittingReview,
                    onRate = { viewModel.onEvent(CaptainDashboardEvent.SelectRiderRating(it)) },
                    onSubmit = { viewModel.onEvent(CaptainDashboardEvent.SubmitRiderRating) },
                )
            }
        } else if (active != null && state.tripInProgress) {
            // Trip started → "dropping" screen: nav banner over the map + the drop-off card.
            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                NavBanner(distanceKm = state.dropDistanceKm)
                Spacer(Modifier.height(8.dp))
                DarrbiCard(navigationBarPadding = true) {
                    DroppingContent(
                        riderName = active.riderName,
                        distanceKm = state.dropDistanceKm,
                        etaMinutes = state.dropTimeMinutes,
                        isCompleting = state.isCompleting,
                        onEndRide = { viewModel.onEvent(CaptainDashboardEvent.EndRide) },
                    )
                }
            }
        } else if (active != null && state.awaitingOtp) {
            // At pickup → enter the rider's OTP to start the trip.
            BottomCard {
                OtpEntryContent(
                    riderName = active.riderName,
                    riderImageUrl = active.riderImageUrl,
                    riderRating = active.riderRating,
                    otp = state.otpInput,
                    isStarting = state.isStartingTrip,
                    isError = state.otpError,
                    onOtp = { viewModel.onEvent(CaptainDashboardEvent.EnterOtp(it)) },
                    onSubmit = { viewModel.onEvent(CaptainDashboardEvent.SubmitOtp) },
                )
            }
        } else if (active != null) {
            // Accepted trip → navigate-to-rider card.
            BottomCard {
                NavigateRiderContent(
                    trip = active,
                    pickupDistanceKm = state.requestPickupDistanceKm,
                    pickupTimeMinutes = state.requestPickupTimeMinutes,
                    navigateStarted = state.navigateStarted,
                    isHandling = state.isHandlingRequest,
                    onCall = { active.riderMobile?.let { dialPhone(navContext, it) } },
                    onChat = { onChat(active) },
                    onNavigate = {
                        openNavigation(navContext, active.pickup.latitude, active.pickup.longitude)
                        viewModel.onEvent(CaptainDashboardEvent.StartNavigate)
                    },
                    onReached = { viewModel.onEvent(CaptainDashboardEvent.MarkReached) },
                )
            }
        } else if (bidding != null) {
            // Tapped an open request → its detail card over the route map (accept the fare or counter).
            BottomCard {
                RequestDetailContent(
                    trip = bidding,
                    isPlacing = state.isPlacingBid,
                    errorCode = state.bidErrorCode,
                    onAccept = { viewModel.onEvent(CaptainDashboardEvent.AcceptFare(bidding.tripId)) },
                    onOffer = { fare -> viewModel.onEvent(CaptainDashboardEvent.CounterBid(bidding.tripId, fare)) },
                    onCancel = { viewModel.onEvent(CaptainDashboardEvent.DismissBidSheet) },
                )
            }
        } else {
            when (state.stage) {
                CaptainStage.Loading -> Unit
                CaptainStage.UnderReview -> BottomCard { UnderReviewContent(onSeeDetails) }
                CaptainStage.Approved -> BottomCard { ApprovedContent { viewModel.onEvent(CaptainDashboardEvent.StartNow) } }
                CaptainStage.EnterIban -> BottomCard { EnterIbanContent(state, viewModel::onEvent) }
                CaptainStage.NoRiders -> BottomCard { NoRidersContent() }
            }
        }
    }

    // Location denied with "don't ask again" on the ready dashboard → instructions + Settings shortcut.
    if (isReady && locationPermission.isPermanentlyDenied) {
        val context = LocalContext.current
        LocationPermissionDeniedDialog(onOpenSettings = { context.openAppSettings() })
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BottomCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    // navigationBarPadding keeps the card content above the system nav bar; imePadding lifts it above the
    // keyboard (e.g. the PIN / IBAN inputs) so the card stays visible while typing.
    DarrbiCard(
        modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
        navigationBarPadding = true,
        content = content,
    )
}

@Composable
private fun UnderReviewContent(onSeeDetails: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CarBadge()
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.dashboard_app_under_process),
            style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dashboard_absher_subtitle),
            style = DarrbiTheme.typography.body.copy(fontSize = 13.sp),
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        DarrbiSecondaryButton(text = stringResource(R.string.common_see_details), onClick = onSeeDetails)
    }
}

@Composable
private fun ApprovedContent(onStartNow: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.image_car),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.45f),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.dashboard_approved_title),
            style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        DarrbiPrimaryButton(text = stringResource(R.string.common_start_now), onClick = onStartNow)
    }
}

@Composable
private fun EnterIbanContent(state: CaptainDashboardUiState, onEvent: (CaptainDashboardEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.iban_enter_title),
            style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
            color = DarrbiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.iban_enter_subtitle),
            style = DarrbiTheme.typography.body.copy(fontSize = 13.sp),
            color = DarrbiTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        DarrbiTextField(
            value = state.ibanInput,
            onValueChange = { onEvent(CaptainDashboardEvent.IbanChanged(it)) },
            label = stringResource(R.string.iban_label),
            keyboardType = KeyboardType.Text,
            isError = state.ibanError,
            visualTransformation = IbanVisualTransformation,
            trailingContent = {
                when {
                    state.ibanVerifying -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = DarrbiTheme.colors.primary,
                    )
                    state.ibanError -> Icon(Icons.Filled.Close, null, tint = DarrbiTheme.colors.error)
                    state.bankName != null -> Icon(Icons.Filled.Check, null, tint = DarrbiTheme.colors.primary)
                }
            },
        )
        if (state.ibanError) {
            Spacer(Modifier.height(8.dp))
            InfoPill(text = stringResource(R.string.iban_incorrect), error = true)
        } else if (state.bankName != null) {
            Spacer(Modifier.height(8.dp))
            InfoPill(text = stringResource(R.string.iban_bank_format, state.bankName), error = false)
        }
        Spacer(Modifier.height(14.dp))
        DarrbiPrimaryButton(
            text = stringResource(R.string.common_done),
            onClick = { onEvent(CaptainDashboardEvent.SubmitIban) },
            enabled = state.canSubmitIban,
        )
    }
}

@Composable
private fun NoRidersContent() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.no_riders_title),
            style = DarrbiTheme.typography.titleLarge.copy(fontSize = 20.sp),
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.no_riders_subtitle),
            style = DarrbiTheme.typography.body.copy(fontSize = 13.sp),
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        DarrbiPrimaryButton(text = stringResource(R.string.no_riders_navigate), onClick = {})
    }
}

// ---------------------------------------------------------------------------------------------
// V2 broadcast dispatch: the full-screen driver list (Online/Offline toggle + open-trip cards).
// ---------------------------------------------------------------------------------------------

@Composable
private fun DriverOpenTripsView(
    state: CaptainDashboardUiState,
    onEvent: (CaptainDashboardEvent) -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(DarrbiTheme.colors.surface).statusBarsPadding()) {
        // Top bar: menu (→ profile) · Online/Offline toggle · profile avatar.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(onClick = onProfile) {
                Icon(Icons.Filled.Menu, stringResource(R.string.cd_profile), tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            OnlineToggle(online = state.isOnline, onChange = { onEvent(CaptainDashboardEvent.SetOnline(it)) })
            Spacer(Modifier.weight(1f))
            CircleButton(onClick = onProfile) {
                Icon(Icons.Filled.Person, stringResource(R.string.cd_profile), tint = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
        if (!state.isOnline) OfflineBanner()
        when {
            state.openTrips.isNotEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                state.openTrips.forEach { trip ->
                    key(trip.tripId) {
                        SwipeableOpenTripCard(
                            trip = trip,
                            onClick = { onEvent(CaptainDashboardEvent.OpenBidSheet(trip.tripId)) },
                            onDismiss = { onEvent(CaptainDashboardEvent.DismissOpenTrip(trip.tripId)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            state.isOnline -> LookingForRides(modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(DarrbiTheme.colors.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** Offline / Online segmented pill (Offline = red active, Online = green active), per the reference. */
@Composable
private fun OnlineToggle(online: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(DarrbiTheme.colors.surfaceVariant).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleSegment(stringResource(R.string.captain_offline), active = !online, activeColor = DarrbiTheme.colors.error) { onChange(false) }
        ToggleSegment(stringResource(R.string.captain_online), active = online, activeColor = DarrbiTheme.colors.primary) { onChange(true) }
    }
}

@Composable
private fun ToggleSegment(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) activeColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = DarrbiTheme.typography.button.copy(fontSize = 14.sp),
            color = if (active) DarrbiTheme.colors.onPrimary else DarrbiTheme.colors.onSurfaceVariant,
        )
    }
}

/** Dark "You are currently offline" banner (per the reference). */
@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarrbiTheme.colors.buttonContainer,
    ) {
        Text(
            text = stringResource(R.string.captain_offline_banner),
            style = DarrbiTheme.typography.bodyMedium,
            color = DarrbiTheme.colors.onButton,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

/** Online + no open trips → the "Looking for rides…" searching state (per the reference). */
@Composable
private fun LookingForRides(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape).background(DarrbiTheme.colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Search, null, tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.captain_looking_rides_title),
            style = DarrbiTheme.typography.titleLarge,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.captain_looking_rides_sub),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** An [OpenTripCard] the captain can swipe (either direction) to remove the request from the list. */
@Composable
private fun SwipeableOpenTripCard(trip: OpenTrip, onClick: () -> Unit, onDismiss: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = DarrbiTheme.colors.error,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.captain_remove_request), tint = DarrbiTheme.colors.onError, modifier = Modifier.size(24.dp))
                    Icon(Icons.Filled.Delete, null, tint = DarrbiTheme.colors.onError, modifier = Modifier.size(24.dp))
                }
            }
        },
    ) {
        OpenTripCard(trip = trip, onClick = onClick)
    }
}

/** One broadcast ride-request card (rider, offered fare, distance·time, pickup/drop) — tap to bid. */
@Composable
private fun OpenTripCard(trip: OpenTrip, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = trip.riderImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.user_placeholder),
                    error = painterResource(R.drawable.user_placeholder),
                    fallback = painterResource(R.drawable.user_placeholder),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.riderName ?: stringResource(R.string.captain_rider_fallback),
                        style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
                        color = DarrbiTheme.colors.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = relativeAge(trip.createdAtMillis),
                        style = DarrbiTheme.typography.label,
                        color = DarrbiTheme.colors.onSurfaceVariant,
                    )
                }
                trip.riderRating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = DarrbiTheme.colors.warning, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(ratingLabel(rating, trip.riderTotalReviews), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.captain_sar, formatAmount(trip.riderOfferedFare)),
                    style = DarrbiTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = DarrbiTheme.colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = distanceTime(trip),
                    style = DarrbiTheme.typography.label,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline)) {
                Column {
                    AddressRow(stringResource(R.string.captain_pickup), trip.pickup.address, DarrbiTheme.colors.primary)
                    HorizontalDivider(color = DarrbiTheme.colors.outline)
                    AddressRow(stringResource(R.string.captain_drop_off), trip.dropoff.address, DarrbiTheme.colors.error)
                }
            }
        }
    }
}

@Composable
private fun AddressRow(label: String, address: String, dotColor: Color) {
    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).border(2.dp, dotColor, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            Text(
                text = address.takeIf { it.isNotBlank() } ?: stringResource(R.string.captain_location_point),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Enter-PIN card shown at pickup (`driver_reached`) per the reference: a segmented 4-box PIN input, the
 * rider row (avatar + name + rating), and a "Start Ride" button.
 */
@Composable
private fun OtpEntryContent(
    riderName: String?,
    riderImageUrl: String?,
    riderRating: Double?,
    otp: String,
    isStarting: Boolean,
    isError: Boolean,
    onOtp: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.captain_enter_pin_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
        Spacer(Modifier.height(20.dp))
        PinInput(otp = otp, isError = isError, onOtp = onOtp)
        if (isError) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.captain_otp_invalid), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.error)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DarrbiTheme.colors.outline)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = riderImageUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.user_placeholder),
                error = painterResource(R.drawable.user_placeholder),
                fallback = painterResource(R.drawable.user_placeholder),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = riderName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.captain_rider_fallback),
                    style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
                    color = DarrbiTheme.colors.onSurface,
                    maxLines = 1,
                )
                riderRating?.let { rating ->
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = DarrbiTheme.colors.warning, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(formatRating(rating), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        DarrbiPrimaryButton(
            text = stringResource(R.string.captain_start_ride),
            onClick = onSubmit,
            enabled = !isStarting && otp.length == PIN_LEN,
        )
    }
}

/** Segmented PIN input: a hidden text field driving [PIN_LEN] boxes (active box green, per the reference). */
@Composable
private fun PinInput(otp: String, isError: Boolean, onOtp: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = otp,
        onValueChange = { v -> if (v.length <= PIN_LEN && v.all(Char::isDigit)) onOtp(v) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = Modifier.focusRequester(focus),
        decorationBox = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(PIN_LEN) { index ->
                    val char = otp.getOrNull(index)?.toString() ?: ""
                    val active = index == otp.length
                    val border = when {
                        isError -> DarrbiTheme.colors.error
                        active -> DarrbiTheme.colors.primary
                        else -> DarrbiTheme.colors.outline
                    }
                    Box(
                        modifier = Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(14.dp))
                            .border(if (active || isError) 2.dp else 1.dp, border, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (char.isNotEmpty()) char else if (active) "|" else "",
                            style = DarrbiTheme.typography.titleLarge.copy(fontSize = 24.sp),
                            color = if (char.isNotEmpty()) DarrbiTheme.colors.onSurface else DarrbiTheme.colors.primary,
                        )
                    }
                }
            }
        },
    )
}

/** Rider PIN length (the reference shows 4 boxes). */
private const val PIN_LEN = 4

/** Dark navigation banner over the map during the trip (per the reference). */
@Composable
private fun NavBanner(distanceKm: Double?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarrbiTheme.colors.buttonContainer,
    ) {
        Text(
            text = distanceKm?.let { stringResource(R.string.captain_to_dropoff, formatKm(it)) }
                ?: stringResource(R.string.captain_head_dropoff),
            style = DarrbiTheme.typography.bodyMedium,
            color = DarrbiTheme.colors.onButton,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

/**
 * Drop-off-changed overlay (`rider_updated_destination`): a dashed-ring drop-off pin, the title/subtitle,
 * the rider's new address in a pill, and a Navigate button (per the reference). Navigate launches external
 * navigation to the new drop-off and returns the captain to the in-trip (start-ride) screen.
 */
@Composable
private fun DestinationChangedContent(address: String, onNavigate: () -> Unit) {
    val ringColor = DarrbiTheme.colors.outline
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .drawBehind {
                    drawCircle(
                        color = ringColor,
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(104.dp).clip(CircleShape).background(DarrbiTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = DarrbiTheme.colors.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.captain_dropoff_changed_title),
            style = DarrbiTheme.typography.titleLarge,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.captain_dropoff_changed_sub),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarrbiTheme.colors.surfaceVariant,
        ) {
            Text(
                text = address.takeIf { it.isNotBlank() } ?: stringResource(R.string.captain_location_point),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        DarrbiPrimaryButton(text = stringResource(R.string.captain_navigate), onClick = onNavigate)
    }
}

/** Dropping (in-trip) card: "Dropping {rider}", ETA, distance·arrival, End Ride (per the reference). */
@Composable
private fun DroppingContent(
    riderName: String?,
    distanceKm: Double?,
    etaMinutes: Int?,
    isCompleting: Boolean,
    onEndRide: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarrbiTheme.colors.surfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.captain_dropping, riderName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.captain_rider_fallback)),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = etaMinutes?.let { stringResource(R.string.captain_eta_mins, it) } ?: "—",
            style = DarrbiTheme.typography.titleLarge,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (distanceKm != null && etaMinutes != null) stringResource(R.string.captain_km_time, formatKm(distanceKm), arrivalClock(etaMinutes)) else "",
            style = DarrbiTheme.typography.label,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = !isCompleting, onClick = onEndRide),
            shape = RoundedCornerShape(14.dp),
            color = DarrbiTheme.colors.error,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.captain_end_ride), style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onError)
            }
        }
    }
}

/** Rate-your-rider card after completion (per the reference): stars, earning/payment, loyalty, submit. */
@Composable
private fun RateRiderContent(
    riderName: String?,
    riderImageUrl: String?,
    stars: Int,
    earning: Double,
    paymentMethod: Int,
    loyaltyKm: Double,
    isSubmitting: Boolean,
    onRate: (Int) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = riderImageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.user_placeholder),
                error = painterResource(R.drawable.user_placeholder),
                fallback = painterResource(R.drawable.user_placeholder),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.captain_rate_rider), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
                Text(
                    text = riderName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.captain_rider_fallback),
                    style = DarrbiTheme.typography.title.copy(fontSize = 18.sp),
                    color = DarrbiTheme.colors.onSurface,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        StarRatingRow(stars = stars, onRate = onRate)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DarrbiTheme.colors.outline)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            RequestStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.captain_total_earning),
                value = stringResource(R.string.captain_sar, formatAmount(earning)),
            )
            RequestStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.captain_payment_method),
                value = stringResource(if (paymentMethod == PAYMENT_METHOD_CARD) R.string.captain_payment_card else R.string.captain_payment_cash),
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DarrbiTheme.colors.outline)
        Spacer(Modifier.height(16.dp))
        RequestStat(
            label = stringResource(R.string.captain_loyalty_points),
            value = stringResource(R.string.captain_km_only, formatKm(loyaltyKm)),
        )
        Spacer(Modifier.height(18.dp))
        DarrbiPrimaryButton(
            text = stringResource(R.string.captain_submit_rating),
            onClick = onSubmit,
            enabled = !isSubmitting && stars >= 1,
        )
    }
}

/** Five tappable star circles (filled = orange, unrated = grey). */
@Composable
private fun StarRatingRow(stars: Int, onRate: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (1..5).forEach { i ->
            val filled = i <= stars
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(DarrbiTheme.colors.surfaceVariant).clickable { onRate(i) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (filled) DarrbiTheme.colors.warning else DarrbiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Open-request detail card (over the route map): rider, offered fare, distance·time, pickup/drop, then
 * "Accept for SAR X" + quick "Offer your fare" chips + Cancel (matches the reference).
 */
@Composable
private fun RequestDetailContent(
    trip: OpenTrip,
    isPlacing: Boolean,
    errorCode: String?,
    onAccept: () -> Unit,
    onOffer: (Double) -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Rider row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = trip.riderImageUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.user_placeholder),
                error = painterResource(R.drawable.user_placeholder),
                fallback = painterResource(R.drawable.user_placeholder),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.riderName ?: stringResource(R.string.captain_rider_fallback),
                    style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
                    color = DarrbiTheme.colors.onSurface,
                    maxLines = 1,
                )
                Text(relativeAge(trip.createdAtMillis), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            }
            trip.riderRating?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = DarrbiTheme.colors.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ratingLabel(rating, trip.riderTotalReviews), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = DarrbiTheme.colors.outline)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.captain_sar, formatAmount(trip.riderOfferedFare)),
                style = DarrbiTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(distanceTime(trip), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline)) {
            Column {
                AddressRow(stringResource(R.string.captain_pickup), trip.pickup.address, DarrbiTheme.colors.primary)
                HorizontalDivider(color = DarrbiTheme.colors.outline)
                AddressRow(stringResource(R.string.captain_drop_off), trip.dropoff.address, DarrbiTheme.colors.error)
            }
        }
        if (errorCode != null) {
            Spacer(Modifier.height(10.dp))
            InfoPill(text = bidErrorText(errorCode), error = true)
        }
        Spacer(Modifier.height(16.dp))
        DarrbiPrimaryButton(
            text = stringResource(R.string.captain_accept_fare, formatAmount(trip.riderOfferedFare)),
            onClick = onAccept,
            enabled = !isPlacing,
        )
        val chips = offerChips(trip.riderOfferedFare, trip.fareRange.max)
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.captain_offer_your_fare),
                style = DarrbiTheme.typography.title.copy(fontSize = 16.sp),
                color = DarrbiTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chips.forEach { amount ->
                    OfferChip(amount = amount, enabled = !isPlacing, onClick = { onOffer(amount.toDouble()) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = !isPlacing, onClick = onCancel),
            shape = RoundedCornerShape(14.dp),
            color = DarrbiTheme.colors.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.common_cancel), style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onSurface)
            }
        }
    }
}

@Composable
private fun OfferChip(amount: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.captain_sar, amount.toString()),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** Three quick higher-fare offers, rounded to the nearest 5 and clamped to the trip's max. */
private fun offerChips(offered: Double, max: Double): List<Int> =
    (1..3).map { (Math.round((offered + 5 * it) / 5.0) * 5).toInt() }
        .filter { it > offered.toInt() && it.toDouble() <= max }
        .distinct()

/** Maps a V2 bid error code to a friendly message. */
@Composable
private fun bidErrorText(code: String): String = when (code) {
    "BID_BELOW_FLOOR" -> stringResource(R.string.captain_bid_err_below)
    "BID_ABOVE_CEILING" -> stringResource(R.string.captain_bid_err_above)
    "DRIVER_INELIGIBLE" -> stringResource(R.string.captain_bid_err_ineligible)
    "DRIVER_OUT_OF_RANGE" -> stringResource(R.string.captain_bid_err_range_out)
    "TRIP_NOT_OPEN" -> stringResource(R.string.captain_bid_err_closed)
    else -> stringResource(R.string.captain_bid_err_generic)
}

private fun formatRating(rating: Double): String = String.format(java.util.Locale.US, "%.1f/5", rating)

/** Rating with the review count appended ("4.8 (36)") when reviews are known; bare rating otherwise. */
@Composable
private fun ratingLabel(rating: Double, reviews: Int?): String =
    if (reviews != null && reviews > 0) stringResource(R.string.captain_rating_reviews, formatRating(rating), reviews)
    else formatRating(rating)

private fun clockTime(millis: Long?): String? = millis?.let {
    runCatching { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it)) }.getOrNull()
}

/** "Just Now" when fresh (or unknown), else "N mins". */
@Composable
private fun relativeAge(millis: Long?): String {
    if (millis == null) return stringResource(R.string.captain_just_now)
    val mins = ((System.currentTimeMillis() - millis) / 60_000L).toInt()
    return if (mins <= 0) stringResource(R.string.captain_just_now) else stringResource(R.string.captain_mins_ago, mins)
}

/** "0.9 km • 3:09 pm" (or just the distance when the time is unknown). */
@Composable
private fun distanceTime(trip: OpenTrip): String {
    val km = formatKm(trip.tripDistanceKm)
    val clock = clockTime(trip.createdAtMillis)
    return if (clock != null) stringResource(R.string.captain_km_time, km, clock) else stringResource(R.string.captain_km_only, km)
}

/** Incoming ride request card: rider, estimated earning, payment, pickup/dest distance, accept/decline. */
@Composable
private fun RideRequestContent(
    request: RideRequest,
    pickupDistanceKm: Double?,
    pickupTimeMinutes: Int?,
    isHandling: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Rider row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = request.riderImageUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.user_placeholder),
                error = painterResource(R.drawable.user_placeholder),
                fallback = painterResource(R.drawable.user_placeholder),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(request.riderName, style = DarrbiTheme.typography.title.copy(fontSize = 16.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
                Text(stringResource(R.string.profile_status_good), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DarrbiTheme.colors.outline)
        Spacer(Modifier.height(16.dp))
        // Earning + payment method.
        Row(modifier = Modifier.fillMaxWidth()) {
            RequestStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.captain_estimate_earning),
                value = stringResource(R.string.captain_earning_sar, formatAmount(request.estimateEarning)),
            )
            RequestStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.captain_payment_method),
                value = stringResource(
                    if (request.paymentMethod == PAYMENT_METHOD_CARD) R.string.captain_payment_card else R.string.captain_payment_cash,
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DarrbiTheme.colors.outline)
        Spacer(Modifier.height(16.dp))
        // Pickup + destination distance/time.
        Row(modifier = Modifier.fillMaxWidth()) {
            RequestStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.captain_pickup_distance),
                value = if (pickupDistanceKm != null && pickupTimeMinutes != null) {
                    stringResource(R.string.captain_distance_time, formatKm(pickupDistanceKm), pickupTimeMinutes)
                } else {
                    "—"
                },
            )
            RequestStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.captain_dest_distance),
                value = stringResource(R.string.captain_distance_time, formatKm(request.destDistanceKm), request.destTimeMinutes.toInt()),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Decline (red).
            Surface(
                modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = !isHandling, onClick = onDecline),
                shape = RoundedCornerShape(14.dp),
                color = DarrbiTheme.colors.error,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.captain_decline), style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onError)
                }
            }
            // Accept (black/primary).
            DarrbiPrimaryButton(
                text = stringResource(R.string.captain_accept),
                onClick = onAccept,
                enabled = !isHandling,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A label-over-value cell used on the ride-request card. */
@Composable
private fun RequestStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = DarrbiTheme.typography.title.copy(fontSize = 16.sp), color = DarrbiTheme.colors.onSurface)
    }
}

/** Rounds an amount to ≤2 decimals, dropping a trailing ".0". */
private fun formatAmount(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/** Distance in km: whole number at ≥10 km, otherwise one decimal (e.g. "0.8", "143"). */
private fun formatKm(km: Double): String =
    if (km >= 10.0) Math.round(km).toString() else (Math.round(km * 10) / 10.0).toString()

private const val PAYMENT_METHOD_CARD = 1
private const val REQUEST_BOUNDS_PADDING_PX = 64

/** Navigate-to-rider card (after accept): rider + chat/call, ETA, distance·arrival, Navigate/Reached. */
@Composable
private fun NavigateRiderContent(
    trip: RideRequest,
    pickupDistanceKm: Double?,
    pickupTimeMinutes: Int?,
    navigateStarted: Boolean,
    isHandling: Boolean,
    onCall: () -> Unit,
    onChat: () -> Unit,
    onNavigate: () -> Unit,
    onReached: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = trip.riderImageUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.user_placeholder),
                error = painterResource(R.drawable.user_placeholder),
                fallback = painterResource(R.drawable.user_placeholder),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(trip.riderName, style = DarrbiTheme.typography.title.copy(fontSize = 16.sp), color = DarrbiTheme.colors.onSurface, maxLines = 1)
                Text(stringResource(R.string.profile_status_good), style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            }
            ContactCircle(iconRes = R.drawable.icon_chat, contentDescription = stringResource(R.string.cd_chat), onClick = onChat)
            Spacer(Modifier.width(12.dp))
            ContactCircle(iconRes = R.drawable.icon_phone, contentDescription = stringResource(R.string.cd_call), onClick = onCall)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DarrbiTheme.colors.outline)
        Spacer(Modifier.height(16.dp))
        Text(
            text = pickupTimeMinutes?.let { stringResource(R.string.captain_eta_mins, it) } ?: "—",
            style = DarrbiTheme.typography.titleLarge.copy(fontSize = 24.sp),
            color = DarrbiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.captain_pickup_eta,
                formatKm(pickupDistanceKm ?: 0.0),
                arrivalClock(pickupTimeMinutes ?: 0),
            ),
            style = DarrbiTheme.typography.body.copy(fontSize = 13.sp),
            color = DarrbiTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (navigateStarted) {
            DarrbiPrimaryButton(text = stringResource(R.string.captain_reached), onClick = onReached, enabled = !isHandling)
        } else {
            DarrbiPrimaryButton(text = stringResource(R.string.captain_navigate_to_rider), onClick = onNavigate, enabled = !isHandling)
        }
    }
}

/** Top-start 3-dot button that reveals a "Cancel Ride" popup while navigating to a rider. */
@Composable
private fun CaptainMenu(showMenu: Boolean, onToggle: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DarrbiTheme.colors.surface)
                .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.captain_menu), tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(24.dp))
        }
        if (showMenu) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = DarrbiTheme.colors.surface, shadowElevation = 6.dp) {
                Text(
                    text = stringResource(R.string.captain_cancel_ride),
                    style = DarrbiTheme.typography.bodyMedium,
                    color = DarrbiTheme.colors.error,
                    modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** Clock time [minutes] from now, formatted like "3:09pm" (arrival ETA). */
private fun arrivalClock(minutes: Int): String {
    val target = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, minutes) }.time
    return java.text.SimpleDateFormat("h:mma", java.util.Locale.ENGLISH).format(target).lowercase(java.util.Locale.ENGLISH)
}

/** Opens turn-by-turn navigation to [lat]/[lng] in Google Maps (falls back to a geo: query). */
private fun openNavigation(context: android.content.Context, lat: Double, lng: Double) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=$lat,$lng"))
            .setPackage("com.google.android.apps.maps")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Opens the phone dialer pre-filled with [number]. */
private fun dialPhone(context: android.content.Context, number: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")),
        )
    }
}

/** Light tonal pill used for the IBAN bank (green) / error (red) message under the field. */
@Composable
private fun InfoPill(text: String, error: Boolean) {
    val accent = if (error) DarrbiTheme.colors.error else DarrbiTheme.colors.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            style = DarrbiTheme.typography.label,
            color = accent,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Car illustration with the small orange "under review" clock badge. */
@Composable
private fun CarBadge() {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.image_car),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.45f),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(34.dp)
                .clip(CircleShape)
                .background(DarrbiTheme.colors.warning),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = DarrbiTheme.colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Groups the IBAN into blocks of 4 for display ("SA44 2000 0002 …") while keeping the raw value. */
private val IbanVisualTransformation = VisualTransformation { text ->
    val grouped = text.text.chunked(4).joinToString(" ")
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            offset + (offset - 1).coerceAtLeast(0) / 4
        override fun transformedToOriginal(offset: Int): Int =
            offset - offset / 5
    }
    TransformedText(AnnotatedString(grouped), mapping)
}
