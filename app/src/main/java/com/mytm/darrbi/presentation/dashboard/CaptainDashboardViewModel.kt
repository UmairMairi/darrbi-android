package com.mytm.darrbi.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.BidType
import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.OngoingTrip
import com.mytm.darrbi.domain.model.OpenTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.RideRequest
import com.mytm.darrbi.domain.model.TripStage
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.repository.TripSocketEvent
import com.mytm.darrbi.domain.repository.V2SocketEvent
import com.mytm.darrbi.domain.usecase.AcceptTripUseCase
import com.mytm.darrbi.domain.usecase.CancelTripByDriverUseCase
import com.mytm.darrbi.domain.usecase.CurrentLocationUseCase
import com.mytm.darrbi.domain.usecase.GetCaptainDetailsUseCase
import com.mytm.darrbi.domain.usecase.GetOngoingTripUseCase
import com.mytm.darrbi.domain.usecase.GetOpenTripsUseCase
import com.mytm.darrbi.domain.usecase.GetRouteUseCase
import com.mytm.darrbi.domain.usecase.PlaceBidUseCase
import com.mytm.darrbi.domain.usecase.ReachedPickupUseCase
import com.mytm.darrbi.domain.usecase.RejectTripUseCase
import com.mytm.darrbi.domain.usecase.StartTripUseCase
import com.mytm.darrbi.domain.usecase.StreamLocationUpdatesUseCase
import com.mytm.darrbi.domain.usecase.ValidateIbanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which card the captain dashboard shows over the map, derived from `GET /captains`. */
enum class CaptainStage { Loading, UnderReview, Approved, EnterIban, NoRiders }

data class CaptainDashboardUiState(
    val stage: CaptainStage = CaptainStage.Loading,
    val captain: CaptainDetails? = null,
    /** Set when the captain taps "Start Now" on the approved card. */
    val started: Boolean = false,
    val ibanInput: String = "",
    val ibanVerifying: Boolean = false,
    val ibanError: Boolean = false,
    val bankName: String? = null,
    /** Device location, once the captain is set up and grants permission, used to centre the map. */
    val myLocation: PlaceLocation? = null,
    /** Incoming ride request to accept/decline (`trip_request`); null when none is pending. */
    val incomingRequest: RideRequest? = null,
    /** Pickup → destination route polyline for the incoming request. */
    val requestRoutePoints: List<LatLngPoint> = emptyList(),
    /** Captain → pickup distance (km) and time (minutes), computed from the captain's current location. */
    val requestPickupDistanceKm: Double? = null,
    val requestPickupTimeMinutes: Int? = null,
    /** Accept/decline request in flight. */
    val isHandlingRequest: Boolean = false,
    /** One-shot: a request was just accepted → the UI shows a confirmation once. */
    val tripAccepted: Boolean = false,
    /** The accepted trip the captain is heading to (navigate-to-rider screen); null when none. */
    val activeTrip: RideRequest? = null,
    /** Captain → pickup route polyline for the navigate-to-rider map. */
    val activeRoutePoints: List<LatLngPoint> = emptyList(),
    /** True once the captain tapped "Navigate to Rider" → the button becomes "Reached". */
    val navigateStarted: Boolean = false,
    /** At pickup (`driver_reached`) → show the OTP-entry overlay to start the trip. */
    val awaitingOtp: Boolean = false,
    val otpInput: String = "",
    val isStartingTrip: Boolean = false,
    val otpError: Boolean = false,
    /** One-shot: the trip just started after a correct OTP → the UI shows a confirmation once. */
    val tripStarted: Boolean = false,
    /** Whether the 3-dot menu (with Cancel Ride) is open. */
    val showMenu: Boolean = false,
    // --- V2 broadcast dispatch + bidding ---
    /** Online (accepting/showing broadcast trips) vs offline (banner). Verified → online by default. */
    val isOnline: Boolean = true,
    /** Live broadcast list of open (awaiting-bids) trips, from the socket. */
    val openTrips: List<OpenTrip> = emptyList(),
    /** The open trip whose detail view is showing (map + bid actions); null when closed. */
    val biddingTrip: OpenTrip? = null,
    /** Pickup → destination route polyline for the open-trip detail map. */
    val biddingRoutePoints: List<LatLngPoint> = emptyList(),
    /** A bid POST is in flight. */
    val isPlacingBid: Boolean = false,
    /** V2 bid error code to surface in the sheet (e.g. BID_BELOW_FLOOR, DRIVER_INELIGIBLE). */
    val bidErrorCode: String? = null,
    /** One-shot: you lost / the trip closed → reason code shown once. */
    val bidLostReason: String? = null,
    val errorMessage: String? = null,
) {
    val canSubmitIban: Boolean
        get() = !ibanVerifying && ibanInput.startsWith("SA", ignoreCase = true) && ibanInput.length == IBAN_LENGTH

    companion object {
        const val IBAN_LENGTH = 24 // Saudi IBAN: "SA" + 22 digits
    }
}

