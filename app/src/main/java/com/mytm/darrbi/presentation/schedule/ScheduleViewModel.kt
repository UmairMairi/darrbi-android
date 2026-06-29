package com.mytm.darrbi.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.Bid
import com.mytm.darrbi.domain.model.BidStatus
import com.mytm.darrbi.domain.model.C2cCity
import com.mytm.darrbi.domain.model.C2cQuote
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.domain.model.ScheduledTrip
import com.mytm.darrbi.domain.model.ScheduledTripState
import com.mytm.darrbi.domain.model.UpcomingScheduledTrip
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.repository.V2SocketEvent
import com.mytm.darrbi.domain.usecase.AutocompletePlacesUseCase
import com.mytm.darrbi.domain.usecase.CancelScheduledTripUseCase
import com.mytm.darrbi.domain.usecase.CreateScheduledTripUseCase
import com.mytm.darrbi.domain.usecase.CurrentLocationUseCase
import com.mytm.darrbi.domain.usecase.GetBalanceUseCase
import com.mytm.darrbi.domain.usecase.GetC2cCitiesUseCase
import com.mytm.darrbi.domain.usecase.GetC2cQuoteUseCase
import com.mytm.darrbi.domain.usecase.GetCabTypesUseCase
import com.mytm.darrbi.domain.usecase.GetMyUpcomingUseCase
import com.mytm.darrbi.domain.usecase.GetRideCategoriesUseCase
import com.mytm.darrbi.domain.usecase.GetRouteUseCase
import com.mytm.darrbi.domain.usecase.GetTripBidsUseCase
import com.mytm.darrbi.domain.usecase.PlaceDetailsUseCase
import com.mytm.darrbi.domain.usecase.RaiseOfferUseCase
import com.mytm.darrbi.domain.usecase.RejectBidUseCase
import com.mytm.darrbi.domain.usecase.ReverseGeocodeUseCase
import com.mytm.darrbi.domain.usecase.SelectBidUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steps of the scheduled City-to-City flow. `DateTime → OriginCity → OriginPoint → DestCity → DestPoint`
 * collect the request; `Review` shows the C2C cab + quote band + propose-fare; on create the rider lands on
 * `Confirmation`. The open request is watched/selected on `Bidding` (reuses the V2 bidding engine), a matched
 * trip is shown on `Waiting`, and `Upcoming` lists all future C2C trips. After activation the trip becomes a
 * normal ongoing trip handled by the existing rider-home flow — no step here covers the live ride.
 */
enum class ScheduleStep { DateTime, OriginCity, OriginPoint, DestCity, DestPoint, Review, Confirmation, Upcoming, Bidding, Waiting, MapPicker }

