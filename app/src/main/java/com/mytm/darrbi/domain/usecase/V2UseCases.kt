package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.Bid
import com.mytm.darrbi.domain.model.BidTrip
import com.mytm.darrbi.domain.model.BidType
import com.mytm.darrbi.domain.model.OpenTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.SelectedBid
import com.mytm.darrbi.domain.repository.V2RideRepository
import javax.inject.Inject

/** RIDER: create a BID trip with a proposed fare → fare range + open trip id. */
class CreateBidTripUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        cabId: String,
        categoryId: String?,
        offeredFare: Double,
    ): ApiResult<BidTrip> = repository.createBidTrip(pickup, destination, cabId, categoryId, offeredFare)
}

/** DRIVER: pull the open (awaiting-bids) trips in range. */
class GetOpenTripsUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(cabId: String, lat: Double, lng: Double): ApiResult<List<OpenTrip>> =
        repository.getOpenInRange(cabId, lat, lng)
}

/** DRIVER: place/update a bid (accept the fare or counter). */
class PlaceBidUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(
        tripId: String,
        bidType: BidType,
        bidFare: Double? = null,
        etaToPickupSec: Int? = null,
        message: String? = null,
        cabId: String? = null,
    ): ApiResult<Bid> = repository.placeBid(tripId, bidType, bidFare, etaToPickupSec, message, cabId)
}

/** DRIVER: withdraw an active bid. */
class WithdrawBidUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(tripId: String, bidId: String): ApiResult<Unit> =
        repository.withdrawBid(tripId, bidId)
}

/** RIDER: fetch the current competing bids. */
class GetTripBidsUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(tripId: String): ApiResult<List<Bid>> = repository.getTripBids(tripId)
}

/** RIDER: select (accept) a bid — commits the match. */
class SelectBidUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(tripId: String, bidId: String): ApiResult<SelectedBid> =
        repository.selectBid(tripId, bidId)
}

/** RIDER: reject a single bid. */
class RejectBidUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(tripId: String, bidId: String): ApiResult<Unit> =
        repository.rejectBid(tripId, bidId)
}

/** RIDER: raise the offered fare. */
class RaiseOfferUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(tripId: String, newOfferedFare: Double): ApiResult<Unit> =
        repository.raiseOffer(tripId, newOfferedFare)
}

/** RIDER: cancel the open request before a match. */
class CancelOpenTripUseCase @Inject constructor(private val repository: V2RideRepository) {
    suspend operator fun invoke(tripId: String, reason: String? = null): ApiResult<Unit> =
        repository.cancelOpenTrip(tripId, reason)
}
