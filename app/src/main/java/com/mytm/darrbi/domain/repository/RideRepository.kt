package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.BookedTrip
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.PlaceLocation

/** Rider ride-booking operations: list cabs/fares, validate a promo, create the trip. */
interface RideRepository {

    suspend fun getCabTypes(pickup: PlaceLocation, destination: PlaceLocation): ApiResult<List<CabOption>>

    suspend fun validatePromo(
        code: String,
        fare: Double,
        cabId: String,
        pickup: PlaceLocation,
    ): ApiResult<AppliedPromo>

    suspend fun createTrip(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        cabId: String,
        promoCode: String?,
    ): ApiResult<BookedTrip>

    /** Cancels a pending trip request (`PUT trips/cancel-trip-request/{tripId}`). */
    suspend fun cancelTripRequest(tripId: String): ApiResult<Unit>
}
