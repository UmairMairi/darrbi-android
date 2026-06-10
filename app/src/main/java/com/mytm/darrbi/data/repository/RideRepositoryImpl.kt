package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.core.common.map
import com.mytm.darrbi.core.network.safeApiCall
import com.mytm.darrbi.core.network.unwrapMain
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.remote.dto.CreateTripRequest
import com.mytm.darrbi.data.remote.dto.PromoValidateRequest
import com.mytm.darrbi.data.remote.dto.TripAddressBody
import com.mytm.darrbi.data.remote.service.RideApi
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.BookedTrip
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.repository.RideRepository
import javax.inject.Inject

class RideRepositoryImpl @Inject constructor(
    private val api: RideApi,
    private val userIdProvider: UserIdProvider,
) : RideRepository {

    override suspend fun getCabTypes(pickup: PlaceLocation, destination: PlaceLocation): ApiResult<List<CabOption>> =
        safeApiCall {
            api.getCabTypes(
                originLat = pickup.latitude.toString(),
                originLng = pickup.longitude.toString(),
                destinationLat = destination.latitude.toString(),
                destinationLng = destination.longitude.toString(),
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

    private companion object {
        const val ADDRESS_PICKUP = 1
        const val ADDRESS_DESTINATION = 2
        const val PAYMENT_METHOD_WALLET = 2
    }
}
