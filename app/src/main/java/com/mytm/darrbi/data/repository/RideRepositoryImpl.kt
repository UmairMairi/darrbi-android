package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.core.common.map
import com.mytm.darrbi.core.network.safeApiCall
import com.mytm.darrbi.core.network.unwrapMain
import com.mytm.darrbi.core.network.unwrapMainUnit
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.mapper.toOngoingTrip
import com.mytm.darrbi.data.remote.dto.ChangeDestinationRequest
import com.mytm.darrbi.data.remote.dto.CompleteTripRequest
import com.mytm.darrbi.data.remote.dto.CreateTripRequest
import com.mytm.darrbi.data.remote.dto.DeclineTripRequest
import com.mytm.darrbi.data.remote.dto.PromoValidateRequest
import com.mytm.darrbi.data.remote.dto.ReviewRequest
import com.mytm.darrbi.data.remote.dto.StartTripRequest
import com.mytm.darrbi.data.remote.dto.TripAddressBody
import com.mytm.darrbi.data.remote.service.RideApi
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.BookedTrip
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.DropChangeQuote
import com.mytm.darrbi.domain.model.OngoingTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.RecentLocation
import com.mytm.darrbi.domain.model.RideCategory
import com.mytm.darrbi.domain.repository.RideRepository
import javax.inject.Inject

