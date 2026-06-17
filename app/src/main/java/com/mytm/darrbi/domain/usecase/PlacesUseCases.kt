package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.domain.repository.PlacesRepository
import javax.inject.Inject

class AutocompletePlacesUseCase @Inject constructor(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke(query: String): ApiResult<List<PlaceSuggestion>> = repository.autocomplete(query)
}

class PlaceDetailsUseCase @Inject constructor(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke(placeId: String): ApiResult<PlaceLocation> = repository.placeDetails(placeId)
}

class CurrentLocationUseCase @Inject constructor(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke(): ApiResult<PlaceLocation> = repository.currentLocation()
}

class ReverseGeocodeUseCase @Inject constructor(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke(latitude: Double, longitude: Double): ApiResult<PlaceLocation> =
        repository.reverseGeocode(latitude, longitude)
}

class GetRouteUseCase @Inject constructor(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke(origin: PlaceLocation, destination: PlaceLocation): ApiResult<List<LatLngPoint>> =
        repository.getRoute(origin, destination)
}

/** Continuous device-location stream for live captain tracking. */
class StreamLocationUpdatesUseCase @Inject constructor(
    private val repository: PlacesRepository,
) {
    operator fun invoke(): kotlinx.coroutines.flow.Flow<LatLngPoint> = repository.locationUpdates()
}
