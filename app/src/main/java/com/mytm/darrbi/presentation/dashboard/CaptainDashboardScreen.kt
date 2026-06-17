package com.mytm.darrbi.presentation.dashboard

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.RideRequest
import com.mytm.darrbi.presentation.common.LocationPermissionDeniedDialog
import com.mytm.darrbi.presentation.rider.AnimatedCarMarker
import com.mytm.darrbi.presentation.rider.ContactCircle
import com.mytm.darrbi.presentation.rider.rememberCarMarkerIcon
import com.mytm.darrbi.presentation.rider.rememberMarkerIcon
import com.mytm.darrbi.presentation.common.ModeToggle
import com.mytm.darrbi.presentation.common.RideMode
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
    onSwitchToRider: () -> Unit = {},
    modeSwitching: Boolean = false,
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
    // Accepted trip → frame the captain → pickup route.
    LaunchedEffect(state.activeTrip?.tripId, state.activeRoutePoints, state.myLocation) {
        val active = state.activeTrip ?: return@LaunchedEffect
        val pts = buildList {
            add(LatLng(active.pickup.latitude, active.pickup.longitude))
            state.myLocation?.let { add(LatLng(it.latitude, it.longitude)) }
            addAll(state.activeRoutePoints.map { LatLng(it.latitude, it.longitude) })
        }
        if (pts.size < 2) return@LaunchedEffect
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

    Box(modifier = Modifier.fillMaxSize()) {
        val request = state.incomingRequest
        val active = state.activeTrip
        val mapBusy = request != null || active != null
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
                state.myLocation?.let {
                    AnimatedCarMarker(target = LatLng(it.latitude, it.longitude), icon = carIcon, key = "req_driver")
                }
            } else if (active != null) {
                // Navigating to the rider → captain→pickup route + pickup pin + the captain's car.
                val route = remember(state.activeRoutePoints) {
                    state.activeRoutePoints.map { LatLng(it.latitude, it.longitude) }
                }
                if (route.size >= 2) Polyline(points = route, color = routeColor, width = 6f)
                Marker(
                    state = rememberMarkerState(key = "act_pickup", position = LatLng(active.pickup.latitude, active.pickup.longitude)),
                    icon = pinIcon, anchor = Offset(0.5f, 0.5f),
                )
                state.myLocation?.let {
                    AnimatedCarMarker(target = LatLng(it.latitude, it.longitude), icon = carIcon, key = "act_driver")
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

        // Top-start: RIDER/CAPTAIN toggle normally; the 3-dot menu (Cancel Ride) while navigating to a rider.
        if (active == null) {
            ModeToggle(
                selected = RideMode.Captain,
                onSelect = { if (it == RideMode.Rider) onSwitchToRider() },
                enabled = !modeSwitching,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
            )
        } else {
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
                    onChat = {
                        android.widget.Toast.makeText(navContext, navContext.getString(R.string.chat_media_coming_soon), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onNavigate = {
                        openNavigation(navContext, active.pickup.latitude, active.pickup.longitude)
                        viewModel.onEvent(CaptainDashboardEvent.StartNavigate)
                    },
                    onReached = { viewModel.onEvent(CaptainDashboardEvent.MarkReached) },
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
    // navigationBarPadding keeps the card content above the system nav bar (it was drawing behind it).
    DarrbiCard(
        modifier = Modifier.align(Alignment.BottomCenter),
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
