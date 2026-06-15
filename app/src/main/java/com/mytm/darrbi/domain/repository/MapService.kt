package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation

/**
 * Single entry point for every device map/location operation: permission state, the device's current
 * location, and reverse-geocoding a point. Map UI reads location through this service (via use cases),
 * keeping the Play-services / Geocoder details out of the feature layer.
 */
interface MapService {
    /** Whether fine OR coarse location permission is currently granted. */
    fun hasLocationPermission(): Boolean

    /** The device's current location, reverse-geocoded to an address (requires location permission). */
    suspend fun currentLocation(): ApiResult<PlaceLocation>

    /** Reverse-geocode an arbitrary point (e.g. a dropped map pin) to an address. */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<PlaceLocation>

    /**
     * Driving route polyline from [origin] to [destination] via the Google Routes API
     * (same as ride-android). Returns the decoded path points (origin → destination).
     */
    suspend fun routeBetween(origin: PlaceLocation, destination: PlaceLocation): ApiResult<List<LatLngPoint>>
}
