package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.Bid
import com.mytm.darrbi.domain.model.BidTrip
import com.mytm.darrbi.domain.model.BidType
import com.mytm.darrbi.domain.model.CourierDetails
import com.mytm.darrbi.domain.model.OpenTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.SelectedBid

/**
 * V2 broadcast-dispatch + bidding actions (client→server, via REST). Real-time delivery (open-trips list,
 * live bids, won/lost) arrives on [SocketService]. On failure, [ApiResult.Error.message] carries the V2
 * error code (e.g. `BID_BELOW_FLOOR`, `DRIVER_INELIGIBLE`, `DRIVER_RACE_LOST`, `PAYMENT_HOLD_FAILED`).
 */
interface V2RideRepository {

    /**
     * RIDER: create a BID trip with [offeredFare]; returns the open trip id + authoritative fare range.
     * [courier] is required for a courier (parcel) cab and omitted for a normal ride.
     */
    suspend fun createBidTrip(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        cabId: String,
        categoryId: String?,
        offeredFare: Double,
        courier: CourierDetails? = null,
    ): ApiResult<BidTrip>

    /** DRIVER: open (awaiting-bids) trips in range for [cabId] around [lat]/[lng]. */
    suspend fun getOpenInRange(cabId: String, lat: Double, lng: Double): ApiResult<List<OpenTrip>>

    /** DRIVER: place/update a bid — ACCEPT the fare or COUNTER with [bidFare]. */
    suspend fun placeBid(
        tripId: String,
        bidType: BidType,
        bidFare: Double?,
        etaToPickupSec: Int?,
        message: String?,
        cabId: String?,
    ): ApiResult<Bid>

    /** DRIVER: withdraw an active bid. */
    suspend fun withdrawBid(tripId: String, bidId: String): ApiResult<Unit>

    /** RIDER: current competing bids for a trip. */
    suspend fun getTripBids(tripId: String): ApiResult<List<Bid>>

    /** RIDER: select (accept) a bid — commits the match. */
    suspend fun selectBid(tripId: String, bidId: String): ApiResult<SelectedBid>

    /** RIDER: reject a single bid. */
    suspend fun rejectBid(tripId: String, bidId: String): ApiResult<Unit>

    /** RIDER: raise the offered fare. */
    suspend fun raiseOffer(tripId: String, newOfferedFare: Double): ApiResult<Unit>

    /** RIDER: cancel the open request before a match. */
    suspend fun cancelOpenTrip(tripId: String, reason: String?): ApiResult<Unit>
}
