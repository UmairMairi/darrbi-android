package com.mytm.darrbi.presentation.rider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.EnvConfig
import com.mytm.darrbi.domain.model.AcceptedTrip
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.Bid
import com.mytm.darrbi.domain.model.BidTrip
import com.mytm.darrbi.domain.model.BookedTrip
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.DropChangeQuote
import com.mytm.darrbi.domain.model.FareRange
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.OngoingTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.domain.model.RecentLocation
import com.mytm.darrbi.domain.model.RideCategory
import com.mytm.darrbi.domain.model.TripStage
import com.mytm.darrbi.domain.repository.NearbyDriver
import com.mytm.darrbi.domain.repository.SessionRepository
import com.mytm.darrbi.domain.repository.SocketConnectionState
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.repository.TripSocketEvent
import com.mytm.darrbi.domain.repository.V2SocketEvent
import com.mytm.darrbi.domain.usecase.AutocompletePlacesUseCase
import com.mytm.darrbi.domain.usecase.CancelOpenTripUseCase
import com.mytm.darrbi.domain.usecase.CancelTripUseCase
import com.mytm.darrbi.domain.usecase.ChangeDestinationUseCase
import com.mytm.darrbi.domain.usecase.CreateBidTripUseCase
import com.mytm.darrbi.domain.usecase.CreateTripUseCase
import com.mytm.darrbi.domain.usecase.EstimateDropChangeUseCase
import com.mytm.darrbi.domain.usecase.RaiseOfferUseCase
import com.mytm.darrbi.domain.usecase.RejectBidUseCase
import com.mytm.darrbi.domain.usecase.SelectBidUseCase
import com.mytm.darrbi.domain.usecase.CurrentLocationUseCase
import com.mytm.darrbi.domain.usecase.GetBalanceUseCase
import com.mytm.darrbi.domain.usecase.GetOngoingTripUseCase
import com.mytm.darrbi.domain.usecase.GetRecentAddressesUseCase
import com.mytm.darrbi.domain.usecase.GetTripBidsUseCase
import com.mytm.darrbi.domain.usecase.GetRideCategoriesUseCase
import com.mytm.darrbi.domain.usecase.GetRouteUseCase
import com.mytm.darrbi.domain.usecase.GetCabTypesUseCase
import com.mytm.darrbi.domain.usecase.PlaceDetailsUseCase
import com.mytm.darrbi.domain.usecase.RateDriverUseCase
import com.mytm.darrbi.domain.usecase.ReverseGeocodeUseCase
import com.mytm.darrbi.domain.usecase.ValidatePromoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Steps of the rider's booking flow: location selection → ride selection → searching for a captain. */
enum class RiderStep { Home, DestinationSearch, PickupSearch, MapPicker, ConfirmPickup, SelectRide, ProposeFare, Bidding, Searching, DriverOnWay, DriverArrived, TripStarted, ChangeDropSearch, ChangeDropConfirm, TripCompleted }

data class RiderBookingUiState(
    val step: RiderStep = RiderStep.Home,
    // Home dashboard: greeting name, API service categories (+ loading), recent quick-picks, and the
    // category the booking flow proceeds with (remembered when a tile or the "Where to?" bar is tapped).
    val userName: String? = null,
    val userImageUrl: String? = null,
    val categories: List<RideCategory> = emptyList(),
    val isLoadingCategories: Boolean = false,
    val recentLocations: List<RecentLocation> = emptyList(),
    val isLoadingRecents: Boolean = false,
    val selectedCategory: RideCategory? = null,
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val destination: PlaceLocation? = null,
    val pickup: PlaceLocation? = null,
    val isResolving: Boolean = false,
    /** Which search step opened the map picker, so the dropped pin sets the right place. */
    val mapPickerOrigin: RiderStep = RiderStep.DestinationSearch,
    // Ride selection
    val cabs: List<CabOption> = emptyList(),
    val selectedCabId: String? = null,
    val isLoadingCabs: Boolean = false,
    val balance: Double? = null,
    val promo: AppliedPromo? = null,
    val isPromoLoading: Boolean = false,
    val isBooking: Boolean = false,
    val bookedTrip: BookedTrip? = null,
    /** Set when the server reports no available captain (socket `no_drivers`) during [RiderStep.Searching]. */
    val noCaptainFound: Boolean = false,
    /** Cancel-trip request in flight (Cancel Ride tapped during an active search). */
    val isCancelling: Boolean = false,
    /** Device location, once permission is granted, used to centre the map. */
    val myLocation: PlaceLocation? = null,
    /** Decoded route polyline (pickup → destination) drawn on the confirm-pickup / select-ride maps. */
    val routePoints: List<LatLngPoint> = emptyList(),
    /** Nearby drivers (captains) from the `find-drivers` socket event, shown as car markers. */
    val nearbyDrivers: List<NearbyDriver> = emptyList(),
    /** The accepted trip (captain on the way) — drives the on-the-way fragment. */
    val acceptedTrip: AcceptedTrip? = null,
    /** Assigned captain's live location while on the way (from `driver-location-updates`). */
    val driverLocation: LatLngPoint? = null,
    /** Pickup → captain route polyline shown while the captain is on the way. */
    val driverRoutePoints: List<LatLngPoint> = emptyList(),
    /** Epoch-ms the captain reached pickup (`driver_reached`) — drives the "arrived" wait timer. */
    val arrivedAtMillis: Long? = null,
    /** Tentative new drop-off being chosen during the change-drop flow (committed only on confirm). */
    val changeDropDestination: PlaceLocation? = null,
    /** Re-quoted fare for [changeDropDestination] (new fare + arrival), shown on the change-drop confirm. */
    val changeDropQuote: DropChangeQuote? = null,
    val isChangingDrop: Boolean = false,
    /** Selected star rating (1..5) on the completed-trip screen; 0 = none yet (Submit disabled). */
    val rating: Int = 0,
    val isSubmittingRating: Boolean = false,
    /** Whether the in-trip Help sheet is open (over the trip map). */
    val showHelp: Boolean = false,
    /** One-shot: the drop-off was just changed → the UI shows the localized success banner once. */
    val dropChangeSucceeded: Boolean = false,
    /** One-shot: the captain cancelled the trip → the UI shows a localized notice once. */
    val cancelledByDriver: Boolean = false,
    // --- V2 bidding ---
    /** The rider's proposed fare on the ProposeFare step (defaults to the selected cab's recommended fare). */
    val offeredFare: Double? = null,
    /** The created BID trip (open trip id + authoritative fare range); set after `POST /v2/trips`. */
    val bidTrip: BidTrip? = null,
    /** Live competing bids for the open trip, cheapest-first (from `v2/trip-bids-update`). */
    val bids: List<Bid> = emptyList(),
    /** A BID trip create is in flight. */
    val isCreatingBidTrip: Boolean = false,
    /** A select/raise/cancel action is in flight. */
    val isBidActionInFlight: Boolean = false,
    /** One-shot notice on the bidding screen (no-bids / window timed out). */
    val bidNotice: String? = null,
    val errorMessage: String? = null,
) {
    val selectedCab: CabOption? get() = cabs.firstOrNull { it.id == selectedCabId }
    /** Fare after any applied promo discount (never below zero). */
    val payableFare: Double get() = ((selectedCab?.fare ?: 0.0) - (promo?.discount ?: 0.0)).coerceAtLeast(0.0)
    /** A zero (or not-yet-loaded) wallet balance below the fare means the rider must top up first. */
    val isBalanceInsufficient: Boolean get() = selectedCab != null && (balance ?: 0.0) < payableFare
    /** Extra to pay when changing the drop-off: new fare − original fare (never below zero). */
    val changeDropRemaining: Double get() = ((changeDropQuote?.newFare ?: 0.0) - (acceptedTrip?.originalFare ?: 0.0)).coerceAtLeast(0.0)
}