data class ScheduleUiState(
    // The flow opens on the Upcoming hub when the rider has scheduled rides; if there are none it jumps
    // straight into the new-ride flow (DateTime). "Create a New Ride" also starts a booking from the hub.
    val step: ScheduleStep = ScheduleStep.Upcoming,
    /** True when the new-booking flow was entered from the Upcoming hub → back returns there, not home. */
    val startedFromHub: Boolean = false,
    /** The point step (OriginPoint / DestPoint) the map picker was opened from, so confirm/back return to it. */
    val mapPickerOrigin: ScheduleStep = ScheduleStep.OriginPoint,
    // Cities for the origin/destination pickers.
    val cities: List<C2cCity> = emptyList(),
    val isLoadingCities: Boolean = false,
    val originCity: C2cCity? = null,
    val destinationCity: C2cCity? = null,
    // Scheduled departure (local epoch millis).
    val departureAtMillis: Long? = null,
    // Pickup/dropoff point search.
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val isResolving: Boolean = false,
    val pickup: PlaceLocation? = null,
    val dropoff: PlaceLocation? = null,
    val routePoints: List<LatLngPoint> = emptyList(),
    val myLocation: PlaceLocation? = null,
    // Review: C2C cab + quote band + proposed fare.
    val seats: Int = 1,
    val cabs: List<CabOption> = emptyList(),
    val selectedCabId: String? = null,
    val isLoadingCabs: Boolean = false,
    val quote: C2cQuote? = null,
    val isLoadingQuote: Boolean = false,
    val offeredFare: Double? = null,
    val balance: Double? = null,
    // Create.
    val isCreating: Boolean = false,
    val createdTrip: ScheduledTrip? = null,
    // Bidding (open request).
    val biddingTripId: String? = null,
    val biddingOfferedFare: Double = 0.0,
    val biddingMaxFare: Double = 0.0,
    val biddingDepartureMillis: Long? = null,
    val bids: List<Bid> = emptyList(),
    val isBidActionInFlight: Boolean = false,
    // Matched / waiting.
    val matchedTripId: String? = null,
    val matchedBid: Bid? = null,
    val matchedFare: Double = 0.0,
    val matchedDepartureMillis: Long? = null,
    val matchedDriverId: String? = null,
    // Upcoming list.
    val upcoming: List<UpcomingScheduledTrip> = emptyList(),
    val isLoadingUpcoming: Boolean = false,
    // Cancel.
    val isCancelling: Boolean = false,
    // One-shots.
    val exitRequested: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val selectedCab: CabOption? get() = cabs.firstOrNull { it.id == selectedCabId }
    val minFare: Double get() = quote?.minFare ?: 0.0
    val maxFare: Double get() = quote?.maxFare ?: Double.MAX_VALUE

    /** Resolve a city id to its display name (Arabic when available is decided in the UI layer). */
    fun cityName(id: String?): String? = cities.firstOrNull { it.id == id }?.name?.takeIf { it.isNotBlank() }
    fun city(id: String?): C2cCity? = cities.firstOrNull { it.id == id }
}

sealed interface ScheduleEvent {
    data class SetDateTime(val millis: Long) : ScheduleEvent
    data object ConfirmDateTime : ScheduleEvent
    data class SelectOriginCity(val city: C2cCity) : ScheduleEvent
    data class SelectDestinationCity(val city: C2cCity) : ScheduleEvent
    data class QueryChanged(val value: String) : ScheduleEvent
    data class SelectSuggestion(val suggestion: PlaceSuggestion) : ScheduleEvent
    data object UseCurrentLocation : ScheduleEvent
    data object LocateMe : ScheduleEvent
    /** Open the full-screen map picker for the current pickup/dropoff point step. */
    data object OpenMapPicker : ScheduleEvent
    /** Confirm the chosen map centre → reverse-geocode → apply as pickup/dropoff. */
    data class ConfirmMapLocation(val latitude: Double, val longitude: Double) : ScheduleEvent
    data class SelectCab(val cabId: String) : ScheduleEvent
    data class SetSeats(val seats: Int) : ScheduleEvent
    /** "Find Offers" → create the open scheduled request. */
    data class SubmitOffer(val fare: Double) : ScheduleEvent
    data object GoToUpcoming : ScheduleEvent
    data object Finish : ScheduleEvent
    data object RefreshUpcoming : ScheduleEvent
    data class OpenUpcoming(val trip: UpcomingScheduledTrip) : ScheduleEvent
    data object CreateNew : ScheduleEvent
    data class SelectBid(val bidId: String) : ScheduleEvent
    data class RejectBid(val bidId: String) : ScheduleEvent
    data class RaiseOffer(val fare: Double) : ScheduleEvent
    data class CancelTrip(val tripId: String) : ScheduleEvent
    data object Back : ScheduleEvent
    data object ConsumeError : ScheduleEvent
    data object ConsumeInfo : ScheduleEvent
    data object ConsumeExit : ScheduleEvent
}

