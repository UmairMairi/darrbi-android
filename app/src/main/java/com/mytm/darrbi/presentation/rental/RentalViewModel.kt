package com.mytm.darrbi.presentation.rental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.SaudiId
import com.mytm.darrbi.domain.model.ExtensionResult
import com.mytm.darrbi.domain.model.NafathResult
import com.mytm.darrbi.domain.model.RentalBooking
import com.mytm.darrbi.domain.model.RentalBookingDetail
import com.mytm.darrbi.domain.model.RentalFilters
import com.mytm.darrbi.domain.model.RentalQuote
import com.mytm.darrbi.domain.model.RentalSort
import com.mytm.darrbi.domain.model.RentalVehicleDetail
import com.mytm.darrbi.domain.model.RentalVehicleSummary
import com.mytm.darrbi.domain.repository.SessionRepository
import com.mytm.darrbi.domain.usecase.CancelRentalBookingUseCase
import com.mytm.darrbi.domain.usecase.CreateRentalBookingUseCase
import com.mytm.darrbi.domain.usecase.ExtendRentalBookingUseCase
import com.mytm.darrbi.domain.usecase.GetRentalBookingUseCase
import com.mytm.darrbi.domain.usecase.GetRentalBookingsUseCase
import com.mytm.darrbi.domain.usecase.GetRentalFiltersUseCase
import com.mytm.darrbi.domain.usecase.GetRentalQuoteUseCase
import com.mytm.darrbi.domain.usecase.GetRentalVehicleUseCase
import com.mytm.darrbi.domain.usecase.RateRentalBookingUseCase
import com.mytm.darrbi.domain.usecase.SearchRentalVehiclesUseCase
import com.mytm.darrbi.domain.usecase.VerifyNafathUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steps of the self-drive car-rental flow (see `RAC_MOBILE_INTEGRATION_GUIDE.md`). All REST, no socket.
 * `Search → Results → Detail → Reserve → Confirmation`; the renter manages existing bookings on the
 * `Bookings` hub and tracks/cancels/extends/rates one on `BookingDetail`. Quote/booking are re-priced
 * server-side, so the client never trusts its own math.
 */
enum class RentalStep { Search, Results, Detail, Reserve, Confirmation, Bookings, BookingDetail }

