package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.ExtensionResult
import com.mytm.darrbi.domain.model.NafathResult
import com.mytm.darrbi.domain.model.RentalBooking
import com.mytm.darrbi.domain.model.RentalBookingDetail
import com.mytm.darrbi.domain.model.RentalCancelResult
import com.mytm.darrbi.domain.model.RentalFilters
import com.mytm.darrbi.domain.model.RentalQuote
import com.mytm.darrbi.domain.model.RentalReview
import com.mytm.darrbi.domain.model.RentalSearchResult
import com.mytm.darrbi.domain.model.RentalVehicleDetail
import com.mytm.darrbi.domain.repository.RentalRepository
import javax.inject.Inject

/** RENTER: search-screen filter LOVs. */
class GetRentalFiltersUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(): ApiResult<RentalFilters> = repository.getFilters()
}

/** RENTER: search available vehicles for a window/filters. */
class SearchRentalVehiclesUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(
        city: String? = null,
        category: String? = null,
        pickupAtIso: String? = null,
        returnAtIso: String? = null,
        seats: Int? = null,
        transmission: String? = null,
        sortBy: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): ApiResult<RentalSearchResult> = repository.searchVehicles(
        city, category, pickupAtIso, returnAtIso, seats, transmission, sortBy, page, limit,
    )
}

/** RENTER: full vehicle detail. */
class GetRentalVehicleUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(vehicleId: String): ApiResult<RentalVehicleDetail> = repository.getVehicle(vehicleId)
}

/** RENTER: authoritative price quote (always quote before booking). */
class GetRentalQuoteUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(
        vehicleId: String,
        pickupAtIso: String,
        returnAtIso: String,
        cdwTier: String,
        addonIds: List<String> = emptyList(),
        couponCode: String? = null,
    ): ApiResult<RentalQuote> = repository.getQuote(vehicleId, pickupAtIso, returnAtIso, cdwTier, addonIds, couponCode)
}

/** RENTER: recent company reviews. */
class GetRentalCompanyReviewsUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(companyId: String, limit: Int? = null): ApiResult<List<RentalReview>> =
        repository.getCompanyReviews(companyId, limit)
}

/** RENTER: Nafath identity verification (before booking). */
class VerifyNafathUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(nationalId: String): ApiResult<NafathResult> = repository.verifyNafath(nationalId)
}

/** RENTER: create a booking — payment + deposit hold happen here. */
class CreateRentalBookingUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(
        vehicleId: String,
        pickupAtIso: String,
        returnAtIso: String,
        cdwTier: String,
        addonIds: List<String> = emptyList(),
        couponCode: String? = null,
        nafathVerified: Boolean,
        renterNationalId: String? = null,
        renterLicenseNo: String? = null,
    ): ApiResult<RentalBooking> = repository.createBooking(
        vehicleId, pickupAtIso, returnAtIso, cdwTier, addonIds, couponCode,
        nafathVerified, renterNationalId, renterLicenseNo,
    )
}

/** RENTER: the renter's bookings (optional status filter). */
class GetRentalBookingsUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(status: String? = null): ApiResult<List<RentalBooking>> = repository.getBookings(status)
}

/** RENTER: full booking detail (own booking). */
class GetRentalBookingUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(bookingId: String): ApiResult<RentalBookingDetail> = repository.getBooking(bookingId)
}

/** RENTER: cancel before pickup (refund per policy). */
class CancelRentalBookingUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(bookingId: String, reason: String? = null): ApiResult<RentalCancelResult> =
        repository.cancelBooking(bookingId, reason)
}

/** RENTER: request a pro-rated extension. */
class ExtendRentalBookingUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(bookingId: String, additionalHours: Int): ApiResult<ExtensionResult> =
        repository.extendBooking(bookingId, additionalHours)
}

/** RENTER: rate a completed booking. */
class RateRentalBookingUseCase @Inject constructor(private val repository: RentalRepository) {
    suspend operator fun invoke(bookingId: String, rating: Int, text: String? = null): ApiResult<Unit> =
        repository.rateBooking(bookingId, rating, text)
}
