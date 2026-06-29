package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.MainEnvelope
import com.mytm.darrbi.data.remote.dto.RacBookingDetailDto
import com.mytm.darrbi.data.remote.dto.RacBookingDto
import com.mytm.darrbi.data.remote.dto.RacBookingListData
import com.mytm.darrbi.data.remote.dto.RacBookingRequest
import com.mytm.darrbi.data.remote.dto.RacCancelData
import com.mytm.darrbi.data.remote.dto.RacCancelRequest
import com.mytm.darrbi.data.remote.dto.RacExtendRequest
import com.mytm.darrbi.data.remote.dto.RacExtensionDto
import com.mytm.darrbi.data.remote.dto.RacFiltersDto
import com.mytm.darrbi.data.remote.dto.RacNafathData
import com.mytm.darrbi.data.remote.dto.RacNafathRequest
import com.mytm.darrbi.data.remote.dto.RacQuoteData
import com.mytm.darrbi.data.remote.dto.RacQuoteRequest
import com.mytm.darrbi.data.remote.dto.RacRateRequest
import com.mytm.darrbi.data.remote.dto.RacReviewDto
import com.mytm.darrbi.data.remote.dto.RacVehicleDetailDto
import com.mytm.darrbi.data.remote.dto.RacVehicleSearchData
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Car-rental (RAC) renter REST — api-gateway `/v2/rac/renter/...` on the Main host (see
 * `RAC_MOBILE_INTEGRATION_GUIDE.md`). REST only; no socket is involved. `filters`, `vehicles*`, `quote`
 * and `companies/:id/reviews` are public; the rest require the `sessionId` header (added by the auth
 * interceptor). All responses use the house [MainEnvelope] `{ statusCode, data, message }`.
 */
interface RacApi {

    /** Search-screen dropdown LOVs (cities, categories, CDW tiers, transmissions, sort) — public (§3.1). */
    @GET("v2/rac/renter/filters")
    suspend fun getFilters(): MainEnvelope<RacFiltersDto>

    /** Search available, approved vehicles for a window — public (§3.2). All params optional. */
    @GET("v2/rac/renter/vehicles")
    suspend fun searchVehicles(
        @Query("city") city: String? = null,
        @Query("category") category: String? = null,
        @Query("pickupAt") pickupAt: String? = null,
        @Query("returnAt") returnAt: String? = null,
        @Query("seats") seats: Int? = null,
        @Query("transmission") transmission: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): MainEnvelope<RacVehicleSearchData>

    /** Full vehicle listing: specs, pricing, CDW tiers, branches, add-ons, reviews — public (§3.3). */
    @GET("v2/rac/renter/vehicles/{id}")
    suspend fun getVehicle(@Path("id") vehicleId: String): MainEnvelope<RacVehicleDetailDto>

    /** Authoritative server-computed price quote (+ deposit, availability) — public (§3.4). */
    @POST("v2/rac/renter/quote")
    suspend fun getQuote(@Body body: RacQuoteRequest): MainEnvelope<RacQuoteData>

    /** Recent reviews for a rental company — public (§3.12). */
    @GET("v2/rac/renter/companies/{id}/reviews")
    suspend fun getCompanyReviews(
        @Path("id") companyId: String,
        @Query("limit") limit: Int? = null,
    ): MainEnvelope<List<RacReviewDto>>

    /** Nafath identity verification — auth (§3.5). */
    @POST("v2/rac/renter/nafath/verify")
    suspend fun verifyNafath(@Body body: RacNafathRequest): MainEnvelope<RacNafathData>

    /** Create a booking — re-prices, locks the vehicle, captures payment + holds the deposit — auth (§3.6). */
    @POST("v2/rac/renter/bookings")
    suspend fun createBooking(@Body body: RacBookingRequest): MainEnvelope<RacBookingDto>

    /** The renter's bookings, optionally filtered by `status` (upcoming|active|past or exact) — auth (§3.7). */
    @GET("v2/rac/renter/bookings")
    suspend fun getBookings(@Query("status") status: String? = null): MainEnvelope<RacBookingListData>

    /** Full booking detail incl. penalties, live tripData, Tajeer contract — auth, own booking only (§3.8). */
    @GET("v2/rac/renter/bookings/{id}")
    suspend fun getBooking(@Path("id") bookingId: String): MainEnvelope<RacBookingDetailDto>

    /** Cancel before pickup (refund per policy window) — auth (§3.9). */
    @POST("v2/rac/renter/bookings/{id}/cancel")
    suspend fun cancelBooking(
        @Path("id") bookingId: String,
        @Body body: RacCancelRequest,
    ): MainEnvelope<RacCancelData>

    /** Request a pro-rated extension; the partner approves/rejects — auth (§3.10). */
    @POST("v2/rac/renter/bookings/{id}/extend")
    suspend fun extendBooking(
        @Path("id") bookingId: String,
        @Body body: RacExtendRequest,
    ): MainEnvelope<RacExtensionDto>

    /** Rate a completed booking (once) — auth (§3.11). */
    @POST("v2/rac/renter/bookings/{id}/rate")
    suspend fun rateBooking(
        @Path("id") bookingId: String,
        @Body body: RacRateRequest,
    ): MainEnvelope<RacReviewDto>
}