data class RentalUiState(
    val step: RentalStep = RentalStep.Search,
    // Filters / search criteria.
    val filters: RentalFilters? = null,
    val selectedCity: String? = null,
    val pickupAtMillis: Long? = null,
    val returnAtMillis: Long? = null,
    val selectedCategory: String? = null,
    val selectedSeats: Int? = null,
    val selectedTransmission: String? = null,
    val sortBy: RentalSort? = null,
    // Overlays.
    val showDateSheet: Boolean = false,
    val showDobSheet: Boolean = false,
    val showFilterSheet: Boolean = false,
    val showSortSheet: Boolean = false,
    // Results.
    val vehicles: List<RentalVehicleSummary> = emptyList(),
    val total: Int = 0,
    val isSearching: Boolean = false,
    // Detail.
    val vehicle: RentalVehicleDetail? = null,
    val isLoadingDetail: Boolean = false,
    // Reserve / quote.
    val cdwTier: String? = null,
    val couponInput: String = "",
    val appliedCoupon: String? = null,
    val renterNid: String = "",
    val renterLicense: String = "",
    val renterName: String = "",
    /** Formatted DOB shown in the field (dd/MM/yyyy); chosen via the calendar sheet, never typed. */
    val renterDob: String = "",
    /** Day-start epoch millis of the chosen DOB — re-seeds the calendar sheet when re-editing. */
    val renterDobMillis: Long? = null,
    val renterPhone: String = "",
    /** Dial code shown before the phone (e.g. "+966"); follows the account number's country when pre-filled. */
    val renterPhoneCc: String = DEFAULT_PHONE_CC,
    /** True once the phone has been pre-filled from the signed-in account; the field is then read-only. */
    val phoneFromAccount: Boolean = false,
    val renterEmail: String = "",
    val termsAccepted: Boolean = false,
    val quote: RentalQuote? = null,
    val isLoadingQuote: Boolean = false,
    val nafathVerified: Boolean = false,
    val isVerifyingNafath: Boolean = false,
    val isBooking: Boolean = false,
    val createdBooking: RentalBooking? = null,
    // Bookings hub.
    val bookings: List<RentalBooking> = emptyList(),
    val isLoadingBookings: Boolean = false,
    // Booking detail.
    val bookingDetail: RentalBookingDetail? = null,
    val isLoadingBookingDetail: Boolean = false,
    val isActionInFlight: Boolean = false,
    val showExtendSheet: Boolean = false,
    val showRateSheet: Boolean = false,
    // One-shots.
    val exitRequested: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    /** Dates chosen and valid (return strictly after pickup) → the Search CTA is enabled. */
    val datesValid: Boolean
        get() {
            val p = pickupAtMillis
            val r = returnAtMillis
            return p != null && r != null && r > p
        }

    /** CDW tier, defaulting to the cheapest waiver the vehicle offers when nothing is picked yet. */
    fun effectiveCdwTier(): String? = cdwTier ?: vehicle?.let { v ->
        v.cdwTiers.firstOrNull { it.tier.equals("basic", ignoreCase = true) }?.tier
            ?: v.cdwTiers.firstOrNull()?.tier
    }

    // ---- Field validation (mirrors the onboarding rules) ----

    /** Saudi NID/Iqama: exactly 10 digits, starting 1 (citizen) or 2 (resident). Required + sent to Nafath. */
    val nidValid: Boolean get() = SaudiId.isValid(renterNid)

    /**
     * Inline NID/Iqama error. Surfaces early when the first digit isn't a valid prefix (1 = NID, 2 = Iqama)
     * so the renter gets immediate feedback, and again once a full 10-digit number is entered that still
     * fails the rule. A correct prefix that's simply not finished typing stays error-free.
     */
    val nidError: Boolean
        get() = renterNid.isNotEmpty() && !nidValid &&
            (renterNid.first() !in '1'..'2' || renterNid.length >= 10)

    val licenseValid: Boolean get() = renterLicense.isNotBlank()

    /** Phone is optional here (the server takes it from the account). A number pulled from the verified
     *  account is trusted as-is (any country); a manually-typed one must be a Saudi mobile — 9 local digits
     *  starting with 5 (after the +966 country code), matching onboarding. */
    val phoneValid: Boolean get() = when {
        renterPhone.isEmpty() -> true
        phoneFromAccount -> true
        else -> renterPhone.length == 9 && renterPhone.startsWith("5")
    }
    val phoneError: Boolean get() = renterPhone.isNotEmpty() && !phoneValid

    /** Email is optional; if entered it must be well-formed. */
    val emailValid: Boolean get() = renterEmail.isBlank() || RAC_EMAIL_REGEX.matches(renterEmail.trim())
    val emailError: Boolean get() = renterEmail.isNotBlank() && !emailValid

    val canReserve: Boolean
        get() = termsAccepted && nidValid && licenseValid && phoneValid && emailValid &&
            quote?.available == true && !isBooking && !isVerifyingNafath
}

/** Basic well-formed-email check (kept local so it works without the Android runtime in tests). */
private val RAC_EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/** Saudi dial code shown by default when the renter has no account number to pre-fill. */
private const val DEFAULT_PHONE_CC = "+966"