sealed interface CaptainDashboardEvent {
    data object StartNow : CaptainDashboardEvent
    data class IbanChanged(val value: String) : CaptainDashboardEvent
    data object SubmitIban : CaptainDashboardEvent
    /** Accept the incoming ride request. */
    data object AcceptRequest : CaptainDashboardEvent
    /** Decline the incoming ride request. */
    data object DeclineRequest : CaptainDashboardEvent
    /** "Navigate to Rider" tapped (external navigation launched by the screen). */
    data object StartNavigate : CaptainDashboardEvent
    /** "Reached" tapped on the navigate screen. */
    data object MarkReached : CaptainDashboardEvent
    /** OTP field edited on the start-trip overlay. */
    data class EnterOtp(val value: String) : CaptainDashboardEvent
    /** Submit the OTP to start the trip. */
    data object SubmitOtp : CaptainDashboardEvent
    /** Dismiss the OTP overlay (back to the navigate screen). */
    data object DismissOtp : CaptainDashboardEvent
    data object ConsumeTripStarted : CaptainDashboardEvent
    /** Toggle the 3-dot menu (Cancel Ride). */
    data object ToggleMenu : CaptainDashboardEvent
    /** Cancel the accepted trip from the 3-dot menu. */
    data object CancelTrip : CaptainDashboardEvent
    data object ConsumeError : CaptainDashboardEvent
    data object ConsumeAccepted : CaptainDashboardEvent
    // --- V2 ---
    /** Toggle online/offline. */
    data class SetOnline(val online: Boolean) : CaptainDashboardEvent
    /** Open the bid sheet for an open trip. */
    data class OpenBidSheet(val tripId: String) : CaptainDashboardEvent
    data object DismissBidSheet : CaptainDashboardEvent
    /** Bid by accepting the rider's offered fare as-is. */
    data class AcceptFare(val tripId: String) : CaptainDashboardEvent
    /** Bid with a counter price (must be within the trip's fare range). */
    data class CounterBid(val tripId: String, val fare: Double) : CaptainDashboardEvent
    data object ConsumeBidError : CaptainDashboardEvent
    data object ConsumeBidLost : CaptainDashboardEvent
}

