package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.PlaceSuggestion

/** Location search + resolution via the Google Places SDK and device location. */
interface PlacesRepository {
    /** Autocomplete predictions for [query] (empty query → empty list). */
    suspend fun autocomplete(query: String): ApiResult<List<PlaceSuggestion>>

    /** Resolve a prediction's [placeId] to coordinates + address. */
    suspend fun placeDetails(placeId: String): ApiResult<PlaceLocation>

    /** The device's current location, reverse-geocoded to an address (needs location permission). */
    suspend fun currentLocation(): ApiResult<PlaceLocation>

    /** Reverse-geocode an arbitrary point (e.g. a pin dropped on the map) to an address. */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<PlaceLocation>
}