sealed interface RentalEvent {
    data class SelectCity(val city: String) : RentalEvent
    data object OpenDateSheet : RentalEvent
    data object DismissDateSheet : RentalEvent
    data class SetDates(val pickupMillis: Long, val returnMillis: Long) : RentalEvent
    data object Search : RentalEvent
    data object OpenFilter : RentalEvent
    data object OpenSort : RentalEvent
    data object DismissSheets : RentalEvent
    data class ApplyFilter(
        val category: String?,
        val seats: Int?,
        val transmission: String?,
    ) : RentalEvent
    data class SetSort(val sort: RentalSort?) : RentalEvent
    data class OpenVehicle(val vehicleId: String) : RentalEvent
    data object Continue : RentalEvent
    data class SetCdwTier(val tier: String) : RentalEvent
    data class CouponChanged(val value: String) : RentalEvent
    data object ApplyCoupon : RentalEvent
    data class SetNid(val value: String) : RentalEvent
    data class SetLicense(val value: String) : RentalEvent
    data class SetName(val value: String) : RentalEvent
    data object OpenDobSheet : RentalEvent
    data object DismissDobSheet : RentalEvent
    /** A day-of-birth was picked on the calendar sheet (day-start epoch millis). */
    data class SetDob(val millis: Long) : RentalEvent
    data class SetPhone(val value: String) : RentalEvent
    data class SetEmail(val value: String) : RentalEvent
    data class SetTerms(val accepted: Boolean) : RentalEvent
    data object ReserveNow : RentalEvent
    data object Done : RentalEvent
    data object OpenBookings : RentalEvent
    data object RefreshBookings : RentalEvent
    data class OpenBooking(val bookingId: String) : RentalEvent
    data object RefreshBooking : RentalEvent
    data object CreateNew : RentalEvent
    data object CancelBooking : RentalEvent
    data object OpenExtend : RentalEvent
    data class RequestExtend(val additionalHours: Int) : RentalEvent
    data object OpenRate : RentalEvent
    data class SubmitRate(val rating: Int, val text: String?) : RentalEvent
    data object DismissBookingSheets : RentalEvent
    data object Back : RentalEvent
    data object ConsumeError : RentalEvent
    data object ConsumeInfo : RentalEvent
    data object ConsumeExit : RentalEvent
}