sealed interface RiderBookingEvent {
    data object OpenDestinationSearch : RiderBookingEvent
    /** A home category tile was tapped → remember it, then continue the booking flow. */
    data class OpenCategory(val category: RideCategory) : RiderBookingEvent
    /** A recent home address was tapped → use it as the destination and continue. */
    data class SelectRecent(val location: RecentLocation) : RiderBookingEvent
    /** Retry loading the home categories after a failure. */
    data object RetryCategories : RiderBookingEvent
    data class QueryChanged(val value: String) : RiderBookingEvent
    data class SelectSuggestion(val suggestion: PlaceSuggestion) : RiderBookingEvent
    data object UseCurrentLocation : RiderBookingEvent
    /** Permission granted on the home map → centre on the device location. */
    data object LocateMe : RiderBookingEvent
    data object OpenMapPicker : RiderBookingEvent
    data class ConfirmMapLocation(val latitude: Double, val longitude: Double) : RiderBookingEvent
    data object EditPickup : RiderBookingEvent
    data object ProceedToRideSelection : RiderBookingEvent
    data class SelectCab(val cabId: String) : RiderBookingEvent
    data class ApplyPromo(val code: String) : RiderBookingEvent
    data object RemovePromo : RiderBookingEvent
    data object ConfirmRide : RiderBookingEvent
    /** V2: submit the proposed fare → create a BID trip. */
    data class SubmitOffer(val fare: Double) : RiderBookingEvent
    /** V2: select a competing bid (commit the match). */
    data class SelectBid(val bidId: String) : RiderBookingEvent
    /** V2: dismiss a single competing bid. */
    data class RejectBid(val bidId: String) : RiderBookingEvent
    /** V2: raise the offered fare to attract more/faster bids. */
    data class RaiseOffer(val fare: Double) : RiderBookingEvent
    /** V2: cancel the open (awaiting-bids) request. */
    data object CancelBidding : RiderBookingEvent
    data object ConsumeBidNotice : RiderBookingEvent
    /** Re-fetch the wallet balance (e.g. after a successful top-up). */
    data object RefreshBalance : RiderBookingEvent
    /** Retry after no captain was found — returns to ride selection to re-request (ride-android: back). */
    data object TryAgainSearch : RiderBookingEvent
    /** Cancel Ride on the searching screen — cancels the active request server-side, then goes home. */
    data object CancelRide : RiderBookingEvent
    /** "I am coming" on the captain-arrived screen — returns to the on-the-way (ride-accepted) view. */
    data object ImComing : RiderBookingEvent
    /** "Change" on the in-trip screen — opens the change-drop-off search. */
    data object OpenChangeDrop : RiderBookingEvent
    /** "Pay Remaining" on the change-drop confirm — commits the new drop-off. */
    data object ConfirmChangeDrop : RiderBookingEvent
    /** "Help" on the in-trip screen — opens / closes the help sheet. */
    data object OpenHelp : RiderBookingEvent
    data object CloseHelp : RiderBookingEvent
    /** Star tapped on the completed-trip rating screen (1..5). */
    data class SelectRating(val stars: Int) : RiderBookingEvent
    /** "Submit Rating" on the completed-trip screen. */
    data object SubmitRating : RiderBookingEvent
    data object Back : RiderBookingEvent
    data object ConsumeError : RiderBookingEvent
    data object ConsumeInfo : RiderBookingEvent
}

private const val SEARCH_DEBOUNCE_MS = 300L

