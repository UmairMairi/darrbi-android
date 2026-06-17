package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.MainEnvelope
import com.mytm.darrbi.core.network.V2Envelope
import com.mytm.darrbi.data.remote.dto.BidDto
import com.mytm.darrbi.data.remote.dto.CancelOpenTripRequest
import com.mytm.darrbi.data.remote.dto.CreateBidTripData
import com.mytm.darrbi.data.remote.dto.CreateBidTripRequest
import com.mytm.darrbi.data.remote.dto.OpenTripsData
import com.mytm.darrbi.data.remote.dto.PlaceBidRequest
import com.mytm.darrbi.data.remote.dto.RaiseOfferRequest
import com.mytm.darrbi.data.remote.dto.SelectBidData
import com.mytm.darrbi.data.remote.dto.TripBidsData
import com.mytm.darrbi.data.remote.dto.V2Ignored
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * V2 broadcast-dispatch + bidding REST (api-gateway `/v2/trips`, Main host). Real-time delivery is on the
 * socket; these are the client→server actions (§7 of `V2_MOBILE_INTEGRATION_GUIDE.md`). `sessionid` header
 * is added automatically by the auth interceptor.
 */
interface V2RideApi {

    /** Rider creates a BID trip with a proposed fare → returns the fare range + `status:15`. */
    @POST("v2/trips")
    suspend fun createTrip(@Body body: CreateBidTripRequest): MainEnvelope<CreateBidTripData>

    /** Driver: the open (awaiting-bids) trips within range of [lat]/[lng] for [cabId]. */
    @GET("v2/trips/open-in-range")
    suspend fun openInRange(
        @Query("cabId") cabId: String,
        @Query("lat") lat: String,
        @Query("long") lng: String,
    ): V2Envelope<OpenTripsData>

    /** Driver places (or updates) a bid — ACCEPT the fare (bidType 1) or COUNTER (bidType 2 + bidFare). */
    @POST("v2/trips/{tripId}/bids")
    suspend fun placeBid(@Path("tripId") tripId: String, @Body body: PlaceBidRequest): V2Envelope<BidDto>

    /** Driver withdraws their active bid. */
    @DELETE("v2/trips/{tripId}/bids/{bidId}")
    suspend fun withdrawBid(@Path("tripId") tripId: String, @Path("bidId") bidId: String): V2Envelope<V2Ignored>

    /** Rider: the current competing bids for a trip (sorted cheapest-first). */
    @GET("v2/trips/{tripId}/bids")
    suspend fun getBids(@Path("tripId") tripId: String): V2Envelope<TripBidsData>

    /** Rider selects (accepts) a bid → commits the match; trip becomes ACCEPTED_BY_DRIVER (2). */
    @PATCH("v2/trips/{tripId}/bids/{bidId}/accept")
    suspend fun acceptBid(@Path("tripId") tripId: String, @Path("bidId") bidId: String): V2Envelope<SelectBidData>

    /** Rider rejects a single bid (keeps collecting others). */
    @PATCH("v2/trips/{tripId}/bids/{bidId}/reject")
    suspend fun rejectBid(@Path("tripId") tripId: String, @Path("bidId") bidId: String): V2Envelope<V2Ignored>

    /** Rider raises the offered fare to attract more/faster bids. */
    @PATCH("v2/trips/{tripId}/raise-offer")
    suspend fun raiseOffer(@Path("tripId") tripId: String, @Body body: RaiseOfferRequest): V2Envelope<V2Ignored>

    /** Rider cancels the open request before a match. */
    @PATCH("v2/trips/{tripId}/cancel")
    suspend fun cancelTrip(@Path("tripId") tripId: String, @Body body: CancelOpenTripRequest): V2Envelope<V2Ignored>
}