@HiltViewModel
class RentalViewModel @Inject constructor(
    private val getFilters: GetRentalFiltersUseCase,
    private val searchVehicles: SearchRentalVehiclesUseCase,
    private val getVehicle: GetRentalVehicleUseCase,
    private val getQuote: GetRentalQuoteUseCase,
    private val verifyNafath: VerifyNafathUseCase,
    private val createBooking: CreateRentalBookingUseCase,
    private val getBookings: GetRentalBookingsUseCase,
    private val getBooking: GetRentalBookingUseCase,
    private val cancelBooking: CancelRentalBookingUseCase,
    private val extendBooking: ExtendRentalBookingUseCase,
    private val rateBooking: RateRentalBookingUseCase,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RentalUiState())
    val state: StateFlow<RentalUiState> = _state.asStateFlow()

    private var quoteJob: Job? = null

    init {
        loadFilters()
    }

    fun onEvent(event: RentalEvent) {
        when (event) {
            is RentalEvent.SelectCity -> _state.update { it.copy(selectedCity = event.city) }
            RentalEvent.OpenDateSheet -> _state.update { it.copy(showDateSheet = true) }
            RentalEvent.DismissDateSheet -> _state.update { it.copy(showDateSheet = false) }
            is RentalEvent.SetDates -> _state.update {
                it.copy(pickupAtMillis = event.pickupMillis, returnAtMillis = event.returnMillis, showDateSheet = false)
            }
            RentalEvent.Search -> search()
            RentalEvent.OpenFilter -> _state.update { it.copy(showFilterSheet = true) }
            RentalEvent.OpenSort -> _state.update { it.copy(showSortSheet = true) }
            RentalEvent.DismissSheets -> _state.update { it.copy(showFilterSheet = false, showSortSheet = false) }
            is RentalEvent.ApplyFilter -> {
                _state.update {
                    it.copy(
                        selectedCategory = event.category,
                        selectedSeats = event.seats,
                        selectedTransmission = event.transmission,
                        showFilterSheet = false,
                    )
                }
                search()
            }
            is RentalEvent.SetSort -> {
                _state.update { it.copy(sortBy = event.sort, showSortSheet = false) }
                search()
            }
            is RentalEvent.OpenVehicle -> openVehicle(event.vehicleId)
            RentalEvent.Continue -> enterReserve()
            is RentalEvent.SetCdwTier -> {
                _state.update { it.copy(cdwTier = event.tier) }
                refreshQuote()
            }
            is RentalEvent.CouponChanged -> _state.update { it.copy(couponInput = event.value) }
            RentalEvent.ApplyCoupon -> {
                _state.update { it.copy(appliedCoupon = it.couponInput.trim().takeIf { c -> c.isNotBlank() }) }
                refreshQuote()
            }
            is RentalEvent.SetNid -> _state.update { it.copy(renterNid = event.value.filter(Char::isDigit).take(NID_LENGTH)) }
            is RentalEvent.SetLicense -> _state.update { it.copy(renterLicense = event.value) }
            is RentalEvent.SetName -> _state.update { it.copy(renterName = event.value) }
            RentalEvent.OpenDobSheet -> _state.update { it.copy(showDobSheet = true) }
            RentalEvent.DismissDobSheet -> _state.update { it.copy(showDobSheet = false) }
            is RentalEvent.SetDob -> _state.update {
                it.copy(renterDobMillis = event.millis, renterDob = formatDob(event.millis), showDobSheet = false)
            }
            is RentalEvent.SetPhone -> _state.update { it.copy(renterPhone = event.value.filter(Char::isDigit).take(SA_PHONE_LENGTH)) }
            is RentalEvent.SetEmail -> _state.update { it.copy(renterEmail = event.value) }
            is RentalEvent.SetTerms -> _state.update { it.copy(termsAccepted = event.accepted) }
            RentalEvent.ReserveNow -> reserveNow()
            RentalEvent.Done -> openBookings()
            RentalEvent.OpenBookings -> openBookings()
            RentalEvent.RefreshBookings -> loadBookings()
            is RentalEvent.OpenBooking -> openBooking(event.bookingId)
            RentalEvent.RefreshBooking -> _state.value.bookingDetail?.bookingId?.let { loadBooking(it) }
            RentalEvent.CreateNew -> resetForNewSearch()
            RentalEvent.CancelBooking -> cancelCurrentBooking()
            RentalEvent.OpenExtend -> _state.update { it.copy(showExtendSheet = true) }
            is RentalEvent.RequestExtend -> requestExtend(event.additionalHours)
            RentalEvent.OpenRate -> _state.update { it.copy(showRateSheet = true) }
            is RentalEvent.SubmitRate -> submitRate(event.rating, event.text)
            RentalEvent.DismissBookingSheets -> _state.update { it.copy(showExtendSheet = false, showRateSheet = false) }
            RentalEvent.Back -> back()
            RentalEvent.ConsumeError -> _state.update { it.copy(errorMessage = null) }
            RentalEvent.ConsumeInfo -> _state.update { it.copy(infoMessage = null) }
            RentalEvent.ConsumeExit -> _state.update { it.copy(exitRequested = false) }
        }
    }

    private fun loadFilters() {
        viewModelScope.launch {
            when (val result = getFilters()) {
                is ApiResult.Success -> _state.update {
                    it.copy(filters = result.data, selectedCity = it.selectedCity ?: result.data.cities.firstOrNull())
                }
                is ApiResult.Error, is ApiResult.Failure -> Unit // search still works without LOVs
            }
        }
    }

    private fun search() {
        val s = _state.value
        _state.update { it.copy(isSearching = true, step = RentalStep.Results, errorMessage = null) }
        viewModelScope.launch {
            val result = searchVehicles(
                city = s.selectedCity,
                category = s.selectedCategory,
                pickupAtIso = s.pickupAtMillis?.let(::toIsoUtc),
                returnAtIso = s.returnAtMillis?.let(::toIsoUtc),
                seats = s.selectedSeats,
                transmission = s.selectedTransmission,
                sortBy = s.sortBy?.wire,
                page = 1,
                limit = DEFAULT_LIMIT,
            )
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(isSearching = false, vehicles = result.data.items, total = result.data.total)
                }
                is ApiResult.Error -> _state.update { it.copy(isSearching = false, errorMessage = errorText(result.code, result.message)) }
                is ApiResult.Failure -> _state.update { it.copy(isSearching = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun openVehicle(vehicleId: String) {
        _state.update { it.copy(step = RentalStep.Detail, isLoadingDetail = true, vehicle = null) }
        viewModelScope.launch {
            when (val result = getVehicle(vehicleId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(isLoadingDetail = false, vehicle = result.data, cdwTier = null)
                }
                is ApiResult.Error -> _state.update { it.copy(isLoadingDetail = false, errorMessage = errorText(result.code, result.message), step = RentalStep.Results) }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingDetail = false, errorMessage = result.error.message, step = RentalStep.Results) }
            }
        }
    }

    private fun enterReserve() {
        if (_state.value.vehicle == null) return
        // Pull the contact number straight from the signed-in account (the server uses it anyway);
        // when present, the field is shown read-only with the account's own country code so the renter
        // doesn't have to re-type it — and it's trusted as-is regardless of country.
        val account = parseAccountPhone(sessionRepository.user?.mobileNo)
        _state.update {
            it.copy(
                step = RentalStep.Reserve,
                quote = null,
                nafathVerified = false,
                renterPhone = account?.second ?: it.renterPhone,
                renterPhoneCc = account?.first ?: DEFAULT_PHONE_CC,
                phoneFromAccount = account != null,
            )
        }
        refreshQuote()
    }

    /**
     * Split a stored `mobileNo` (country code + local digits, e.g. `923434132575` → `+92` / `3434132575`)
     * into a displayable dial code and the local number, using a longest-prefix match over [DIAL_CODES].
     * Returns null when there is no number to pre-fill.
     */
    private fun parseAccountPhone(mobileNo: String?): Pair<String, String>? {
        val digits = mobileNo?.filter(Char::isDigit).orEmpty()
        if (digits.isEmpty()) return null
        val code = DIAL_CODES.firstOrNull { digits.length > it.length && digits.startsWith(it) }
        return if (code != null) "+$code" to digits.removePrefix(code) else DEFAULT_PHONE_CC to digits
    }

    /** Display format for a picked date of birth (matches the existing field, e.g. `10/10/1990`). */
    private fun formatDob(millis: Long): String =
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(millis))

    /** Re-fetch the authoritative quote for the current vehicle + dates + CDW tier (+ coupon). */
    private fun refreshQuote() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        val pickup = s.pickupAtMillis ?: return
        val ret = s.returnAtMillis ?: return
        val tier = s.effectiveCdwTier() ?: return
        quoteJob?.cancel()
        _state.update { it.copy(isLoadingQuote = true) }
        quoteJob = viewModelScope.launch {
            when (val result = getQuote(vehicle.id, toIsoUtc(pickup), toIsoUtc(ret), tier, emptyList(), s.appliedCoupon)) {
                is ApiResult.Success -> _state.update { it.copy(isLoadingQuote = false, quote = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isLoadingQuote = false, quote = null, errorMessage = errorText(result.code, result.message)) }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingQuote = false, quote = null, errorMessage = result.error.message) }
            }
        }
    }

    /** Reserve = (verify Nafath if needed) → create booking (payment + deposit hold happen server-side). */
    private fun reserveNow() {
        val s = _state.value
        if (!s.canReserve) return
        val vehicle = s.vehicle ?: return
        val pickup = s.pickupAtMillis ?: return
        val ret = s.returnAtMillis ?: return
        val tier = s.effectiveCdwTier() ?: return
        viewModelScope.launch {
            // 1. Nafath (guide §3.5) — required before booking.
            if (!s.nafathVerified) {
                _state.update { it.copy(isVerifyingNafath = true, errorMessage = null) }
                when (val nafath = verifyNafath(s.renterNid.trim())) {
                    is ApiResult.Success -> {
                        if (nafath.data.verified) {
                            _state.update { it.copy(isVerifyingNafath = false, nafathVerified = true) }
                        } else {
                            _state.update { it.copy(isVerifyingNafath = false, errorMessage = CODE_NAFATH_FAILED) }
                            return@launch
                        }
                    }
                    is ApiResult.Error -> {
                        _state.update { it.copy(isVerifyingNafath = false, errorMessage = errorText(nafath.code, nafath.message)) }
                        return@launch
                    }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(isVerifyingNafath = false, errorMessage = nafath.error.message) }
                        return@launch
                    }
                }
            }
            // 2. Create booking (guide §3.6) — atomic re-price + lock + capture + deposit hold.
            _state.update { it.copy(isBooking = true, errorMessage = null) }
            val result = createBooking(
                vehicleId = vehicle.id,
                pickupAtIso = toIsoUtc(pickup),
                returnAtIso = toIsoUtc(ret),
                cdwTier = tier,
                addonIds = emptyList(),
                couponCode = s.appliedCoupon,
                nafathVerified = true,
                renterNationalId = s.renterNid.trim(),
                renterLicenseNo = s.renterLicense.trim(),
            )
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(isBooking = false, createdBooking = result.data, step = RentalStep.Confirmation)
                }
                is ApiResult.Error -> _state.update { it.copy(isBooking = false, errorMessage = errorText(result.code, result.message)) }
                is ApiResult.Failure -> _state.update { it.copy(isBooking = false, errorMessage = result.error.message) }
            }
        }
    }

    // ---- Bookings hub ----

    private fun openBookings() {
        _state.update { it.copy(step = RentalStep.Bookings) }
        loadBookings()
    }

    private fun loadBookings() {
        _state.update { it.copy(isLoadingBookings = true) }
        viewModelScope.launch {
            when (val result = getBookings(null)) {
                is ApiResult.Success -> _state.update { it.copy(isLoadingBookings = false, bookings = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isLoadingBookings = false, errorMessage = errorText(result.code, result.message)) }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingBookings = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun openBooking(bookingId: String) {
        _state.update { it.copy(step = RentalStep.BookingDetail, bookingDetail = null) }
        loadBooking(bookingId)
    }

    private fun loadBooking(bookingId: String) {
        _state.update { it.copy(isLoadingBookingDetail = true) }
        viewModelScope.launch {
            when (val result = getBooking(bookingId)) {
                is ApiResult.Success -> _state.update { it.copy(isLoadingBookingDetail = false, bookingDetail = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isLoadingBookingDetail = false, errorMessage = errorText(result.code, result.message)) }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingBookingDetail = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun cancelCurrentBooking() {
        val bookingId = _state.value.bookingDetail?.bookingId ?: return
        if (_state.value.isActionInFlight) return
        _state.update { it.copy(isActionInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = cancelBooking(bookingId, null)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isActionInFlight = false, infoMessage = CODE_CANCELLED) }
                    loadBooking(bookingId)
                }
                is ApiResult.Error -> _state.update { it.copy(isActionInFlight = false, errorMessage = errorText(result.code, result.message)) }
                is ApiResult.Failure -> _state.update { it.copy(isActionInFlight = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun requestExtend(additionalHours: Int) {
        val bookingId = _state.value.bookingDetail?.bookingId ?: return
        if (_state.value.isActionInFlight) return
        _state.update { it.copy(isActionInFlight = true, showExtendSheet = false, errorMessage = null) }
        viewModelScope.launch {
            when (val result = extendBooking(bookingId, additionalHours)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isActionInFlight = false, infoMessage = CODE_EXTENSION_REQUESTED) }
                    loadBooking(bookingId)
                }
                is ApiResult.Error -> _state.update { it.copy(isActionInFlight = false, errorMessage = errorText(result.code, result.message)) }
                is ApiResult.Failure -> _state.update { it.copy(isActionInFlight = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun submitRate(rating: Int, text: String?) {
        val bookingId = _state.value.bookingDetail?.bookingId ?: return
        if (_state.value.isActionInFlight) return
        _state.update { it.copy(isActionInFlight = true, showRateSheet = false, errorMessage = null) }
        viewModelScope.launch {
            when (val result = rateBooking(bookingId, rating, text)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isActionInFlight = false, infoMessage = CODE_RATED) }
                    loadBooking(bookingId)
                }
                is ApiResult.Error -> _state.update { it.copy(isActionInFlight = false, errorMessage = errorText(result.code, result.message)) }
                is ApiResult.Failure -> _state.update { it.copy(isActionInFlight = false, errorMessage = result.error.message) }
            }
        }
    }

    /** "Book a Car" from the hub → reset the booking scratch state and go back to Search. */
    private fun resetForNewSearch() {
        _state.update {
            RentalUiState(
                step = RentalStep.Search,
                filters = it.filters,
                selectedCity = it.selectedCity,
                bookings = it.bookings,
            )
        }
    }

    private fun back() {
        _state.update { s ->
            when (s.step) {
                // Search is the root → leave the flow to the rider home.
                RentalStep.Search -> return@update s.copy(exitRequested = true)
                RentalStep.Results -> s.copy(step = RentalStep.Search)
                RentalStep.Detail -> s.copy(step = RentalStep.Results, vehicle = null)
                RentalStep.Reserve -> s.copy(step = RentalStep.Detail, quote = null)
                // Post-create / management screens fall back to the bookings hub.
                RentalStep.Confirmation -> s.copy(step = RentalStep.Bookings)
                RentalStep.Bookings -> s.copy(step = RentalStep.Search)
                RentalStep.BookingDetail -> s.copy(step = RentalStep.Bookings, bookingDetail = null)
            }
        }
        if (_state.value.step == RentalStep.Bookings) loadBookings()
    }

    /** ISO-8601 UTC (the wire format for all RAC timestamps). */
    private fun toIsoUtc(millis: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return fmt.format(java.util.Date(millis))
    }

    /** Map known HTTP/house codes to a friendly sentinel (resolved to a string in the UI); else the server message. */
    private fun errorText(code: Int, message: String?): String = when (code) {
        409 -> CODE_NOT_AVAILABLE
        402 -> CODE_PAYMENT_FAILED
        403 -> CODE_GUEST
        400 -> message?.takeIf { it.isNotBlank() } ?: CODE_INVALID_DATES
        else -> message?.takeIf { it.isNotBlank() } ?: CODE_GENERIC
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MIN_LEAD_MINUTES = 60
        const val MAX_LEAD_DAYS = 90
        const val NID_LENGTH = 10
        const val SA_PHONE_LENGTH = 9

        /**
         * Country calling codes used to split a stored `mobileNo` into dial-code + local number,
         * ordered longest-first so the prefix match picks the most specific code (e.g. `971` before `9`).
         * Covers the region and common expat nationalities; unknown numbers fall back to [DEFAULT_PHONE_CC].
         */
        private val DIAL_CODES: List<String> = listOf(
            // 3-digit
            "966", "971", "973", "974", "968", "965", "962", "970", "961", "963", "964", "967",
            "880", "960", "977", "212", "213", "216", "218", "249", "251", "234", "254", "233",
            "994", "995", "992", "993", "998",
            // 2-digit
            "20", "27", "30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44", "45",
            "46", "47", "48", "49", "60", "62", "63", "65", "66", "81", "82", "84", "86", "90",
            "91", "92", "93", "94", "95", "98",
            // 1-digit
            "1", "7",
        )
        // Sentinels mapped to localized strings in the UI (see rentalMessageText).
        const val CODE_NOT_AVAILABLE = "RAC_NOT_AVAILABLE"
        const val CODE_PAYMENT_FAILED = "RAC_PAYMENT_FAILED"
        const val CODE_GUEST = "RAC_GUEST"
        const val CODE_INVALID_DATES = "RAC_INVALID_DATES"
        const val CODE_GENERIC = "RAC_GENERIC"
        const val CODE_NAFATH_FAILED = "RAC_NAFATH_FAILED"
        const val CODE_CANCELLED = "RAC_CANCELLED"
        const val CODE_EXTENSION_REQUESTED = "RAC_EXTENSION_REQUESTED"
        const val CODE_RATED = "RAC_RATED"
    }
}
