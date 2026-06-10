package com.mytm.darrbi.presentation.rider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.BookedTrip
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.domain.usecase.AutocompletePlacesUseCase
import com.mytm.darrbi.domain.usecase.CreateTripUseCase
import com.mytm.darrbi.domain.usecase.CurrentLocationUseCase
import com.mytm.darrbi.domain.usecase.GetBalanceUseCase
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
    data object OpenMapPicker : RiderBookingEvent
    data class ConfirmMapLocation(val latitude: Double, val longitude: Double) : RiderBookingEvent
    data object EditPickup : RiderBookingEvent
    data object ProceedToRideSelection : RiderBookingEvent
    data class SelectCab(val cabId: String) : RiderBookingEvent
    data class ApplyPromo(val code: String) : RiderBookingEvent
    data object RemovePromo : RiderBookingEvent
    data object ConfirmRide : RiderBookingEvent
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
    private val getBalance: GetBalanceUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RiderBookingUiState())
    val state: StateFlow<RiderBookingUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onEvent(event: RiderBookingEvent) {
        when (event) {
            RiderBookingEvent.OpenDestinationSearch ->
                _state.update { it.copy(step = RiderStep.DestinationSearch, query = "", suggestions = emptyList()) }
            is RiderBookingEvent.QueryChanged -> onQueryChanged(event.value)
            is RiderBookingEvent.SelectSuggestion -> selectSuggestion(event.suggestion)
            RiderBookingEvent.UseCurrentLocation -> useCurrentLocation()
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
        _state.update { it.copy(isBooking = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = createTrip(pickup, destination, cab.id, state.promo?.code)) {
                is ApiResult.Success -> _state.update {
                    it.copy(isBooking = false, bookedTrip = result.data, step = RiderStep.Searching)
                }
                is ApiResult.Error -> _state.update { it.copy(isBooking = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isBooking = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun back() {
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
            state.copy(step = previous, query = "", suggestions = emptyList(), errorMessage = null)
        }
    }
}
