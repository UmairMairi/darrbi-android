package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.AppError
import com.mytm.darrbi.core.common.map
import com.mytm.darrbi.core.network.safeApiCall
import com.mytm.darrbi.core.network.unwrapMain
import com.mytm.darrbi.core.network.unwrapMainUnit
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.remote.dto.CancelOpenTripRequest
import com.mytm.darrbi.data.remote.dto.CourierBody
import com.mytm.darrbi.data.remote.dto.CreateBidTripRequest
import com.mytm.darrbi.data.remote.dto.PlaceBidRequest
import com.mytm.darrbi.data.remote.dto.RaiseOfferRequest
import com.mytm.darrbi.data.remote.dto.TripAddressBody
import com.mytm.darrbi.data.remote.service.V2RideApi
import com.mytm.darrbi.domain.model.Bid
import com.mytm.darrbi.domain.model.BidTrip
import com.mytm.darrbi.domain.model.BidType
import com.mytm.darrbi.domain.model.CourierDetails
import com.mytm.darrbi.domain.model.OpenTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.SelectedBid
import com.mytm.darrbi.domain.repository.V2RideRepository
import javax.inject.Inject

class V2RideRepositoryImpl @Inject constructor(
    private val api: V2RideApi,
) : V2RideRepository {

    override suspend fun createBidTrip(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        cabId: String,
        categoryId: String?,
        offeredFare: Double,
        courier: CourierDetails?,
    ): ApiResult<BidTrip> =
        safeApiCall {
            api.createTrip(
                CreateBidTripRequest(
                    cabId = cabId,
                    categoryId = categoryId?.takeIf { it.isNotBlank() },
                    paymentMethod = PAYMENT_METHOD_WALLET,
                    tripType = 1,
                    riderOfferedFare = offeredFare,
                    addresses = listOf(
                        // For a courier trip pickup = sender and destination = receiver (guide §6).
                        TripAddressBody(pickup.address.ifBlank { pickup.name }, ADDRESS_PICKUP, pickup.latitude, pickup.longitude),
                        TripAddressBody(destination.address.ifBlank { destination.name }, ADDRESS_DESTINATION, destination.latitude, destination.longitude),
                    ),
                    courier = courier?.toBody(),
                ),
            )
        }.unwrapMain().map { it.toDomain(offeredFare) }

    override suspend fun getOpenInRange(cabId: String, lat: Double, lng: Double): ApiResult<List<OpenTrip>> =
        safeApiCall { api.openInRange(cabId, lat.toString(), lng.toString()) }
            .unwrapMain().map { data -> data.trips.mapNotNull { it.toDomain() } }

    override suspend fun placeBid(
        tripId: String,
        bidType: BidType,
        bidFare: Double?,
        etaToPickupSec: Int?,
        message: String?,
        cabId: String?,
    ): ApiResult<Bid> {
        val result = safeApiCall {
            api.placeBid(
                tripId,
                PlaceBidRequest(
                    bidType = bidType.wire,
                    bidFare = bidFare?.takeIf { bidType == BidType.Counter },
                    etaToPickupSec = etaToPickupSec,
                    message = message?.takeIf { it.isNotBlank() },
                    cabId = cabId?.takeIf { it.isNotBlank() },
                ),
            )
        }.unwrapMain()
        return when (result) {
            is ApiResult.Success -> result.data.toDomain()?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(AppError.Serialization("Malformed bid response"))
            is ApiResult.Error -> result
            is ApiResult.Failure -> result
        }
    }

    override suspend fun withdrawBid(tripId: String, bidId: String): ApiResult<Unit> =
        safeApiCall { api.withdrawBid(tripId, bidId) }.unwrapMainUnit()

    override suspend fun getTripBids(tripId: String): ApiResult<List<Bid>> =
        safeApiCall { api.getBids(tripId) }.unwrapMain().map { data -> data.bids.mapNotNull { it.toDomain() } }

    override suspend fun selectBid(tripId: String, bidId: String): ApiResult<SelectedBid> =
        safeApiCall { api.acceptBid(tripId, bidId) }.unwrapMain().map { it.toDomain() }

    override suspend fun rejectBid(tripId: String, bidId: String): ApiResult<Unit> =
        safeApiCall { api.rejectBid(tripId, bidId) }.unwrapMainUnit()

    override suspend fun raiseOffer(tripId: String, newOfferedFare: Double): ApiResult<Unit> =
        safeApiCall { api.raiseOffer(tripId, RaiseOfferRequest(newOfferedFare)) }.unwrapMainUnit()

    override suspend fun cancelOpenTrip(tripId: String, reason: String?): ApiResult<Unit> =
        safeApiCall { api.cancelTrip(tripId, CancelOpenTripRequest(reason?.takeIf { it.isNotBlank() })) }.unwrapMainUnit()

    private companion object {
        const val ADDRESS_PICKUP = 1
        const val ADDRESS_DESTINATION = 2
        const val PAYMENT_METHOD_WALLET = 2
    }
}

/** Maps the rider's courier input to the create-trip `courier{}` wire block (guide §6.1). */
private fun CourierDetails.toBody(): CourierBody = CourierBody(
    senderPhone = senderPhone,
    senderName = senderName?.takeIf { it.isNotBlank() },
    receiverPhone = receiverPhone,
    receiverName = receiverName?.takeIf { it.isNotBlank() },
    parcelType = parcelType.wire,
    parcelWeightKg = parcelWeightKg,
    weightBucket = weightBucket?.wire,
    lengthCm = lengthCm,
    widthCm = widthCm,
    heightCm = heightCm,
    parcelNote = note?.takeIf { it.isNotBlank() },
)
