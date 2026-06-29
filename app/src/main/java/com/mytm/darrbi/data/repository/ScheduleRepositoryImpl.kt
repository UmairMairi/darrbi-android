package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.map
import com.mytm.darrbi.core.network.safeApiCall
import com.mytm.darrbi.core.network.unwrapMain
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.remote.dto.CancelScheduleRequest
import com.mytm.darrbi.data.remote.dto.CreateScheduleRequest
import com.mytm.darrbi.data.remote.dto.TripAddressBody
import com.mytm.darrbi.data.remote.service.ScheduleApi
import com.mytm.darrbi.domain.model.C2cCity
import com.mytm.darrbi.domain.model.C2cOpenScheduledTrip
import com.mytm.darrbi.domain.model.C2cQuote
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.ScheduleCancelResult
import com.mytm.darrbi.domain.model.ScheduledTrip
import com.mytm.darrbi.domain.model.UpcomingScheduledTrip
import com.mytm.darrbi.domain.repository.ScheduleRepository
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val api: ScheduleApi,
) : ScheduleRepository {

    override suspend fun getCities(): ApiResult<List<C2cCity>> =
        safeApiCall { api.getCities() }.unwrapMain().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun getQuote(
        originCityId: String,
        destinationCityId: String,
        cabId: String,
        seats: Int,
    ): ApiResult<C2cQuote> =
        safeApiCall { api.getQuote(originCityId, destinationCityId, cabId, seats) }
            .unwrapMain().map { it.toDomain() }

    override suspend fun createScheduledTrip(
        cabId: String,
        originCityId: String,
        destinationCityId: String,
        pickup: PlaceLocation,
        dropoff: PlaceLocation,
        scheduledDepartureAtIso: String,
        seats: Int,
        riderOfferedFare: Double,
        paymentMethod: Int,
        cardId: String?,
    ): ApiResult<ScheduledTrip> =
        safeApiCall {
            api.createCityToCity(
                CreateScheduleRequest(
                    cabId = cabId,
                    originCityId = originCityId,
                    destinationCityId = destinationCityId,
                    scheduledDepartureAt = scheduledDepartureAtIso,
                    seats = seats,
                    riderOfferedFare = riderOfferedFare,
                    paymentMethod = paymentMethod,
                    cardId = cardId?.takeIf { it.isNotBlank() },
                    addresses = listOf(
                        TripAddressBody(pickup.address.ifBlank { pickup.name }, ADDRESS_PICKUP, pickup.latitude, pickup.longitude),
                        TripAddressBody(dropoff.address.ifBlank { dropoff.name }, ADDRESS_DESTINATION, dropoff.latitude, dropoff.longitude),
                    ),
                ),
            )
        }.unwrapMain().map { it.toDomain(riderOfferedFare) }

    override suspend fun getMyUpcoming(): ApiResult<List<UpcomingScheduledTrip>> =
        safeApiCall { api.getMyUpcoming() }.unwrapMain().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun getOpenScheduledTrips(
        originCityId: String?,
        destinationCityId: String?,
    ): ApiResult<List<C2cOpenScheduledTrip>> =
        safeApiCall {
            api.getOpenInRoute(originCityId?.takeIf { it.isNotBlank() }, destinationCityId?.takeIf { it.isNotBlank() })
        }.unwrapMain().map { data -> data.trips.orEmpty().mapNotNull { it.toDomain() } }

    override suspend fun cancelScheduledTrip(tripId: String, reason: String?): ApiResult<ScheduleCancelResult> =
        safeApiCall { api.cancel(tripId, CancelScheduleRequest(reason?.takeIf { it.isNotBlank() })) }
            .unwrapMain().map { it.toDomain() }

    private companion object {
        const val ADDRESS_PICKUP = 1
        const val ADDRESS_DESTINATION = 2
    }
}
