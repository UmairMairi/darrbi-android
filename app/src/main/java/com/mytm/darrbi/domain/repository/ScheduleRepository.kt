package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.C2cCity
import com.mytm.darrbi.domain.model.C2cOpenScheduledTrip
import com.mytm.darrbi.domain.model.C2cQuote
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.ScheduleCancelResult
import com.mytm.darrbi.domain.model.ScheduledTrip
import com.mytm.darrbi.domain.model.UpcomingScheduledTrip

/**
 * Scheduled City-to-City actions (client→server, via REST — the `/v2/schedule` endpoints). The bid/select lifecycle
 * for the created trip reuses the V2 bidding engine ([V2RideRepository]); real-time bid delivery arrives on
 * [SocketService]. On failure, [ApiResult.Error.message] carries the C2C machine code (e.g.
 * `OFFER_OUT_OF_BAND`, `DEPARTURE_TOO_SOON`, `C2C_DISABLED`).
 */
interface ScheduleRepository {

    /** Selectable cities for the origin/destination pickers. */
    suspend fun getCities(): ApiResult<List<C2cCity>>

    /** Fare band for a (origin, destination, cab, seats) tuple before creating. */
    suspend fun getQuote(
        originCityId: String,
        destinationCityId: String,
        cabId: String,
        seats: Int,
    ): ApiResult<C2cQuote>

    /** Create an open scheduled C2C request → opens for bids immediately. */
    suspend fun createScheduledTrip(
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
    ): ApiResult<ScheduledTrip>

    /** Matched + open future C2C trips for the current user (rider: owned; driver: assigned). */
    suspend fun getMyUpcoming(): ApiResult<List<UpcomingScheduledTrip>>

    /** Driver: open C2C requests on a route the driver can bid on (omit a city id to widen). */
    suspend fun getOpenScheduledTrips(originCityId: String?, destinationCityId: String?): ApiResult<List<C2cOpenScheduledTrip>>

    /** Cancel a scheduled trip (window/fee aware). */
    suspend fun cancelScheduledTrip(tripId: String, reason: String?): ApiResult<ScheduleCancelResult>
}
