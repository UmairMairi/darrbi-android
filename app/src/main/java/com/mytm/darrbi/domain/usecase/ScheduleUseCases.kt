package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.C2cCity
import com.mytm.darrbi.domain.model.C2cOpenScheduledTrip
import com.mytm.darrbi.domain.model.C2cQuote
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.ScheduleCancelResult
import com.mytm.darrbi.domain.model.ScheduledTrip
import com.mytm.darrbi.domain.model.UpcomingScheduledTrip
import com.mytm.darrbi.domain.repository.ScheduleRepository
import javax.inject.Inject

/** RIDER: selectable cities for the origin/destination pickers. */
class GetC2cCitiesUseCase @Inject constructor(private val repository: ScheduleRepository) {
    suspend operator fun invoke(): ApiResult<List<C2cCity>> = repository.getCities()
}

/** RIDER: fare band for a (origin, destination, cab, seats) tuple. */
class GetC2cQuoteUseCase @Inject constructor(private val repository: ScheduleRepository) {
    suspend operator fun invoke(
        originCityId: String,
        destinationCityId: String,
        cabId: String,
        seats: Int,
    ): ApiResult<C2cQuote> = repository.getQuote(originCityId, destinationCityId, cabId, seats)
}

/** RIDER: create an open scheduled C2C request. */
class CreateScheduledTripUseCase @Inject constructor(private val repository: ScheduleRepository) {
    suspend operator fun invoke(
        cabId: String,
        originCityId: String,
        destinationCityId: String,
        pickup: PlaceLocation,
        dropoff: PlaceLocation,
        scheduledDepartureAtIso: String,
        seats: Int,
        riderOfferedFare: Double,
        paymentMethod: Int,
        cardId: String? = null,
    ): ApiResult<ScheduledTrip> = repository.createScheduledTrip(
        cabId, originCityId, destinationCityId, pickup, dropoff,
        scheduledDepartureAtIso, seats, riderOfferedFare, paymentMethod, cardId,
    )
}

/** RIDER/DRIVER: matched + open future C2C trips. */
class GetMyUpcomingUseCase @Inject constructor(private val repository: ScheduleRepository) {
    suspend operator fun invoke(): ApiResult<List<UpcomingScheduledTrip>> = repository.getMyUpcoming()
}

/** DRIVER: open C2C requests on a route, available to bid on. */
class GetOpenScheduledTripsUseCase @Inject constructor(private val repository: ScheduleRepository) {
    suspend operator fun invoke(
        originCityId: String? = null,
        destinationCityId: String? = null,
    ): ApiResult<List<C2cOpenScheduledTrip>> = repository.getOpenScheduledTrips(originCityId, destinationCityId)
}

/** RIDER: cancel a scheduled trip (window/fee aware). */
class CancelScheduledTripUseCase @Inject constructor(private val repository: ScheduleRepository) {
    suspend operator fun invoke(tripId: String, reason: String? = null): ApiResult<ScheduleCancelResult> =
        repository.cancelScheduledTrip(tripId, reason)
}