private const val SEARCH_DEBOUNCE_MS = 300L

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val getCities: GetC2cCitiesUseCase,
    private val getQuote: GetC2cQuoteUseCase,
    private val createScheduledTrip: CreateScheduledTripUseCase,
    private val getMyUpcoming: GetMyUpcomingUseCase,
    private val cancelScheduledTrip: CancelScheduledTripUseCase,
    private val getRideCategories: GetRideCategoriesUseCase,
    private val getCabTypes: GetCabTypesUseCase,
    private val autocomplete: AutocompletePlacesUseCase,
    private val placeDetails: PlaceDetailsUseCase,
    private val currentLocation: CurrentLocationUseCase,
    private val reverseGeocode: ReverseGeocodeUseCase,
    private val getRoute: GetRouteUseCase,
    private val getBalance: GetBalanceUseCase,
    private val getTripBids: GetTripBidsUseCase,
    private val selectBidUseCase: SelectBidUseCase,
    private val rejectBidUseCase: RejectBidUseCase,
    private val raiseOfferUseCase: RaiseOfferUseCase,
    private val socketService: SocketService,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var bidsPollJob: Job? = null
    /** The SCHEDULE service category id (to list the City-to-City cab types). */
    private var scheduleCategoryId: String? = null

    init {
        socketService.connect()
        loadCities()
        loadScheduleCategory()
        // Decide the landing: show the Upcoming hub if the rider has scheduled rides, else go straight to
        // the new-ride flow.
        loadUpcoming(initialRoute = true)
        // Live competing bids / match for the open trip the rider is watching.
        viewModelScope.launch {
            socketService.v2Events.collect { event ->
                val tripId = _state.value.biddingTripId
                when (event) {
                    is V2SocketEvent.BidsUpdate -> if (event.tripId == tripId) {
                        _state.update { it.copy(bids = event.bids) }
                    }
                    is V2SocketEvent.BidAccepted -> if (event.tripId == tripId) onMatched(event.bidId, event.agreedFare)
                    else -> Unit
                }
            }
        }
    }

    fun onEvent(event: ScheduleEvent) {
        when (event) {
            is ScheduleEvent.SetDateTime -> _state.update { it.copy(departureAtMillis = event.millis) }
            ScheduleEvent.ConfirmDateTime -> _state.update { it.copy(step = ScheduleStep.OriginCity) }
            is ScheduleEvent.SelectOriginCity -> selectOriginCity(event.city)
            is ScheduleEvent.SelectDestinationCity -> selectDestinationCity(event.city)
            is ScheduleEvent.QueryChanged -> onQueryChanged(event.value)
            is ScheduleEvent.SelectSuggestion -> selectSuggestion(event.suggestion)
            ScheduleEvent.UseCurrentLocation -> useCurrentLocation()
            ScheduleEvent.LocateMe -> locateMe()
            ScheduleEvent.OpenMapPicker -> openMapPicker()
            is ScheduleEvent.ConfirmMapLocation -> confirmMapLocation(event.latitude, event.longitude)
            is ScheduleEvent.SelectCab -> selectCab(event.cabId)
            is ScheduleEvent.SetSeats -> setSeats(event.seats)
            is ScheduleEvent.SubmitOffer -> submitOffer(event.fare)
            ScheduleEvent.GoToUpcoming -> openUpcomingList()
            ScheduleEvent.Finish -> finish()
            ScheduleEvent.RefreshUpcoming -> loadUpcoming()
            is ScheduleEvent.OpenUpcoming -> openUpcoming(event.trip)
            ScheduleEvent.CreateNew -> resetForNewRide()
            is ScheduleEvent.SelectBid -> selectBid(event.bidId)
            is ScheduleEvent.RejectBid -> rejectBid(event.bidId)
            is ScheduleEvent.RaiseOffer -> raiseOffer(event.fare)
            is ScheduleEvent.CancelTrip -> cancelTrip(event.tripId)
            ScheduleEvent.Back -> back()
            ScheduleEvent.ConsumeError -> _state.update { it.copy(errorMessage = null) }
            ScheduleEvent.ConsumeInfo -> _state.update { it.copy(infoMessage = null) }
            ScheduleEvent.ConsumeExit -> _state.update { it.copy(exitRequested = false) }
        }
    }

    private fun loadCities() {
        _state.update { it.copy(isLoadingCities = true) }
        viewModelScope.launch {
            when (val result = getCities()) {
                is ApiResult.Success -> _state.update { it.copy(isLoadingCities = false, cities = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isLoadingCities = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingCities = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Find the SCHEDULE service category so its cab types (City to City) can be listed on Review. */
    private fun loadScheduleCategory() {
        viewModelScope.launch {
            when (val result = getRideCategories()) {
                is ApiResult.Success -> scheduleCategoryId = result.data.firstOrNull { it.isSchedule }?.id
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    private fun selectOriginCity(city: C2cCity) {
        _state.update {
            it.copy(originCity = city, step = ScheduleStep.OriginPoint, query = "", suggestions = emptyList())
        }
    }

    private fun selectDestinationCity(city: C2cCity) {
        // Guard against same origin/destination locally (server also returns SAME_ORIGIN_DESTINATION).
        if (city.id == _state.value.originCity?.id) {
            _state.update { it.copy(errorMessage = CODE_SAME_OD) }
            return
        }
        _state.update {
            it.copy(destinationCity = city, step = ScheduleStep.DestPoint, query = "", suggestions = emptyList())
        }
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

    private fun locateMe() {
        viewModelScope.launch {
            when (val result = currentLocation()) {
                is ApiResult.Success -> _state.update { it.copy(myLocation = result.data) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    /** Open the full-screen map picker, remembering which point step (pickup/dropoff) we came from. */
    private fun openMapPicker() {
        _state.update {
            if (it.step == ScheduleStep.OriginPoint || it.step == ScheduleStep.DestPoint) {
                it.copy(mapPickerOrigin = it.step, step = ScheduleStep.MapPicker)
            } else {
                it
            }
        }
    }

    /** Reverse-geocode the chosen map centre, then apply it as the pickup/dropoff. */
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

    /**
     * A picked place becomes the pickup (OriginPoint) or dropoff (DestPoint); advances the flow. When the
     * place came from the map picker, resolve against the point step the picker was opened from.
     */
    private fun applyResolvedPlace(place: PlaceLocation) {
        val pointStep = if (_state.value.step == ScheduleStep.MapPicker) _state.value.mapPickerOrigin else _state.value.step
        when (pointStep) {
            ScheduleStep.OriginPoint -> _state.update {
                it.copy(isResolving = false, pickup = place, step = ScheduleStep.DestCity, query = "", suggestions = emptyList())
            }
            ScheduleStep.DestPoint -> {
                _state.update {
                    it.copy(isResolving = false, dropoff = place, step = ScheduleStep.Review, query = "", suggestions = emptyList())
                }
                onEnterReview()
            }
            else -> _state.update { it.copy(isResolving = false) }
        }
    }

    /** On Review: draw the route, list the C2C cab(s), fetch the quote, and load the wallet balance. */
    private fun onEnterReview() {
        fetchRoute()
        loadBalance()
        loadCabs()
    }

    private fun fetchRoute() {
        val pickup = _state.value.pickup ?: return
        val dropoff = _state.value.dropoff ?: return
        viewModelScope.launch {
            val points = when (val result = getRoute(pickup, dropoff)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error, is ApiResult.Failure -> emptyList()
            }
            val path = points.ifEmpty {
                listOf(LatLngPoint(pickup.latitude, pickup.longitude), LatLngPoint(dropoff.latitude, dropoff.longitude))
            }
            _state.update { it.copy(routePoints = path) }
        }
    }

    private fun loadBalance() {
        viewModelScope.launch {
            when (val result = getBalance()) {
                is ApiResult.Success -> _state.update { it.copy(balance = result.data) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    private fun loadCabs() {
        val pickup = _state.value.pickup ?: return
        val dropoff = _state.value.dropoff ?: return
        _state.update { it.copy(isLoadingCabs = true) }
        viewModelScope.launch {
            when (val result = getCabTypes(pickup, dropoff, scheduleCategoryId)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoadingCabs = false,
                            cabs = result.data,
                            selectedCabId = it.selectedCabId ?: result.data.firstOrNull()?.id,
                        )
                    }
                    loadQuote()
                }
                is ApiResult.Error -> _state.update { it.copy(isLoadingCabs = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingCabs = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Fetch the fare band for the selected (origin, destination, cab, seats); seed the offer at recommended. */
    private fun loadQuote() {
        val s = _state.value
        val origin = s.originCity ?: return
        val dest = s.destinationCity ?: return
        val cab = s.selectedCab ?: return
        _state.update { it.copy(isLoadingQuote = true) }
        viewModelScope.launch {
            when (val result = getQuote(origin.id, dest.id, cab.id, s.seats)) {
                is ApiResult.Success -> _state.update { st ->
                    val q = result.data
                    val seeded = st.offeredFare?.coerceIn(q.minFare, q.maxFare) ?: q.recommendedFare
                    st.copy(isLoadingQuote = false, quote = q, offeredFare = seeded)
                }
                is ApiResult.Error -> _state.update { it.copy(isLoadingQuote = false, quote = null, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingQuote = false, quote = null, errorMessage = result.error.message) }
            }
        }
    }

    private fun selectCab(cabId: String) {
        if (cabId == _state.value.selectedCabId) return
        _state.update { it.copy(selectedCabId = cabId, offeredFare = null) }
        loadQuote()
    }

    private fun setSeats(seats: Int) {
        val clamped = seats.coerceIn(1, MAX_SEATS)
        if (clamped == _state.value.seats) return
        _state.update { it.copy(seats = clamped, offeredFare = null) }
        loadQuote()
    }

    /** "Find Offers" → create the open scheduled C2C request, then show the confirmation. */
    private fun submitOffer(fare: Double) {
        val s = _state.value
        val cab = s.selectedCab ?: return
        val origin = s.originCity ?: return
        val dest = s.destinationCity ?: return
        val pickup = s.pickup ?: return
        val dropoff = s.dropoff ?: return
        val departure = s.departureAtMillis ?: return
        if (s.isCreating) return
        socketService.connect()
        _state.update { it.copy(isCreating = true, offeredFare = fare, errorMessage = null) }
        viewModelScope.launch {
            val result = createScheduledTrip(
                cabId = cab.id,
                originCityId = origin.id,
                destinationCityId = dest.id,
                pickup = pickup,
                dropoff = dropoff,
                scheduledDepartureAtIso = toIsoUtc(departure),
                seats = s.seats,
                riderOfferedFare = fare,
                paymentMethod = PAYMENT_METHOD_WALLET,
                cardId = null,
            )
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(isCreating = false, createdTrip = result.data, step = ScheduleStep.Confirmation)
                }
                is ApiResult.Error -> _state.update { it.copy(isCreating = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isCreating = false, errorMessage = result.error.message) }
            }
        }
    }

    // ---- Upcoming ----

    private fun openUpcomingList() {
        _state.update { it.copy(step = ScheduleStep.Upcoming) }
        loadUpcoming()
    }

    /** "Done" on the confirmation → leave to the rider home, leaving the flow parked on the Upcoming hub. */
    private fun finish() {
        stopBidsPolling()
        _state.update { it.copy(step = ScheduleStep.Upcoming, exitRequested = true) }
        loadUpcoming()
    }

    /**
     * Loads the upcoming list. When [initialRoute] (the first load on entry) finds no scheduled rides, the
     * flow jumps straight to the new-ride [ScheduleStep.DateTime] step instead of showing an empty hub.
     */
    private fun loadUpcoming(initialRoute: Boolean = false) {
        _state.update { it.copy(isLoadingUpcoming = true) }
        viewModelScope.launch {
            when (val result = getMyUpcoming()) {
                is ApiResult.Success -> _state.update {
                    val empty = result.data.isEmpty()
                    it.copy(
                        isLoadingUpcoming = false,
                        upcoming = result.data,
                        step = if (initialRoute && empty) ScheduleStep.DateTime else it.step,
                    )
                }
                // On a failed initial load, default to the new-ride flow so the rider can still book.
                is ApiResult.Error -> _state.update {
                    it.copy(isLoadingUpcoming = false, step = if (initialRoute) ScheduleStep.DateTime else it.step)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(isLoadingUpcoming = false, step = if (initialRoute) ScheduleStep.DateTime else it.step)
                }
            }
        }
    }

    /** Open a future trip from the upcoming list: open → bidding/offers; matched/reminded → waiting card. */
    private fun openUpcoming(trip: UpcomingScheduledTrip) {
        when (trip.scheduledState) {
            ScheduledTripState.Open -> enterBidding(
                tripId = trip.tripId,
                offeredFare = trip.riderOfferedFare,
                maxFare = trip.riderOfferedFare * RESTORE_MAX_FACTOR,
                departureMillis = trip.scheduledDepartureAtMillis,
            )
            ScheduledTripState.Matched, ScheduledTripState.Reminded -> enterWaiting(trip)
            else -> Unit // expired/cancelled/activated have no scheduled screen
        }
    }

    private fun resetForNewRide() {
        stopBidsPolling()
        _state.update {
            ScheduleUiState(
                step = ScheduleStep.DateTime,
                // Entered from the hub → back from the booking returns to the hub.
                startedFromHub = true,
                cities = it.cities,
                myLocation = it.myLocation,
            )
        }
    }

    // ---- Bidding (reuses the V2 bidding engine) ----

    private fun enterBidding(tripId: String, offeredFare: Double, maxFare: Double, departureMillis: Long?) {
        socketService.connect()
        _state.update {
            it.copy(
                step = ScheduleStep.Bidding,
                biddingTripId = tripId,
                biddingOfferedFare = offeredFare,
                biddingMaxFare = if (maxFare > offeredFare) maxFare else offeredFare * RESTORE_MAX_FACTOR,
                biddingDepartureMillis = departureMillis,
                bids = emptyList(),
            )
        }
        startBidsPolling(tripId)
    }

    private fun startBidsPolling(tripId: String) {
        bidsPollJob?.cancel()
        bidsPollJob = viewModelScope.launch {
            while (_state.value.step == ScheduleStep.Bidding && _state.value.biddingTripId == tripId) {
                fetchBids(tripId)
                delay(BID_POLL_MS)
            }
        }
    }

    private fun stopBidsPolling() {
        bidsPollJob?.cancel()
        bidsPollJob = null
    }

    private fun fetchBids(tripId: String) {
        viewModelScope.launch {
            when (val result = getTripBids(tripId)) {
                is ApiResult.Success -> _state.update { if (it.biddingTripId == tripId) it.copy(bids = result.data) else it }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    private fun selectBid(bidId: String) {
        val tripId = _state.value.biddingTripId ?: return
        if (_state.value.isBidActionInFlight) return
        _state.update { it.copy(isBidActionInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = selectBidUseCase(tripId, bidId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isBidActionInFlight = false) }
                    onMatched(bidId, result.data.agreedFare)
                }
                is ApiResult.Error -> _state.update { it.copy(isBidActionInFlight = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isBidActionInFlight = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun rejectBid(bidId: String) {
        val tripId = _state.value.biddingTripId ?: return
        _state.update { it.copy(bids = it.bids.filterNot { b -> b.bidId == bidId }) }
        viewModelScope.launch { rejectBidUseCase(tripId, bidId) }
    }

    private fun raiseOffer(fare: Double) {
        val tripId = _state.value.biddingTripId ?: return
        if (_state.value.isBidActionInFlight) return
        _state.update { it.copy(isBidActionInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = raiseOfferUseCase(tripId, fare)) {
                is ApiResult.Success -> _state.update { it.copy(isBidActionInFlight = false, biddingOfferedFare = fare) }
                is ApiResult.Error -> _state.update { it.copy(isBidActionInFlight = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isBidActionInFlight = false, errorMessage = result.error.message) }
            }
        }
    }

    /** A bid was selected/accepted → switch to the matched/waiting card (resolve the driver from the bid). */
    private fun onMatched(bidId: String, agreedFare: Double) {
        stopBidsPolling()
        val s = _state.value
        val bid = s.bids.firstOrNull { it.bidId == bidId }
        _state.update {
            it.copy(
                step = ScheduleStep.Waiting,
                matchedTripId = it.biddingTripId,
                matchedBid = bid,
                matchedFare = if (agreedFare > 0) agreedFare else (bid?.fare ?: it.biddingOfferedFare),
                matchedDepartureMillis = it.biddingDepartureMillis,
                matchedDriverId = bid?.driverId,
            )
        }
    }

    /** Show the matched/waiting card for a trip opened from the upcoming list (resolve driver from accepted bid). */
    private fun enterWaiting(trip: UpcomingScheduledTrip) {
        _state.update {
            it.copy(
                step = ScheduleStep.Waiting,
                matchedTripId = trip.tripId,
                matchedBid = null,
                matchedFare = trip.riderOfferedFare,
                matchedDepartureMillis = trip.scheduledDepartureAtMillis,
                matchedDriverId = trip.driverId,
            )
        }
        viewModelScope.launch {
            when (val result = getTripBids(trip.tripId)) {
                is ApiResult.Success -> {
                    val accepted = result.data.firstOrNull { it.status == BidStatus.Accepted }
                    if (accepted != null) {
                        _state.update {
                            if (it.matchedTripId == trip.tripId) {
                                it.copy(matchedBid = accepted, matchedFare = accepted.fare, matchedDriverId = accepted.driverId)
                            } else {
                                it
                            }
                        }
                    }
                }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    // ---- Cancel ----

    private fun cancelTrip(tripId: String) {
        if (_state.value.isCancelling) return
        _state.update { it.copy(isCancelling = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = cancelScheduledTrip(tripId, null)) {
                is ApiResult.Success -> {
                    stopBidsPolling()
                    val fee = result.data.feeApplied
                    _state.update {
                        it.copy(
                            isCancelling = false,
                            infoMessage = if (fee) INFO_CANCELLED_FEE else INFO_CANCELLED_FREE,
                            // Drop the cancelled trip from the bidding/waiting screens and refresh upcoming.
                            biddingTripId = null,
                            matchedTripId = null,
                            step = ScheduleStep.Upcoming,
                        )
                    }
                    loadUpcoming()
                }
                is ApiResult.Error -> _state.update { it.copy(isCancelling = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isCancelling = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun back() {
        stopBidsPollingIfLeavingBidding()
        _state.update { s ->
            val previous = when (s.step) {
                // Upcoming is the hub → back leaves the flow to the rider home.
                ScheduleStep.Upcoming -> { return@update s.copy(exitRequested = true) }
                // DateTime: back to the hub if we came from it, otherwise (entered directly because there
                // were no upcoming rides) leave to the rider home.
                ScheduleStep.DateTime ->
                    if (s.startedFromHub) ScheduleStep.Upcoming else { return@update s.copy(exitRequested = true) }
                ScheduleStep.OriginCity -> ScheduleStep.DateTime
                ScheduleStep.OriginPoint -> ScheduleStep.OriginCity
                ScheduleStep.DestCity -> ScheduleStep.OriginPoint
                ScheduleStep.DestPoint -> ScheduleStep.DestCity
                ScheduleStep.Review -> ScheduleStep.DestPoint
                // Map picker returns to the point step it was opened from.
                ScheduleStep.MapPicker -> s.mapPickerOrigin
                // Post-create / lifecycle screens fall back to the hub.
                ScheduleStep.Confirmation -> ScheduleStep.Upcoming
                ScheduleStep.Bidding -> ScheduleStep.Upcoming
                ScheduleStep.Waiting -> ScheduleStep.Upcoming
            }
            s.copy(step = previous, query = "", suggestions = emptyList(), errorMessage = null)
        }
        // Reaching Upcoming via back from a lifecycle screen should refresh the list.
        if (_state.value.step == ScheduleStep.Upcoming) loadUpcoming()
    }

    private fun stopBidsPollingIfLeavingBidding() {
        if (_state.value.step == ScheduleStep.Bidding) stopBidsPolling()
    }

    private fun toIsoUtc(millis: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return fmt.format(java.util.Date(millis))
    }

    companion object {
        const val MIN_LEAD_MINUTES = 60
        const val MAX_LEAD_DAYS = 30
        const val MAX_SEATS = 6
        const val PAYMENT_METHOD_WALLET = 2
        private const val BID_POLL_MS = 3_000L
        private const val RESTORE_MAX_FACTOR = 2.0
        const val INFO_CANCELLED_FREE = "CANCELLED_FREE"
        const val INFO_CANCELLED_FEE = "CANCELLED_FEE"
        const val CODE_SAME_OD = "SAME_ORIGIN_DESTINATION"
    }
}