@HiltViewModel
class CaptainDashboardViewModel @Inject constructor(
    private val getCaptainDetails: GetCaptainDetailsUseCase,
    private val validateIban: ValidateIbanUseCase,
    private val currentLocation: CurrentLocationUseCase,
    private val streamLocationUpdates: StreamLocationUpdatesUseCase,
    private val getRoute: GetRouteUseCase,
    private val acceptTrip: AcceptTripUseCase,
    private val rejectTrip: RejectTripUseCase,
    private val reachedPickup: ReachedPickupUseCase,
    private val startTripUseCase: StartTripUseCase,
    private val cancelTripByDriver: CancelTripByDriverUseCase,
    private val getOngoingTrip: GetOngoingTripUseCase,
    private val getOpenTrips: GetOpenTripsUseCase,
    private val placeBid: PlaceBidUseCase,
    private val socketService: SocketService,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptainDashboardUiState())
    val state: StateFlow<CaptainDashboardUiState> = _state.asStateFlow()

    /** Active location → socket streaming job (only while the verified captain is on the dashboard). */
    private var locationStreamJob: Job? = null

    /** Auto-expire the incoming request after a fixed window (matches ride-android's 18s countdown). */
    private var requestTimeoutJob: Job? = null

    init {
        refresh()
        // Listen for incoming ride requests on the shared socket (captain side; V1 fallback).
        viewModelScope.launch {
            socketService.tripEvents.collect { event ->
                if (event is TripSocketEvent.TripRequest) onTripRequest(event.request)
            }
        }
        // V2: mirror the broadcast open-trips list into state (shown when online).
        viewModelScope.launch {
            socketService.openTrips.collect { trips ->
                _state.update { st ->
                    val sorted = trips.sortedByDescending { it.createdAtMillis ?: 0L }
                    st.copy(
                        openTrips = sorted,
                        // Keep the open bid sheet's trip in sync (or close it if the trip vanished).
                        biddingTrip = st.biddingTrip?.let { open -> sorted.firstOrNull { it.tripId == open.tripId } },
                    )
                }
            }
        }
        // V2: bid outcomes — won → assigned/navigate; lost/closed → drop + notify.
        viewModelScope.launch {
            socketService.v2Events.collect { event ->
                when (event) {
                    is V2SocketEvent.BidWon -> onBidWon()
                    is V2SocketEvent.BidLost -> _state.update { it.copy(bidLostReason = event.reason ?: REASON_LOST) }
                    is V2SocketEvent.TripClosed -> _state.update { it.copy(bidLostReason = event.reason ?: REASON_CLOSED) }
                    else -> Unit // rider-facing events
                }
            }
        }
    }

    /**
     * Start streaming the captain's location to the server over the socket. Called once the captain is
     * verified (NoRiders) and location permission is granted; idempotent. Each fused-location update is
     * emitted via `update-captain-location` (mirrors ride-android's continuous driver-location updates).
     */
    fun startLocationStreaming() {
        if (locationStreamJob?.isActive == true) return
        socketService.connect()
        locationStreamJob = viewModelScope.launch {
            streamLocationUpdates().collect { point ->
                socketService.updateCaptainLocation(point.latitude, point.longitude)
            }
        }
    }

    /** Centre the map on the device location (called once permission is granted on the ready dashboard). */
    fun locateMe() {
        viewModelScope.launch {
            when (val result = currentLocation()) {
                is ApiResult.Success -> {
                    val hadLocation = _state.value.myLocation != null
                    _state.update { it.copy(myLocation = result.data) }
                    // First fix while online → pull the open-trips snapshot now that we have coords.
                    if (!hadLocation && _state.value.isOnline && _state.value.stage == CaptainStage.NoRiders) fetchOpenTrips()
                }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    fun onEvent(event: CaptainDashboardEvent) {
        when (event) {
            CaptainDashboardEvent.StartNow -> {
                val stage = stageFor(_state.value.captain, started = true)
                _state.update { it.copy(started = true, stage = stage) }
                connectSocketIfReady(stage)
            }
            is CaptainDashboardEvent.IbanChanged -> _state.update {
                // Keep the raw IBAN (uppercased, no spaces); the field formats it for display.
                it.copy(
                    ibanInput = event.value.filter { c -> !c.isWhitespace() }.uppercase()
                        .take(CaptainDashboardUiState.IBAN_LENGTH),
                    ibanError = false,
                    bankName = null,
                )
            }
            CaptainDashboardEvent.SubmitIban -> submitIban()
            CaptainDashboardEvent.AcceptRequest -> acceptRequest()
            CaptainDashboardEvent.DeclineRequest -> declineRequest()
            CaptainDashboardEvent.StartNavigate -> _state.update { it.copy(navigateStarted = true) }
            CaptainDashboardEvent.MarkReached -> markReached()
            is CaptainDashboardEvent.EnterOtp -> _state.update { it.copy(otpInput = event.value.filter { c -> c.isDigit() }.take(OTP_MAX_LEN), otpError = false) }
            CaptainDashboardEvent.SubmitOtp -> submitOtp()
            CaptainDashboardEvent.DismissOtp -> _state.update { it.copy(awaitingOtp = false, otpInput = "", otpError = false) }
            CaptainDashboardEvent.ConsumeTripStarted -> _state.update { it.copy(tripStarted = false) }
            CaptainDashboardEvent.ToggleMenu -> _state.update { it.copy(showMenu = !it.showMenu) }
            CaptainDashboardEvent.CancelTrip -> cancelTrip()
            CaptainDashboardEvent.ConsumeError -> _state.update { it.copy(errorMessage = null) }
            CaptainDashboardEvent.ConsumeAccepted -> _state.update { it.copy(tripAccepted = false) }
            is CaptainDashboardEvent.SetOnline -> setOnline(event.online)
            is CaptainDashboardEvent.OpenBidSheet -> openBidDetail(event.tripId)
            CaptainDashboardEvent.DismissBidSheet -> _state.update { it.copy(biddingTrip = null, bidErrorCode = null, biddingRoutePoints = emptyList()) }
            is CaptainDashboardEvent.AcceptFare -> submitBid(event.tripId, BidType.AcceptFare, null)
            is CaptainDashboardEvent.CounterBid -> submitBid(event.tripId, BidType.Counter, event.fare)
            CaptainDashboardEvent.ConsumeBidError -> _state.update { it.copy(bidErrorCode = null) }
            CaptainDashboardEvent.ConsumeBidLost -> _state.update { it.copy(bidLostReason = null) }
        }
    }

    /** Online ⇄ offline. Going online (re)pulls the open-trips snapshot; the socket keeps it live. */
    private fun setOnline(online: Boolean) {
        _state.update { it.copy(isOnline = online) }
        if (online) {
            socketService.connect()
            fetchOpenTrips()
        }
    }

    /** Initial open-trips pull (the socket's `open-trips-list` keeps it fresh afterwards). */
    private fun fetchOpenTrips() {
        val cabId = _state.value.captain?.cabId ?: return
        val loc = _state.value.myLocation ?: return
        viewModelScope.launch {
            when (val result = getOpenTrips(cabId, loc.latitude, loc.longitude)) {
                is ApiResult.Success -> _state.update {
                    // Merge: keep any socket-delivered trips, prefer the fresh snapshot.
                    val merged = (result.data + it.openTrips).distinctBy { t -> t.tripId }
                        .sortedByDescending { t -> t.createdAtMillis ?: 0L }
                    it.copy(openTrips = merged)
                }
                is ApiResult.Error, is ApiResult.Failure -> Unit // socket list still applies
            }
        }
    }

    /** Tapped a request card → open its detail view and fetch the pickup → destination route for the map. */
    private fun openBidDetail(tripId: String) {
        val trip = _state.value.openTrips.firstOrNull { it.tripId == tripId } ?: return
        _state.update { it.copy(biddingTrip = trip, bidErrorCode = null, biddingRoutePoints = emptyList()) }
        viewModelScope.launch {
            val points = when (val result = getRoute(trip.pickup, trip.dropoff)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error, is ApiResult.Failure -> emptyList()
            }
            val path = points.ifEmpty {
                listOf(
                    LatLngPoint(trip.pickup.latitude, trip.pickup.longitude),
                    LatLngPoint(trip.dropoff.latitude, trip.dropoff.longitude),
                )
            }
            _state.update { if (it.biddingTrip?.tripId == trip.tripId) it.copy(biddingRoutePoints = path) else it }
        }
    }

    /** Place a bid (ACCEPT the fare or COUNTER). Surfaces the V2 error code on failure. */
    private fun submitBid(tripId: String, bidType: BidType, fare: Double?) {
        if (_state.value.isPlacingBid) return
        _state.update { it.copy(isPlacingBid = true, bidErrorCode = null) }
        viewModelScope.launch {
            val result = placeBid(
                tripId = tripId,
                bidType = bidType,
                bidFare = fare,
                cabId = _state.value.captain?.cabId,
            )
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(isPlacingBid = false, biddingTrip = null, biddingRoutePoints = emptyList()) }
                is ApiResult.Error -> _state.update { it.copy(isPlacingBid = false, bidErrorCode = result.message ?: REASON_BID_FAILED) }
                is ApiResult.Failure -> _state.update { it.copy(isPlacingBid = false, bidErrorCode = result.error.message ?: REASON_BID_FAILED) }
            }
        }
    }

    /** Won the trip → fetch the assigned trip (`trips/exists` → `trips/socket/{id}`) and navigate to rider. */
    private fun onBidWon() {
        _state.update { it.copy(biddingTrip = null) }
        viewModelScope.launch {
            when (val result = getOngoingTrip()) {
                is ApiResult.Success -> result.data?.let { restoreOngoing(it) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    /** A ride request arrived → compute the captain→pickup estimate, draw the route, and start the timer. */
    private fun onTripRequest(request: RideRequest) {
        // Only while online/idle on the dashboard, and don't replace one already showing.
        if (_state.value.stage != CaptainStage.NoRiders || _state.value.incomingRequest != null) return
        val pickup = computePickup(request)
        _state.update {
            it.copy(
                incomingRequest = request,
                requestRoutePoints = emptyList(),
                requestPickupDistanceKm = pickup?.first,
                requestPickupTimeMinutes = pickup?.second,
                isHandlingRequest = false,
            )
        }
        fetchRequestRoute(request)
        requestTimeoutJob?.cancel()
        requestTimeoutJob = viewModelScope.launch {
            delay(REQUEST_TIMEOUT_MS)
            // Timed out → just dismiss (no reject call), like ride-android returning to the waiting state.
            clearRequest()
        }
    }

    /** Captain → pickup distance (km) + a rough ETA (minutes) from the captain's current location. */
    private fun computePickup(request: RideRequest): Pair<Double, Int>? {
        val me = _state.value.myLocation ?: return null
        val out = FloatArray(1)
        android.location.Location.distanceBetween(me.latitude, me.longitude, request.pickup.latitude, request.pickup.longitude, out)
        val km = out[0] / 1000.0
        val mins = Math.ceil(km / PICKUP_AVG_SPEED_KMH * 60.0).toInt().coerceAtLeast(1)
        return km to mins
    }

    private fun fetchRequestRoute(request: RideRequest) {
        viewModelScope.launch {
            val points = when (val result = getRoute(request.pickup, request.destination)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error, is ApiResult.Failure -> emptyList()
            }
            val path = points.ifEmpty {
                listOf(
                    LatLngPoint(request.pickup.latitude, request.pickup.longitude),
                    LatLngPoint(request.destination.latitude, request.destination.longitude),
                )
            }
            // Only apply if this is still the request being shown.
            _state.update { if (it.incomingRequest?.tripId == request.tripId) it.copy(requestRoutePoints = path) else it }
        }
    }

    private fun acceptRequest() {
        val request = _state.value.incomingRequest ?: return
        if (_state.value.isHandlingRequest) return
        _state.update { it.copy(isHandlingRequest = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = acceptTrip(request.tripId)) {
                is ApiResult.Success -> {
                    requestTimeoutJob?.cancel()
                    // Move to the navigate-to-rider screen for the accepted trip.
                    _state.update {
                        it.copy(
                            incomingRequest = null,
                            requestRoutePoints = emptyList(),
                            isHandlingRequest = false,
                            activeTrip = request,
                            navigateStarted = false,
                            showMenu = false,
                        )
                    }
                    fetchActiveRoute(request)
                }
                is ApiResult.Error -> _state.update { it.copy(isHandlingRequest = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isHandlingRequest = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun declineRequest() {
        val request = _state.value.incomingRequest ?: return
        requestTimeoutJob?.cancel()
        // Dismiss immediately; tell the server in the background.
        _state.update { it.copy(incomingRequest = null, requestRoutePoints = emptyList(), isHandlingRequest = false) }
        viewModelScope.launch { rejectTrip(request.tripId, request.destination) }
    }

    private fun clearRequest() {
        requestTimeoutJob?.cancel()
        _state.update { it.copy(incomingRequest = null, requestRoutePoints = emptyList(), isHandlingRequest = false) }
    }

    /** Draws the captain → pickup route on the navigate-to-rider map, and refreshes the pickup estimate. */
    private fun fetchActiveRoute(request: RideRequest) {
        computePickup(request)?.let { (km, mins) ->
            _state.update { it.copy(requestPickupDistanceKm = km, requestPickupTimeMinutes = mins) }
        }
        val origin = _state.value.myLocation ?: return
        viewModelScope.launch {
            val points = when (val result = getRoute(origin, request.pickup)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error, is ApiResult.Failure -> emptyList()
            }
            val path = points.ifEmpty {
                listOf(
                    LatLngPoint(origin.latitude, origin.longitude),
                    LatLngPoint(request.pickup.latitude, request.pickup.longitude),
                )
            }
            _state.update { if (it.activeTrip?.tripId == request.tripId) it.copy(activeRoutePoints = path) else it }
        }
    }

    /** "Reached" → tell the server the captain is at pickup; on success show the OTP-entry overlay. */
    private fun markReached() {
        val trip = _state.value.activeTrip ?: return
        if (_state.value.isHandlingRequest) return
        _state.update { it.copy(isHandlingRequest = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = reachedPickup(trip.tripId)) {
                is ApiResult.Success -> _state.update { it.copy(isHandlingRequest = false, awaitingOtp = true, otpInput = "", otpError = false) }
                is ApiResult.Error -> _state.update { it.copy(isHandlingRequest = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isHandlingRequest = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Submit the rider's OTP to start the trip (`PATCH trips/started/{id}`). */
    private fun submitOtp() {
        val trip = _state.value.activeTrip ?: return
        val otp = _state.value.otpInput.toIntOrNull()
        if (otp == null || _state.value.isStartingTrip) {
            _state.update { it.copy(otpError = true) }
            return
        }
        _state.update { it.copy(isStartingTrip = true, otpError = false, errorMessage = null) }
        viewModelScope.launch {
            when (val result = startTripUseCase(trip.tripId, otp)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isStartingTrip = false, tripStarted = true) }
                    clearActiveTrip()
                }
                is ApiResult.Error -> _state.update { it.copy(isStartingTrip = false, otpError = true) }
                is ApiResult.Failure -> _state.update { it.copy(isStartingTrip = false, otpError = true) }
            }
        }
    }

    /** Cancel Ride (3-dot menu) → cancel server-side and return to the idle dashboard. */
    private fun cancelTrip() {
        val trip = _state.value.activeTrip ?: return
        _state.update { it.copy(showMenu = false) }
        viewModelScope.launch { cancelTripByDriver(trip.tripId, trip.destination) }
        clearActiveTrip()
    }

    private fun clearActiveTrip() {
        _state.update {
            it.copy(
                activeTrip = null,
                activeRoutePoints = emptyList(),
                navigateStarted = false,
                showMenu = false,
                isHandlingRequest = false,
                requestPickupDistanceKm = null,
                requestPickupTimeMinutes = null,
                awaitingOtp = false,
                otpInput = "",
                otpError = false,
                isStartingTrip = false,
            )
        }
    }

    /** Fetches `GET /captains` (cached into the session by the use case) and recomputes the stage. */
    private fun refresh() {
        _state.update { it.copy(stage = CaptainStage.Loading) }
        viewModelScope.launch {
            when (val result = getCaptainDetails()) {
                is ApiResult.Success -> {
                    val stage = stageFor(result.data, _state.value.started)
                    _state.update { it.copy(captain = result.data, stage = stage) }
                    // All checks passed (WASL approved + IBAN set → NoRiders) → open the socket.
                    connectSocketIfReady(stage)
                }
                // No captain yet / fetch failed → treat as still under review.
                is ApiResult.Error ->
                    _state.update { it.copy(captain = null, stage = stageFor(null, it.started)) }
                is ApiResult.Failure ->
                    _state.update { it.copy(stage = stageFor(it.captain, it.started), errorMessage = result.error.message) }
            }
        }
    }

    private fun submitIban() {
        val current = _state.value
        if (!current.canSubmitIban) return
        _state.update { it.copy(ibanVerifying = true, ibanError = false, bankName = null) }
        viewModelScope.launch {
            when (val result = validateIban(current.ibanInput)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(ibanVerifying = false, bankName = result.data.bank, ibanError = false) }
                    // IBAN accepted → re-fetch captain details and re-run all checks (matches ride-android).
                    refresh()
                }
                is ApiResult.Error -> _state.update { it.copy(ibanVerifying = false, ibanError = true) }
                is ApiResult.Failure -> _state.update { it.copy(ibanVerifying = false, ibanError = true) }
            }
        }
    }

    /**
     * Captain dashboard stage rules:
     * - WASL not approved (isWASLApproved != 1) → under review.
     * - WASL approved & IBAN already saved → "no riders around" directly.
     * - WASL approved & no IBAN → "Application Approved" (Start Now) first, then add IBAN.
     */
    private fun stageFor(captain: CaptainDetails?, started: Boolean): CaptainStage = when {
        captain?.isWaslApproved != 1 -> CaptainStage.UnderReview
        !captain.iban.isNullOrBlank() -> CaptainStage.NoRiders
        !started -> CaptainStage.Approved
        else -> CaptainStage.EnterIban
    }

    /** Connect the real-time socket only when the captain is fully verified (WASL approved + IBAN set). */
    private fun connectSocketIfReady(stage: CaptainStage) {
        if (stage == CaptainStage.NoRiders) {
            socketService.connect()
            checkOngoingTrip()
            // Verified → online by default; pull the open-trips snapshot (socket keeps it live).
            if (_state.value.isOnline) fetchOpenTrips()
        }
    }

    /** On dashboard entry, restore an in-progress accepted trip (`trips/exists` → `trips/socket/{id}`). */
    private var ongoingChecked = false
    private fun checkOngoingTrip() {
        if (ongoingChecked) return
        ongoingChecked = true
        viewModelScope.launch {
            when (val result = getOngoingTrip()) {
                is ApiResult.Success -> result.data?.let { restoreOngoing(it) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    /** Restores the captain to the navigate-to-rider screen for an accepted/arrived/in-progress trip. */
    private fun restoreOngoing(trip: OngoingTrip) {
        if (_state.value.activeTrip != null || _state.value.incomingRequest != null) return
        val request = trip.rideRequest ?: return
        when (trip.stage) {
            TripStage.DriverAssigned, TripStage.DriverArrived, TripStage.InProgress -> {
                _state.update {
                    it.copy(
                        activeTrip = request,
                        navigateStarted = trip.stage != TripStage.DriverAssigned,
                        // Restored at `driver_reached` → prompt for the rider's OTP to start the trip.
                        awaitingOtp = trip.stage == TripStage.DriverArrived,
                        otpInput = "",
                        otpError = false,
                        showMenu = false,
                    )
                }
                fetchActiveRoute(request)
            }
            else -> Unit
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 18_000L
        const val PICKUP_AVG_SPEED_KMH = 20.0
        const val REASON_LOST = "LOST"
        const val REASON_CLOSED = "CLOSED"
        const val REASON_BID_FAILED = "BID_FAILED"
        const val OTP_MAX_LEN = 6
    }
}