@HiltViewModel
class RiderBookingViewModel @Inject constructor(
    private val autocomplete: AutocompletePlacesUseCase,
    private val placeDetails: PlaceDetailsUseCase,
    private val currentLocation: CurrentLocationUseCase,
    private val reverseGeocode: ReverseGeocodeUseCase,
    private val getCabTypes: GetCabTypesUseCase,
    private val validatePromo: ValidatePromoUseCase,
    private val createTrip: CreateTripUseCase,
    private val cancelTrip: CancelTripUseCase,
    private val getBalance: GetBalanceUseCase,
    private val getRoute: GetRouteUseCase,
    private val getOngoingTrip: GetOngoingTripUseCase,
    private val estimateDropChange: EstimateDropChangeUseCase,
    private val changeDestination: ChangeDestinationUseCase,
    private val rateDriver: RateDriverUseCase,
    private val getRideCategories: GetRideCategoriesUseCase,
    private val getRecentAddresses: GetRecentAddressesUseCase,
    private val createBidTrip: CreateBidTripUseCase,
    private val getTripBids: GetTripBidsUseCase,
    private val selectBidUseCase: SelectBidUseCase,
    private val rejectBidUseCase: RejectBidUseCase,
    private val raiseOfferUseCase: RaiseOfferUseCase,
    private val cancelOpenTrip: CancelOpenTripUseCase,
    private val session: SessionRepository,
    private val env: EnvConfig,
    private val socketService: SocketService,
) : ViewModel() {

    private val _state = MutableStateFlow(RiderBookingUiState())
    val state: StateFlow<RiderBookingUiState> = _state.asStateFlow()

    init {
        // Home greeting name + avatar (from the verify-OTP profile cached in the session).
        _state.update {
            it.copy(
                userName = session.user?.name?.takeIf { name -> name.isNotBlank() },
                userImageUrl = session.user?.profileImageUrl?.takeIf { url -> url.isNotBlank() },
            )
        }
        // Load the home service categories + recent quick-picks for the dashboard.
        loadCategories()
        loadRecents()
        // Rider reached the dashboard → open the real-time socket (connect + subscribe-user).
        socketService.connect()
        // Show nearby captains from the `find-drivers` socket response on the map.
        viewModelScope.launch {
            socketService.nearbyDrivers.collect { drivers -> _state.update { it.copy(nearbyDrivers = drivers) } }
        }
        // Request nearby drivers once the socket is connected (so it follows subscribe-user), using the
        // known rider location. locateMe() also requests after a fresh location fix.
        viewModelScope.launch {
            socketService.connectionState.collect { connState ->
                if (connState == SocketConnectionState.Connected) {
                    _state.value.myLocation?.let { socketService.findDrivers(it.latitude, it.longitude) }
                }
            }
        }
        // While searching, a `no_drivers` (no captain accepted) or `trip_expired` push ends the search →
        // show the "try again" state (same UI as ride-android, which reuses the no-captain screen).
        viewModelScope.launch {
            socketService.tripEvents.collect { event ->
                when (event) {
                    TripSocketEvent.NoCaptainFound, TripSocketEvent.TripExpired -> failSearch()
                    is TripSocketEvent.DriverAccepted -> onDriverAccepted(event.trip)
                    is TripSocketEvent.DriverArrived -> onDriverArrived(event.trip)
                    is TripSocketEvent.TripStarted -> onTripStarted(event.trip)
                    is TripSocketEvent.TripCompleted -> onTripCompleted(event.trip)
                    TripSocketEvent.DriverCancelled -> onDriverCancelled()
                    is TripSocketEvent.TripRequest -> Unit // captain-side event; ignored on the rider
                }
            }
        }
        // While the captain is on the way, follow their live location. Only the captain's MARKER moves
        // (the map animates it from the previous point with bearing); the pickup→captain route is drawn
        // once on accept and NOT recreated on every location tick (matches ride-android).
        viewModelScope.launch {
            socketService.driverLocation.collect { loc ->
                val step = _state.value.step
                val activeTrip = step == RiderStep.DriverOnWay || step == RiderStep.DriverArrived || step == RiderStep.TripStarted
                if (loc != null && activeTrip) {
                    _state.update { it.copy(driverLocation = loc) }
                }
            }
        }
        // V2 bidding events for the rider's open trip: live bids, match committed, no-bids/timeout.
        viewModelScope.launch {
            socketService.v2Events.collect { event ->
                val tripId = _state.value.bidTrip?.tripId
                when (event) {
                    is V2SocketEvent.BidsUpdate -> if (event.tripId == tripId) _state.update { it.copy(bids = event.bids) }
                    is V2SocketEvent.BidAccepted -> if (event.tripId == tripId) onBidMatched()
                    is V2SocketEvent.NoBids -> if (event.tripId == tripId) _state.update { it.copy(bidNotice = NOTICE_NO_BIDS) }
                    is V2SocketEvent.BiddingTimeout -> if (event.tripId == tripId) _state.update { it.copy(bidNotice = NOTICE_TIMEOUT) }
                    else -> Unit // driver-facing events
                }
            }
        }
        // Restore an in-progress ride if one exists (ride-android: `checkOnGoingRide` on dashboard load).
        checkOngoingTrip()
    }

    /**
     * On dashboard entry, check for an active ride (`trips/exists` → `trips/socket/{id}`) and restore the
     * matching screen, like ride-android. Failures/no-trip leave the rider on the idle home map.
     */
    private fun checkOngoingTrip() {
        viewModelScope.launch {
            when (val result = getOngoingTrip()) {
                is ApiResult.Success -> result.data?.let { restoreOngoingTrip(it) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    /** Restores the rider to the screen that matches the trip's stage (only while still idle on home). */
    private fun restoreOngoingTrip(trip: OngoingTrip) {
        // Don't clobber a booking the rider has already started while the check was in flight.
        if (_state.value.step != RiderStep.Home) return
        when (trip.stage) {
            // V2: still collecting bids → restore the bidding screen and refresh the current bids.
            TripStage.AwaitingBids -> {
                val offered = trip.offeredFare ?: 0.0
                _state.update {
                    it.copy(
                        step = RiderStep.Bidding,
                        pickup = trip.pickup ?: it.pickup,
                        destination = trip.destination ?: it.destination,
                        offeredFare = offered,
                        bidTrip = BidTrip(
                            tripId = trip.tripId,
                            status = 15,
                            fareRange = FareRange("SAR", recommended = offered, min = offered, max = offered * RESTORE_MAX_FACTOR, riderOfferedFare = offered),
                            requestTimeLimit = null,
                        ),
                        bids = emptyList(),
                    )
                }
                fetchRoute()
                startBidsPolling(trip.tripId)
            }
            TripStage.Searching -> {
                _state.update {
                    it.copy(
                        step = RiderStep.Searching,
                        pickup = trip.pickup ?: it.pickup,
                        destination = trip.destination ?: it.destination,
                        bookedTrip = BookedTrip(tripId = trip.tripId, requestTimeLimit = null),
                        noCaptainFound = false,
                    )
                }
                // Show the same pickup→destination route as the live searching screen.
                fetchRoute()
            }
            // Captain assigned / arrived / trip started → the matching active-ride view.
            TripStage.DriverAssigned, TripStage.DriverArrived, TripStage.InProgress -> {
                val accepted = trip.acceptedTrip ?: return
                // Set pickup/destination first so the route (pickup→captain or pickup→destination) can draw.
                _state.update {
                    it.copy(
                        pickup = trip.pickup ?: it.pickup,
                        destination = trip.destination ?: it.destination,
                        bookedTrip = BookedTrip(tripId = trip.tripId, requestTimeLimit = null),
                        driverLocation = trip.driverLocation ?: it.driverLocation,
                    )
                }
                when (trip.stage) {
                    TripStage.DriverArrived -> onDriverArrived(accepted, trip.arrivedAtMillis)
                    TripStage.InProgress -> onTripStarted(accepted)
                    else -> onDriverAccepted(accepted)
                }
            }
            // Completed (unrated) → restore the rate-your-captain screen.
            TripStage.Completed -> trip.acceptedTrip?.let {
                _state.update { st ->
                    st.copy(pickup = trip.pickup ?: st.pickup, destination = trip.destination ?: st.destination)
                }
                onTripCompleted(it)
            }
            // Terminal states have no active ride to restore → stay on home.
            TripStage.Cancelled, TripStage.Expired, TripStage.Unknown -> Unit
        }
    }

    private var searchJob: Job? = null
    private var searchTimeoutJob: Job? = null

    /** Polls competing bids while on the bidding screen (REST fallback for the `v2/trip-bids-update` push). */
    private var bidsPollJob: Job? = null

    fun onEvent(event: RiderBookingEvent) {
        when (event) {
            RiderBookingEvent.OpenDestinationSearch -> openDestinationSearch(null)
            is RiderBookingEvent.OpenCategory -> openDestinationSearch(event.category)
            is RiderBookingEvent.SelectRecent -> selectRecent(event.location)
            RiderBookingEvent.RetryCategories -> loadCategories()
            is RiderBookingEvent.QueryChanged -> onQueryChanged(event.value)
            is RiderBookingEvent.SelectSuggestion -> selectSuggestion(event.suggestion)
            RiderBookingEvent.UseCurrentLocation -> useCurrentLocation()
            RiderBookingEvent.LocateMe -> locateMe()
            RiderBookingEvent.OpenMapPicker ->
                _state.update { it.copy(mapPickerOrigin = it.step, step = RiderStep.MapPicker) }
            is RiderBookingEvent.ConfirmMapLocation -> confirmMapLocation(event.latitude, event.longitude)
            RiderBookingEvent.EditPickup ->
                _state.update { it.copy(step = RiderStep.PickupSearch, query = "", suggestions = emptyList()) }
            RiderBookingEvent.ProceedToRideSelection -> proceedToRideSelection()
            is RiderBookingEvent.SelectCab -> selectCab(event.cabId)
            is RiderBookingEvent.ApplyPromo -> applyPromo(event.code)
            RiderBookingEvent.RemovePromo -> _state.update { it.copy(promo = null) }
            RiderBookingEvent.ConfirmRide -> openProposeFare()
            is RiderBookingEvent.SubmitOffer -> submitOffer(event.fare)
            is RiderBookingEvent.SelectBid -> selectBid(event.bidId)
            is RiderBookingEvent.RejectBid -> rejectBid(event.bidId)
            is RiderBookingEvent.RaiseOffer -> raiseOffer(event.fare)
            RiderBookingEvent.CancelBidding -> cancelBidding()
            RiderBookingEvent.ConsumeBidNotice -> _state.update { it.copy(bidNotice = null) }
            RiderBookingEvent.RefreshBalance -> loadBalance()
            RiderBookingEvent.TryAgainSearch -> tryAgainSearch()
            RiderBookingEvent.CancelRide -> cancelRide()
            RiderBookingEvent.ImComing -> imComing()
            RiderBookingEvent.OpenChangeDrop -> openChangeDrop()
            RiderBookingEvent.ConfirmChangeDrop -> confirmChangeDrop()
            RiderBookingEvent.OpenHelp -> _state.update { it.copy(showHelp = true) }
            RiderBookingEvent.CloseHelp -> _state.update { it.copy(showHelp = false) }
            is RiderBookingEvent.SelectRating -> _state.update { it.copy(rating = event.stars) }
            RiderBookingEvent.SubmitRating -> submitRating()
            RiderBookingEvent.Back -> back()
            RiderBookingEvent.ConsumeError -> _state.update { it.copy(errorMessage = null) }
            RiderBookingEvent.ConsumeInfo -> _state.update { it.copy(dropChangeSucceeded = false, cancelledByDriver = false) }
        }
    }

    /** Loads the home service categories; on success remembers a default (taxi → first) for the booking flow. */
    private fun loadCategories() {
        _state.update { it.copy(isLoadingCategories = true) }
        viewModelScope.launch {
            when (val result = getRideCategories()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isLoadingCategories = false,
                        categories = result.data,
                        selectedCategory = it.selectedCategory ?: defaultCategory(result.data),
                    )
                }
                is ApiResult.Error, is ApiResult.Failure -> _state.update { it.copy(isLoadingCategories = false) }
            }
        }
    }

    /** Loads the recent quick-pick addresses; any failure simply leaves the section empty (hidden). */
    private fun loadRecents() {
        _state.update { it.copy(isLoadingRecents = true) }
        viewModelScope.launch {
            when (val result = getRecentAddresses()) {
                is ApiResult.Success -> _state.update { it.copy(isLoadingRecents = false, recentLocations = result.data) }
                is ApiResult.Error, is ApiResult.Failure -> _state.update { it.copy(isLoadingRecents = false) }
            }
        }
    }

    /** Taxi category when present (by key/name), else the first — the default the "Where to?" bar proceeds with. */
    private fun defaultCategory(list: List<RideCategory>): RideCategory? =
        list.firstOrNull { it.key.contains("taxi") } ?: list.firstOrNull()

    /**
     * Opens the destination search and continues the existing booking flow, remembering the category it
     * proceeds with: an explicitly tapped tile, else the already-selected one, else the default (taxi/first).
     */
    private fun openDestinationSearch(category: RideCategory?) {
        val remembered = category ?: _state.value.selectedCategory ?: defaultCategory(_state.value.categories)
        // New booking → drop any previous route so a stale polyline never lingers.
        _state.update {
            it.copy(
                step = RiderStep.DestinationSearch,
                selectedCategory = remembered,
                query = "",
                suggestions = emptyList(),
                routePoints = emptyList(),
            )
        }
    }

    /** A recent home address → set it as the destination (default category remembered) and continue. */
    private fun selectRecent(location: RecentLocation) {
        val remembered = _state.value.selectedCategory ?: defaultCategory(_state.value.categories)
        _state.update {
            it.copy(
                step = RiderStep.DestinationSearch,
                selectedCategory = remembered,
                query = "",
                suggestions = emptyList(),
                routePoints = emptyList(),
            )
        }
        applyResolvedPlace(location.place)
    }

    private fun onQueryChanged(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(isSearching = true) }
            when (val result = autocomplete(value)) {
                is ApiResult.Success -> _state.update { it.copy(isSearching = false, suggestions = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isSearching = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isSearching = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun selectSuggestion(suggestion: PlaceSuggestion) {
        _state.update { it.copy(isResolving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = placeDetails(suggestion.id)) {
                is ApiResult.Success -> applyResolvedPlace(result.data)
                is ApiResult.Error -> _state.update { it.copy(isResolving = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isResolving = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun useCurrentLocation() {
        _state.update { it.copy(isResolving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = currentLocation()) {
                is ApiResult.Success -> applyResolvedPlace(result.data)
                is ApiResult.Error -> _state.update { it.copy(isResolving = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isResolving = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Centre the home map on the device location (no flow change); ignores failures silently. */
    private fun locateMe() {
        viewModelScope.launch {
            when (val result = currentLocation()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(myLocation = result.data) }
                    // Ask the server for nearby captains around the rider (find-drivers).
                    socketService.findDrivers(result.data.latitude, result.data.longitude)
                }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    private fun confirmMapLocation(latitude: Double, longitude: Double) {
        _state.update { it.copy(isResolving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = reverseGeocode(latitude, longitude)) {
                is ApiResult.Success -> applyResolvedPlace(result.data)
                is ApiResult.Error -> _state.update { it.copy(isResolving = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isResolving = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Destination set first → pickup search; pickup set → confirm pickup. */
    private fun applyResolvedPlace(place: PlaceLocation) {
        val target = if (_state.value.step == RiderStep.MapPicker) _state.value.mapPickerOrigin else _state.value.step
        // Change-drop flow: the picked place is a TENTATIVE new drop → preview the new fare (not committed).
        if (target == RiderStep.ChangeDropSearch) {
            _state.update {
                it.copy(
                    isResolving = false,
                    changeDropDestination = place,
                    changeDropQuote = null,
                    step = RiderStep.ChangeDropConfirm,
                    query = "",
                    suggestions = emptyList(),
                )
            }
            fetchChangeDropQuote()
            return
        }
        _state.update { state ->
            when (target) {
                RiderStep.DestinationSearch -> state.copy(
                    isResolving = false,
                    destination = place,
                    step = RiderStep.PickupSearch,
                    query = "",
                    suggestions = emptyList(),
                )
                RiderStep.PickupSearch -> state.copy(
                    isResolving = false,
                    pickup = place,
                    step = RiderStep.ConfirmPickup,
                    query = "",
                    suggestions = emptyList(),
                )
                else -> state.copy(isResolving = false)
            }
        }
        // Once both ends are known (pickup just set), fetch the route to draw on the map.
        fetchRoute()
    }

    /** Fetches the pickup → destination route polyline; falls back to a straight line if unavailable. */
    private fun fetchRoute() {
        val pickup = _state.value.pickup ?: return
        val destination = _state.value.destination ?: return
        viewModelScope.launch {
            val points = when (val result = getRoute(pickup, destination)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error, is ApiResult.Failure -> emptyList()
            }
            val path = points.ifEmpty {
                listOf(
                    LatLngPoint(pickup.latitude, pickup.longitude),
                    LatLngPoint(destination.latitude, destination.longitude),
                )
            }
            _state.update { it.copy(routePoints = path) }
        }
    }

    /** Move to ride selection and load the cab list + wallet balance. */
    private fun proceedToRideSelection() {
        val pickup = _state.value.pickup ?: return
        val destination = _state.value.destination ?: return
        val categoryId = _state.value.selectedCategory?.id
        _state.update { it.copy(step = RiderStep.SelectRide, isLoadingCabs = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getCabTypes(pickup, destination, categoryId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isLoadingCabs = false,
                        cabs = result.data,
                        // Pre-select the first cab (ride-android shows the top one highlighted).
                        selectedCabId = it.selectedCabId ?: result.data.firstOrNull()?.id,
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(isLoadingCabs = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingCabs = false, errorMessage = result.error.message) }
            }
        }
        loadBalance()
    }

    private fun loadBalance() {
        viewModelScope.launch {
            when (val result = getBalance()) {
                is ApiResult.Success -> _state.update { it.copy(balance = result.data) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    private fun selectCab(cabId: String) {
        _state.update { it.copy(selectedCabId = cabId) }
        // A promo discount depends on fare + cab, so re-validate it against the newly selected cab.
        _state.value.promo?.let { revalidatePromo(it.code) }
    }

    private fun applyPromo(code: String) = revalidatePromo(code)

    private fun revalidatePromo(code: String) {
        val state = _state.value
        val cab = state.selectedCab ?: return
        val pickup = state.pickup ?: return
        if (code.isBlank()) return
        _state.update { it.copy(isPromoLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = validatePromo(code, cab.fare, cab.id, pickup)) {
                is ApiResult.Success -> _state.update { it.copy(isPromoLoading = false, promo = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isPromoLoading = false, promo = null, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isPromoLoading = false, promo = null, errorMessage = result.error.message) }
            }
        }
    }

    private fun confirmRide() {
        val state = _state.value
        val cab = state.selectedCab ?: return
        val pickup = state.pickup ?: return
        val destination = state.destination ?: return
        // Clear any previous "no captain" result so a retry shows the waiting state immediately.
        _state.update { it.copy(isBooking = true, noCaptainFound = false, errorMessage = null) }
        viewModelScope.launch {
            when (val result = createTrip(pickup, destination, cab.id, state.promo?.code)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(isBooking = false, bookedTrip = result.data, step = RiderStep.Searching, noCaptainFound = false)
                    }
                    // Expire the search if no captain accepts within the request window (socket fallback).
                    startSearchTimeout(result.data.requestTimeLimit)
                }
                is ApiResult.Error -> _state.update { it.copy(isBooking = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isBooking = false, errorMessage = result.error.message) }
            }
        }
    }

    /** "Confirm Ride" → V2: go to the propose-fare step, seeded with the selected cab's recommended fare. */
    private fun openProposeFare() {
        val cab = _state.value.selectedCab ?: return
        if (_state.value.pickup == null || _state.value.destination == null) return
        _state.update { it.copy(step = RiderStep.ProposeFare, offeredFare = it.offeredFare ?: cab.fare, errorMessage = null) }
    }

    /** Submit the proposed fare → create a BID trip (status 15) and move to the live-bids screen. */
    private fun submitOffer(fare: Double) {
        val state = _state.value
        val cab = state.selectedCab ?: return
        val pickup = state.pickup ?: return
        val destination = state.destination ?: return
        if (state.isCreatingBidTrip) return
        // Ensure the rider's socket is connected + subscribed to their room so the `v2/trip-bids-update`
        // pushes (and the eventual bid-accepted) are delivered while bidding.
        socketService.connect()
        _state.update { it.copy(isCreatingBidTrip = true, offeredFare = fare, errorMessage = null) }
        viewModelScope.launch {
            when (val result = createBidTrip(pickup, destination, cab.id, state.selectedCategory?.id, fare)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(isCreatingBidTrip = false, bidTrip = result.data, bids = emptyList(), bidNotice = null, step = RiderStep.Bidding)
                    }
                    // The socket pushes bids live; also poll as a fallback so offers always surface.
                    startBidsPolling(result.data.tripId)
                }
                is ApiResult.Error -> _state.update { it.copy(isCreatingBidTrip = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isCreatingBidTrip = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Polls `GET /v2/trips/{id}/bids` every few seconds while the rider is on the bidding screen. */
    private fun startBidsPolling(tripId: String) {
        bidsPollJob?.cancel()
        bidsPollJob = viewModelScope.launch {
            while (_state.value.step == RiderStep.Bidding && _state.value.bidTrip?.tripId == tripId) {
                fetchTripBids(tripId)
                delay(BID_POLL_MS)
            }
        }
    }

    private fun stopBidsPolling() {
        bidsPollJob?.cancel()
        bidsPollJob = null
    }

    /** Select a competing bid → commit the match; on success switch to the assigned (on-the-way) flow. */
    private fun selectBid(bidId: String) {
        val tripId = _state.value.bidTrip?.tripId ?: return
        if (_state.value.isBidActionInFlight) return
        _state.update { it.copy(isBidActionInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = selectBidUseCase(tripId, bidId)) {
                is ApiResult.Success -> { _state.update { it.copy(isBidActionInFlight = false) }; onBidMatched() }
                is ApiResult.Error -> _state.update { it.copy(isBidActionInFlight = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isBidActionInFlight = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Dismiss a single competing bid (optimistic; the next bids-update is authoritative). */
    private fun rejectBid(bidId: String) {
        val tripId = _state.value.bidTrip?.tripId ?: return
        _state.update { it.copy(bids = it.bids.filterNot { b -> b.bidId == bidId }) }
        viewModelScope.launch { rejectBidUseCase(tripId, bidId) }
    }

    /** Raise the offered fare to attract more/faster bids. */
    private fun raiseOffer(fare: Double) {
        val tripId = _state.value.bidTrip?.tripId ?: return
        if (_state.value.isBidActionInFlight) return
        _state.update { it.copy(isBidActionInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = raiseOfferUseCase(tripId, fare)) {
                is ApiResult.Success -> _state.update { st ->
                    st.copy(
                        isBidActionInFlight = false,
                        offeredFare = fare,
                        bidTrip = st.bidTrip?.let { it.copy(fareRange = it.fareRange.copy(riderOfferedFare = fare)) },
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(isBidActionInFlight = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isBidActionInFlight = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Cancel the open (awaiting-bids) request and return home (cancel is best-effort). */
    private fun cancelBidding() {
        val tripId = _state.value.bidTrip?.tripId
        if (tripId == null) { goHome(); return }
        if (_state.value.isBidActionInFlight) return
        _state.update { it.copy(isBidActionInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            cancelOpenTrip(tripId, null)
            clearBidding()
            goHome()
        }
    }

    /** A bid was matched → fetch the assigned trip (`trips/exists` → socket) and go on-the-way. */
    private fun onBidMatched() {
        stopBidsPolling()
        viewModelScope.launch {
            when (val result = getOngoingTrip()) {
                is ApiResult.Success -> result.data?.acceptedTrip?.let { accepted ->
                    val trip = result.data
                    _state.update {
                        it.copy(pickup = trip.pickup ?: it.pickup, destination = trip.destination ?: it.destination, bidTrip = null, bids = emptyList())
                    }
                    onDriverAccepted(accepted)
                }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    private fun clearBidding() {
        stopBidsPolling()
        _state.update { it.copy(bidTrip = null, bids = emptyList(), isBidActionInFlight = false, isCreatingBidTrip = false) }
    }

    /** Fetch the current competing bids (on restore); the socket keeps them live afterwards. */
    private fun fetchTripBids(tripId: String) {
        viewModelScope.launch {
            when (val result = getTripBids(tripId)) {
                is ApiResult.Success -> _state.update { if (it.bidTrip?.tripId == tripId) it.copy(bids = result.data) else it }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    /** Ends the search and shows the "no captain / expired" try-again state (idempotent). */
    private fun failSearch() {
        if (_state.value.step != RiderStep.Searching) return
        searchTimeoutJob?.cancel()
        _state.update { it.copy(noCaptainFound = true) }
    }

    /**
     * Try Again (only shown once the search failed) — the request is already dead, so just return to ride
     * selection to re-request, exactly like ride-android's back-to-cab-selection (no extra cancel call).
     */
    private fun tryAgainSearch() {
        searchTimeoutJob?.cancel()
        _state.update { it.copy(step = RiderStep.SelectRide, noCaptainFound = false) }
    }

    /**
     * Cancel Ride on the searching screen, handled like ride-android:
     * - active request → call `cancel-trip-request`, show a loader, and only return home once it succeeds
     *   (errors keep the rider on the screen so they can retry);
     * - already failed/expired → nothing to cancel server-side, so just go home.
     */
    private fun cancelRide() {
        searchTimeoutJob?.cancel()
        val state = _state.value
        val tripId = state.bookedTrip?.tripId
        if (state.noCaptainFound || tripId.isNullOrBlank()) {
            goHome()
            return
        }
        _state.update { it.copy(isCancelling = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = cancelTrip(tripId)) {
                is ApiResult.Success -> goHome()
                is ApiResult.Error -> _state.update { it.copy(isCancelling = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isCancelling = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Clears the booked trip and returns to the home map. */
    private fun goHome() {
        _state.update {
            it.copy(
                step = RiderStep.Home,
                noCaptainFound = false,
                isCancelling = false,
                bookedTrip = null,
                acceptedTrip = null,
                driverLocation = null,
                driverRoutePoints = emptyList(),
                arrivedAtMillis = null,
                rating = 0,
                isSubmittingRating = false,
                bidTrip = null,
                bids = emptyList(),
                offeredFare = null,
                isCreatingBidTrip = false,
                isBidActionInFlight = false,
                errorMessage = null,
            )
        }
    }

    /** Captain accepted the request → show the "on the way" screen and draw the pickup→captain route. */
    private fun onDriverAccepted(trip: AcceptedTrip) {
        searchTimeoutJob?.cancel()
        val driverLatLng = if (trip.driverLatitude != null && trip.driverLongitude != null) {
            LatLngPoint(trip.driverLatitude, trip.driverLongitude)
        } else {
            _state.value.driverLocation
        }
        _state.update {
            it.copy(
                step = RiderStep.DriverOnWay,
                noCaptainFound = false,
                acceptedTrip = trip,
                driverLocation = driverLatLng,
            )
        }
        fetchDriverRoute()
    }

    /** Fetches the pickup → captain route polyline (falls back to a straight line). */
    private fun fetchDriverRoute() {
        val pickup = _state.value.pickup ?: return
        val driver = _state.value.driverLocation ?: return
        viewModelScope.launch {
            val driverPlace = PlaceLocation(name = "", address = "", latitude = driver.latitude, longitude = driver.longitude)
            val points = when (val result = getRoute(pickup, driverPlace)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error, is ApiResult.Failure -> emptyList()
            }
            val path = points.ifEmpty {
                listOf(LatLngPoint(pickup.latitude, pickup.longitude), driver)
            }
            _state.update { it.copy(driverRoutePoints = path) }
        }
    }

    /**
     * Captain reached the pickup point (`driver_reached`) → show the "your ride has arrived" screen with
     * the wait timer, and switch the map to the pickup → destination route. [arrivedAt] is the server's
     * reached-at time (restore) or null for a live event (timer counts from now).
     */
    private fun onDriverArrived(trip: AcceptedTrip, arrivedAt: Long? = null) {
        searchTimeoutJob?.cancel()
        val driverLatLng = if (trip.driverLatitude != null && trip.driverLongitude != null) {
            LatLngPoint(trip.driverLatitude, trip.driverLongitude)
        } else {
            _state.value.driverLocation
        }
        _state.update {
            it.copy(
                step = RiderStep.DriverArrived,
                noCaptainFound = false,
                acceptedTrip = trip,
                driverLocation = driverLatLng,
                arrivedAtMillis = arrivedAt ?: it.arrivedAtMillis ?: System.currentTimeMillis(),
            )
        }
        // The map now previews the trip itself: pickup → destination.
        fetchRoute()
    }

    /** Trip started (`trip_started`) → in-trip view; the map shows the pickup → destination route. */
    private fun onTripStarted(trip: AcceptedTrip) {
        searchTimeoutJob?.cancel()
        _state.update {
            it.copy(step = RiderStep.TripStarted, noCaptainFound = false, acceptedTrip = trip)
        }
        fetchRoute()
    }

    /** Trip finished (`trip_completed`) → the rate-your-captain screen (payment, balance, loyalty, invoice). */
    private fun onTripCompleted(trip: AcceptedTrip) {
        searchTimeoutJob?.cancel()
        _state.update {
            it.copy(step = RiderStep.TripCompleted, noCaptainFound = false, acceptedTrip = trip, rating = 0)
        }
        loadBalance()
        fetchRoute()
    }

    /**
     * The captain cancelled the trip (`driver_cancelled`). Drop the dead trip and send the rider back to the
     * confirm-ride (cab selection) screen with their pickup + destination still selected, so they can
     * re-request quickly. Falls back to home if the locations were somehow lost.
     */
    private fun onDriverCancelled() {
        searchTimeoutJob?.cancel()
        _state.update {
            it.copy(
                acceptedTrip = null,
                driverLocation = null,
                driverRoutePoints = emptyList(),
                bookedTrip = null,
                arrivedAtMillis = null,
                rating = 0,
                noCaptainFound = false,
                showHelp = false,
                cancelledByDriver = true,
            )
        }
        val current = _state.value
        if (current.pickup != null && current.destination != null) {
            // Re-draw the pickup → destination route and reopen the cab-selection / confirm-ride screen.
            fetchRoute()
            proceedToRideSelection()
        } else {
            goHome()
        }
    }

    /** Submits the star rating; on success the trip is done → return home. */
    private fun submitRating() {
        val state = _state.value
        val trip = state.acceptedTrip ?: return
        if (state.rating < 1 || state.isSubmittingRating) return
        _state.update { it.copy(isSubmittingRating = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = rateDriver(trip.tripId, state.rating, RIDER_REVIEW_NAME, trip.driverName)) {
                is ApiResult.Success -> goHome()
                is ApiResult.Error -> _state.update { it.copy(isSubmittingRating = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isSubmittingRating = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Browser URL for the trip's VAT invoice (ride-android: `<dashboardUrl>/trip-invoice/{tripId}`). */
    fun invoiceUrl(): String? =
        _state.value.acceptedTrip?.let { "${env.dashboardUrl.trimEnd('/')}/trip-invoice/${it.tripId}" }

    /** "I am coming" on the arrived screen → back to the on-the-way (ride-accepted) view. */
    private fun imComing() {
        if (_state.value.acceptedTrip == null) return
        _state.update { it.copy(step = RiderStep.DriverOnWay) }
        // Restore the pickup → captain route for the on-the-way map.
        fetchDriverRoute()
    }

    /** "Change" on the in-trip screen → open the change-drop-off search. */
    private fun openChangeDrop() {
        if (_state.value.acceptedTrip == null) return
        _state.update {
            it.copy(
                step = RiderStep.ChangeDropSearch,
                query = "",
                suggestions = emptyList(),
                changeDropDestination = null,
                changeDropQuote = null,
            )
        }
    }

    /** Re-quotes the fare for the tentative new drop-off (same cab type). */
    private fun fetchChangeDropQuote() {
        val pickup = _state.value.pickup ?: return
        val newDest = _state.value.changeDropDestination ?: return
        val cabId = _state.value.acceptedTrip?.cabId.orEmpty()
        val categoryId = _state.value.selectedCategory?.id
        loadBalance()
        viewModelScope.launch {
            when (val result = estimateDropChange(pickup, newDest, cabId, categoryId)) {
                is ApiResult.Success -> _state.update { it.copy(changeDropQuote = result.data) }
                is ApiResult.Error -> _state.update { it.copy(errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    /** "Pay Remaining" → commit the new drop-off; on success update the trip + return to the in-trip view. */
    private fun confirmChangeDrop() {
        val state = _state.value
        val tripId = state.acceptedTrip?.tripId ?: return
        val newDest = state.changeDropDestination ?: return
        if (state.isChangingDrop) return
        _state.update { it.copy(isChangingDrop = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = changeDestination(tripId, newDest)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            isChangingDrop = false,
                            step = RiderStep.TripStarted,
                            destination = newDest,
                            changeDropDestination = null,
                            changeDropQuote = null,
                            dropChangeSucceeded = true,
                        )
                    }
                    // Redraw the pickup → new-destination route on the in-trip map.
                    fetchRoute()
                }
                is ApiResult.Error -> _state.update { it.copy(isChangingDrop = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isChangingDrop = false, errorMessage = result.error.message) }
            }
        }
    }

    /**
     * Expires the search after the request window. The window is derived from the server's
     * [requestTimeLimit] when it parses to a sane duration; otherwise a default is used.
     */
    private fun startSearchTimeout(requestTimeLimit: String?) {
        searchTimeoutJob?.cancel()
        searchTimeoutJob = viewModelScope.launch {
            delay(searchTimeoutMillis(requestTimeLimit))
            failSearch()
        }
    }

    private fun searchTimeoutMillis(requestTimeLimit: String?): Long {
        val expiresAt = requestTimeLimit?.let {
            runCatching {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                format.parse(it)?.time
            }.getOrNull()
        }
        val remaining = expiresAt?.let { it - System.currentTimeMillis() }
        // Use the server window only when it's sane; otherwise fall back (guards against TZ/format quirks).
        return if (remaining != null && remaining in MIN_SEARCH_TIMEOUT_MS..MAX_SEARCH_TIMEOUT_MS) {
            remaining
        } else {
            DEFAULT_SEARCH_TIMEOUT_MS
        }
    }

    private fun back() {
        searchTimeoutJob?.cancel()
        _state.update { state ->
            val previous = when (state.step) {
                RiderStep.Home -> RiderStep.Home
                RiderStep.DestinationSearch -> RiderStep.Home
                RiderStep.PickupSearch -> RiderStep.DestinationSearch
                RiderStep.MapPicker -> state.mapPickerOrigin
                RiderStep.ConfirmPickup -> RiderStep.PickupSearch
                RiderStep.SelectRide -> RiderStep.ConfirmPickup
                // Propose-fare → back to cab selection; bidding has no back-dismiss (use Cancel).
                RiderStep.ProposeFare -> RiderStep.SelectRide
                RiderStep.Bidding -> RiderStep.Bidding
                RiderStep.Searching -> RiderStep.Home
                // Active-ride views: don't allow back-dismiss (rider uses Cancel Ride / I am coming).
                RiderStep.DriverOnWay -> RiderStep.DriverOnWay
                RiderStep.DriverArrived -> RiderStep.DriverArrived
                RiderStep.TripStarted -> RiderStep.TripStarted
                // Change-drop flow: search → back to in-trip; confirm → back to search.
                RiderStep.ChangeDropSearch -> RiderStep.TripStarted
                RiderStep.ChangeDropConfirm -> RiderStep.ChangeDropSearch
                // Rating screen: no back-dismiss (rider submits to finish).
                RiderStep.TripCompleted -> RiderStep.TripCompleted
            }
            state.copy(step = previous, query = "", suggestions = emptyList(), noCaptainFound = false, errorMessage = null)
        }
    }

    private companion object {
        const val RIDER_REVIEW_NAME = "Rider"
        const val DEFAULT_SEARCH_TIMEOUT_MS = 45_000L
        const val MIN_SEARCH_TIMEOUT_MS = 5_000L
        const val MAX_SEARCH_TIMEOUT_MS = 180_000L
        const val BID_POLL_MS = 3_000L
        const val NOTICE_NO_BIDS = "NO_BIDS"
        const val NOTICE_TIMEOUT = "TIMEOUT"
        const val RESTORE_MAX_FACTOR = 2.5
    }
}