class RideRepositoryImpl @Inject constructor(
    private val api: RideApi,
    private val userIdProvider: UserIdProvider,
) : RideRepository {

    override suspend fun getCategories(): ApiResult<List<RideCategory>> =
        safeApiCall { api.getCabCategories() }.unwrapMain().map { list ->
            list.mapNotNull { it.toDomain() }.sortedBy { it.order }
        }

    override suspend fun getRecentAddresses(): ApiResult<List<RecentLocation>> =
        safeApiCall { api.getRecentAddresses() }.unwrapMain().map { data -> data.recentAddresses.mapNotNull { it.toDomain() } }

    override suspend fun getCabTypes(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        categoryId: String?,
    ): ApiResult<List<CabOption>> =
        safeApiCall {
            api.getCabTypes(
                originLat = pickup.latitude.toString(),
                originLng = pickup.longitude.toString(),
                destinationLat = destination.latitude.toString(),
                destinationLng = destination.longitude.toString(),
                categoryId = categoryId?.takeIf { it.isNotBlank() },
            )
        }.unwrapMain().map { data -> data.cabs.map { it.toDomain() }.filter { it.id.isNotBlank() } }

    override suspend fun validatePromo(
        code: String,
        fare: Double,
        cabId: String,
        pickup: PlaceLocation,
    ): ApiResult<AppliedPromo> =
        safeApiCall {
            api.validatePromo(
                PromoValidateRequest(
                    promoCode = code,
                    amount = fare,
                    userId = userIdProvider.userId.orEmpty(),
                    lat = pickup.latitude,
                    lng = pickup.longitude,
                    applyingTo = 1,
                    cabId = cabId,
                ),
            )
        }.unwrapMain().map { it.toDomain() }

    override suspend fun createTrip(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        cabId: String,
        promoCode: String?,
    ): ApiResult<BookedTrip> =
        safeApiCall {
            api.createTrip(
                CreateTripRequest(
                    addresses = listOf(
                        TripAddressBody(
                            address = pickup.address,
                            addressType = ADDRESS_PICKUP,
                            latitude = pickup.latitude,
                            longitude = pickup.longitude,
                        ),
                        TripAddressBody(
                            address = destination.address,
                            addressType = ADDRESS_DESTINATION,
                            latitude = destination.latitude,
                            longitude = destination.longitude,
                        ),
                    ),
                    cabId = cabId,
                    promoCode = promoCode?.takeIf { it.isNotBlank() },
                    // 2 = wallet/cash (this flow has no card payment UI; mirrors ride-android's non-card path).
                    paymentMethod = PAYMENT_METHOD_WALLET,
                    cardId = null,
                ),
            )
        }.unwrapMain().map { BookedTrip(tripId = it.id.orEmpty(), requestTimeLimit = it.tripRequestTimeLimit) }

    override suspend fun cancelTripRequest(tripId: String): ApiResult<Unit> =
        safeApiCall { api.cancelTripRequest(tripId) }.unwrapMainUnit()

    override suspend fun getOngoingTrip(): ApiResult<OngoingTrip?> {
        // 1) Is there an active trip? A failed/empty `exists` check means "nothing to restore" (ride-android
        //    silently stays on home), so collapse it to a Success(null) rather than surfacing an error.
        val tripId = when (val exists = safeApiCall { api.checkTripExists() }.unwrapMain()) {
            is ApiResult.Success -> (exists.data.tripId ?: exists.data.id)?.takeIf { it.isNotBlank() }
            is ApiResult.Error, is ApiResult.Failure -> null
        } ?: return ApiResult.Success(null)
        // 2) Fetch the live snapshot and map its numeric status to the rider's restorable stage.
        return safeApiCall { api.getOngoingTripDetail(tripId) }.unwrapMain().map { it.toOngoingTrip() }
    }

    override suspend fun estimateDropChange(
        pickup: PlaceLocation,
        newDestination: PlaceLocation,
        cabId: String,
        categoryId: String?,
    ): ApiResult<DropChangeQuote> =
        safeApiCall {
            api.getCabTypes(
                originLat = pickup.latitude.toString(),
                originLng = pickup.longitude.toString(),
                destinationLat = newDestination.latitude.toString(),
                destinationLng = newDestination.longitude.toString(),
                categoryId = categoryId?.takeIf { it.isNotBlank() },
            )
        }.unwrapMain().map { data ->
            // Re-quote for the SAME cab type the rider is on (fall back to the first if not found).
            val cab = data.cabs.firstOrNull { it.id == cabId } ?: data.cabs.firstOrNull()
            DropChangeQuote(
                newFare = cab?.estimateCost ?: 0.0,
                arrivalMinutes = data.estimate?.time?.toInt() ?: cab?.shareEstimatedTimeArrival,
            )
        }

    override suspend fun changeDestination(tripId: String, destination: PlaceLocation): ApiResult<Unit> =
        safeApiCall {
            api.changeDestination(
                tripId,
                ChangeDestinationRequest(
                    address = destination.address.ifBlank { destination.name },
                    cityNameInArabic = CITY_NAME_AR,
                    latitude = destination.latitude,
                    longitude = destination.longitude,
                    paymentMethod = PAYMENT_METHOD_WALLET,
                ),
            )
        }.unwrapMainUnit()

    override suspend fun rateDriver(tripId: String, stars: Int, riderName: String, driverName: String): ApiResult<Unit> =
        safeApiCall {
            api.rateDriver(
                ReviewRequest(
                    title = REVIEW_TITLE,
                    description = "$riderName Review to $driverName",
                    rating = stars.toFloat(),
                    tripId = tripId,
                ),
            )
        }.unwrapMainUnit()

    override suspend fun acceptTrip(tripId: String): ApiResult<Unit> =
        safeApiCall { api.driverAcceptTrip(tripId) }.unwrapMainUnit()

    override suspend fun rejectTrip(tripId: String, destination: PlaceLocation): ApiResult<Unit> =
        safeApiCall { api.driverRejectTrip(tripId, declineBody(destination)) }.unwrapMainUnit()

    override suspend fun reachedPickup(tripId: String): ApiResult<Unit> =
        safeApiCall { api.driverReachedPickup(tripId) }.unwrapMainUnit()

    override suspend fun startTrip(tripId: String, otp: Int): ApiResult<Unit> =
        safeApiCall { api.startTrip(tripId, StartTripRequest(tripOtp = otp)) }.unwrapMainUnit()

    override suspend fun completeTrip(tripId: String, dropOff: PlaceLocation): ApiResult<Unit> =
        safeApiCall {
            api.completeTrip(
                tripId,
                CompleteTripRequest(
                    address = dropOff.address.ifBlank { dropOff.name },
                    latitude = dropOff.latitude,
                    longitude = dropOff.longitude,
                ),
            )
        }.unwrapMainUnit()

    override suspend fun cancelTripByDriver(tripId: String, destination: PlaceLocation): ApiResult<Unit> =
        safeApiCall { api.driverCancelTrip(tripId, declineBody(destination)) }.unwrapMainUnit()

    private fun declineBody(destination: PlaceLocation) = DeclineTripRequest(
        declinedReason = DECLINE_REASON_NONE,
        dropAddress = DeclineTripRequest.DropAddress(
            address = destination.address.ifBlank { destination.name },
            latitude = destination.latitude,
            longitude = destination.longitude,
        ),
    )

    private companion object {
        const val ADDRESS_PICKUP = 1
        const val ADDRESS_DESTINATION = 2
        const val PAYMENT_METHOD_WALLET = 2
        // ride-android sends the city name in Arabic with the change-destination request.
        const val CITY_NAME_AR = "الرياض"
        const val REVIEW_TITLE = "Rider Review"
        const val DECLINE_REASON_NONE = "none"
    }
}
