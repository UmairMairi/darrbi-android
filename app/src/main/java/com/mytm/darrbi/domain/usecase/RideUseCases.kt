package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.BookedTrip
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.repository.RideRepository
import javax.inject.Inject

class GetCabTypesUseCase @Inject constructor(private val repository: RideRepository) {
    suspend operator fun invoke(pickup: PlaceLocation, destination: PlaceLocation): ApiResult<List<CabOption>> =
        repository.getCabTypes(pickup, destination)
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
