package com.mytm.darrbi.presentation.rider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.BookedTrip
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.repository.TripSocketEvent
import com.mytm.darrbi.domain.usecase.AutocompletePlacesUseCase
import com.mytm.darrbi.domain.usecase.CancelTripUseCase
import com.mytm.darrbi.domain.usecase.CreateTripUseCase
import com.mytm.darrbi.domain.usecase.CurrentLocationUseCase
import com.mytm.darrbi.domain.usecase.GetBalanceUseCase
import com.mytm.darrbi.domain.usecase.GetRouteUseCase
import com.mytm.darrbi.domain.usecase.GetCabTypesUseCase
import com.mytm.darrbi.domain.usecase.PlaceDetailsUseCase
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
enum class RiderStep { Home, DestinationSearch, PickupSearch, MapPicker, ConfirmPickup, SelectRide, Searching }

data class RiderBookingUiState(
    val step: RiderStep = RiderStep.Home,
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
    val errorMessage: String? = null,
) {
    val selectedCab: CabOption? get() = cabs.firstOrNull { it.id == selectedCabId }
    /** Fare after any applied promo discount (never below zero). */
    val payableFare: Double get() = ((selectedCab?.fare ?: 0.0) - (promo?.discount ?: 0.0)).coerceAtLeast(0.0)
    /** A zero (or not-yet-loaded) wallet balance below the fare means the rider must top up first. */
    val isBalanceInsufficient: Boolean get() = selectedCab != null && (balance ?: 0.0) < payableFare
}

sealed interface RiderBookingEvent {
    data object OpenDestinationSearch : RiderBookingEvent
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
    /** Re-fetch the wallet balance (e.g. after a successful top-up). */
    data object RefreshBalance : RiderBookingEvent
    /** Retry after no captain was found — returns to ride selection to re-request (ride-android: back). */
    data object TryAgainSearch : RiderBookingEvent
    /** Cancel Ride on the searching screen — cancels the active request server-side, then goes home. */
    data object CancelRide : RiderBookingEvent
    data object Back : RiderBookingEvent
    data object ConsumeError : RiderBookingEvent
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
    private val socketService: SocketService,
) : ViewModel() {

    private val _state = MutableStateFlow(RiderBookingUiState())
    val state: StateFlow<RiderBookingUiState> = _state.asStateFlow()

    init {
        // Rider reached the dashboard → open the real-time socket.
        socketService.connect()
        // While searching, a `no_drivers` (no captain accepted) or `trip_expired` push ends the search →
        // show the "try again" state (same UI as ride-android, which reuses the no-captain screen).
        viewModelScope.launch {
            socketService.tripEvents.collect { event ->
                when (event) {
                    TripSocketEvent.NoCaptainFound, TripSocketEvent.TripExpired -> failSearch()
                }
            }
        }
    }

    private var searchJob: Job? = null
    private var searchTimeoutJob: Job? = null

    fun onEvent(event: RiderBookingEvent) {
        when (event) {
            RiderBookingEvent.OpenDestinationSearch ->
                // New booking → drop any previous route so a stale polyline never lingers.
                _state.update { it.copy(step = RiderStep.DestinationSearch, query = "", suggestions = emptyList(), routePoints = emptyList()) }
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
            RiderBookingEvent.ConfirmRide -> confirmRide()
            RiderBookingEvent.RefreshBalance -> loadBalance()
            RiderBookingEvent.TryAgainSearch -> tryAgainSearch()
            RiderBookingEvent.CancelRide -> cancelRide()
            RiderBookingEvent.Back -> back()
            RiderBookingEvent.ConsumeError -> _state.update { it.copy(errorMessage = null) }
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

    /** Centre the home map on the device location (no flow change); ignores failures silently. */
    private fun locateMe() {
        viewModelScope.launch {
            when (val result = currentLocation()) {
                is ApiResult.Success -> _state.update { it.copy(myLocation = result.data) }
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
        _state.update { state ->
            // From the map picker, the place applies to whichever search opened it.
            val target = if (state.step == RiderStep.MapPicker) state.mapPickerOrigin else state.step
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
        _state.update { it.copy(step = RiderStep.SelectRide, isLoadingCabs = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getCabTypes(pickup, destination)) {
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
                errorMessage = null,
            )
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
                RiderStep.Searching -> RiderStep.Home
            }
            state.copy(step = previous, query = "", suggestions = emptyList(), noCaptainFound = false, errorMessage = null)
        }
    }

    private companion object {
        const val DEFAULT_SEARCH_TIMEOUT_MS = 45_000L
        const val MIN_SEARCH_TIMEOUT_MS = 5_000L
        const val MAX_SEARCH_TIMEOUT_MS = 180_000L
    }
}
