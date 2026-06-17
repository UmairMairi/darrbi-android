package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.BookedTrip
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.DropChangeQuote
import com.mytm.darrbi.domain.model.OngoingTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.RecentLocation
import com.mytm.darrbi.domain.model.RideCategory

/** Rider ride-booking operations: list cabs/fares, validate a promo, create the trip. */
interface RideRepository {

    /** Home service categories (`GET captains/cab-type-category/all`), sorted by their display order. */
    suspend fun getCategories(): ApiResult<List<RideCategory>>

    /** Recent rider addresses for the home quick-picks; callers treat any error as an empty list. */
    suspend fun getRecentAddresses(): ApiResult<List<RecentLocation>>

    suspend fun getCabTypes(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        categoryId: String? = null,
    ): ApiResult<List<CabOption>>

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

    /**
     * Restores any in-progress ride when the rider returns to the dashboard
     * (`GET trips/exists` → `GET trips/socket/{id}`). [ApiResult.Success] with `null` = no active ride.
     */
    suspend fun getOngoingTrip(): ApiResult<OngoingTrip?>

    /** Re-quotes the fare for a new drop-off (same cab type) without committing it. */
    suspend fun estimateDropChange(
        pickup: PlaceLocation,
        newDestination: PlaceLocation,
        cabId: String,
        categoryId: String? = null,
    ): ApiResult<DropChangeQuote>

    /** Commits the new drop-off for [tripId] (`PATCH trips/change-destination/{tripId}`). */
    suspend fun changeDestination(tripId: String, destination: PlaceLocation): ApiResult<Unit>

    /** Submits the rider's star rating ([stars] = 1..5) for the captain of [tripId] (`POST reviews/rider`). */
    suspend fun rateDriver(tripId: String, stars: Int, riderName: String, driverName: String): ApiResult<Unit>

    /** CAPTAIN accepts an incoming ride request (`PATCH trips/driver-accepted/{tripId}`). */
    suspend fun acceptTrip(tripId: String): ApiResult<Unit>

    /** CAPTAIN declines an incoming ride request (`PATCH trips/driver-rejected/{tripId}`). */
    suspend fun rejectTrip(tripId: String, destination: PlaceLocation): ApiResult<Unit>

    /** CAPTAIN reached the pickup (`PATCH trips/driver-reached-at-pickup-point/{tripId}`). */
    suspend fun reachedPickup(tripId: String): ApiResult<Unit>

    /** CAPTAIN starts the trip after verifying the rider's [otp] (`PATCH trips/started/{tripId}`). */
    suspend fun startTrip(tripId: String, otp: Int): ApiResult<Unit>

    /** CAPTAIN cancels an accepted trip (`PATCH trips/driver-cancelled/{tripId}`). */
    suspend fun cancelTripByDriver(tripId: String, destination: PlaceLocation): ApiResult<Unit>
}
