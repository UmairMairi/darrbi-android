package com.mytm.darrbi.domain.repository

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

/**
 * Car-rental (RAC) renter actions, all REST on `/v2/rac/renter/...` (see `RAC_MOBILE_INTEGRATION_GUIDE.md`).
 * Search/detail/quote/reviews are public; identity, bookings and post-trip actions reuse the platform
 * `sessionId` auth. The server is authoritative for pricing + availability; [createBooking] re-prices and
 * re-checks under a row lock, then captures payment + holds the deposit atomically. On failure,
 * [ApiResult.Error.message] carries the human/house message (409 = window taken, 402 = payment failed,
 * 403 = guest/not-your-booking).
 */
interface RentalRepository {

    /** Search-screen filter LOVs. */
    suspend fun getFilters(): ApiResult<RentalFilters>

    /** Search available, approved vehicles for the given window/filters. */
    suspend fun searchVehicles(
        city: String?,
        category: String?,
        pickupAtIso: String?,
        returnAtIso: String?,
        seats: Int?,
        transmission: String?,
        sortBy: String?,
        page: Int?,
        limit: Int?,
    ): ApiResult<RentalSearchResult>

    /** Full vehicle listing for the detail screen. */
    suspend fun getVehicle(vehicleId: String): ApiResult<RentalVehicleDetail>

    /** Authoritative price quote for a window + CDW tier (+ coupon). Always quote before booking. */
    suspend fun getQuote(
        vehicleId: String,
        pickupAtIso: String,
        returnAtIso: String,
        cdwTier: String,
        addonIds: List<String>,
        couponCode: String?,
    ): ApiResult<RentalQuote>

    /** Recent reviews for a rental company. */
    suspend fun getCompanyReviews(companyId: String, limit: Int?): ApiResult<List<RentalReview>>

    /** Nafath identity verification (run before booking). */
    suspend fun verifyNafath(nationalId: String): ApiResult<NafathResult>

    /** Create a booking — payment + deposit hold happen here, atomically. */
    suspend fun createBooking(
        vehicleId: String,
        pickupAtIso: String,
        returnAtIso: String,
        cdwTier: String,
        addonIds: List<String>,
        couponCode: String?,
        nafathVerified: Boolean,
        renterNationalId: String?,
        renterLicenseNo: String?,
    ): ApiResult<RentalBooking>

    /** The renter's bookings, optionally filtered by status (`upcoming|active|past` or an exact status). */
    suspend fun getBookings(status: String?): ApiResult<List<RentalBooking>>

    /** Full booking detail (own booking only). */
    suspend fun getBooking(bookingId: String): ApiResult<RentalBookingDetail>

    /** Cancel before pickup; refund follows the policy window. */
    suspend fun cancelBooking(bookingId: String, reason: String?): ApiResult<RentalCancelResult>

    /** Request a pro-rated extension (partner approves/rejects). */
    suspend fun extendBooking(bookingId: String, additionalHours: Int): ApiResult<ExtensionResult>

    /** Rate a completed booking (once). */
    suspend fun rateBooking(bookingId: String, rating: Int, text: String?): ApiResult<Unit>
}
