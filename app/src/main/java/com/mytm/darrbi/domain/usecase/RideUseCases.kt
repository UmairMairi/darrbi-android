package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
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

/** Home service categories shown in the rider dashboard grid. */
class GetRideCategoriesUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(): ApiResult<List<RideCategory>> = repository.getCategories()
}

/** Recent rider addresses for the home quick-picks (best-effort). */
class GetRecentAddressesUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(): ApiResult<List<RecentLocation>> = repository.getRecentAddresses()
}

class GetCabTypesUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        categoryId: String? = null,
    ): ApiResult<List<CabOption>> = repository.getCabTypes(pickup, destination, categoryId)
}

class ValidatePromoUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(code: String, fare: Double, cabId: String, pickup: PlaceLocation): ApiResult<AppliedPromo> =
        repository.validatePromo(code, fare, cabId, pickup)
}

class CreateTripUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(
        pickup: PlaceLocation,
        destination: PlaceLocation,
        cabId: String,
        promoCode: String?,
    ): ApiResult<BookedTrip> = repository.createTrip(pickup, destination, cabId, promoCode)
}

class CancelTripUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(tripId: String): ApiResult<Unit> = repository.cancelTripRequest(tripId)
}

/** Restores any in-progress ride when the rider opens the dashboard. Null = no active ride. */
class GetOngoingTripUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(): ApiResult<OngoingTrip?> = repository.getOngoingTrip()
}

/** Re-quotes the fare for a new drop-off (same cab) before the rider commits the change. */
class EstimateDropChangeUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(
        pickup: PlaceLocation,
        newDestination: PlaceLocation,
        cabId: String,
        categoryId: String? = null,
    ): ApiResult<DropChangeQuote> = repository.estimateDropChange(pickup, newDestination, cabId, categoryId)
}

/** Commits the new drop-off for an active trip. */
class ChangeDestinationUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(tripId: String, destination: PlaceLocation): ApiResult<Unit> =
        repository.changeDestination(tripId, destination)
}

/** Submits the rider's star rating for the captain after a completed trip. */
class RateDriverUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(tripId: String, stars: Int, riderName: String, driverName: String): ApiResult<Unit> =
        repository.rateDriver(tripId, stars, riderName, driverName)
}

/** CAPTAIN accepts an incoming ride request. */
class AcceptTripUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(tripId: String): ApiResult<Unit> = repository.acceptTrip(tripId)
}

/** CAPTAIN declines an incoming ride request. */
class RejectTripUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(tripId: String, destination: PlaceLocation): ApiResult<Unit> =
        repository.rejectTrip(tripId, destination)
}

/** CAPTAIN reached the pickup point. */
class ReachedPickupUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(tripId: String): ApiResult<Unit> = repository.reachedPickup(tripId)
}

/** CAPTAIN cancels an accepted trip. */
class CancelTripByDriverUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(tripId: String, destination: PlaceLocation): ApiResult<Unit> =
        repository.cancelTripByDriver(tripId, destination)
}
