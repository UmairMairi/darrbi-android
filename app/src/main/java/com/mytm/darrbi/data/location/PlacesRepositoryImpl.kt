package com.mytm.darrbi.data.location

import android.content.Context
import android.util.Log
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.domain.repository.MapService
import com.mytm.darrbi.domain.repository.PlacesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Place search/resolution via the Google Places SDK; device location is delegated to [MapService]. */
@Singleton
class PlacesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapService: MapService,
) : PlacesRepository {

    private val placesClient by lazy { Places.createClient(context) }
    // A session token keeps autocomplete + details billed as one session.
    private var sessionToken = AutocompleteSessionToken.newInstance()

    override suspend fun autocomplete(query: String): ApiResult<List<PlaceSuggestion>> {
        if (query.isBlank()) return ApiResult.Success(emptyList())
        return runCatching {
            // Mirror ride-android's WhereToGo/Pickup autocomplete: restrict predictions to one country.
            val request = FindAutocompletePredictionsRequest.builder()
                .setCountries(AUTOCOMPLETE_COUNTRY)
                .setSessionToken(sessionToken)
                .setQuery(query)
                .build()
            Log.d(TAG, "autocomplete request: query=\"$query\" country=$AUTOCOMPLETE_COUNTRY")
            val response = placesClient.findAutocompletePredictions(request).await()
            val predictions = response.autocompletePredictions
            Log.d(TAG, "autocomplete response: ${predictions.size} prediction(s)")
            predictions.forEachIndexed { i, p ->
                Log.d(TAG, "  [$i] placeId=${p.placeId} | primary=\"${p.getPrimaryText(null)}\" | secondary=\"${p.getSecondaryText(null)}\" | full=\"${p.getFullText(null)}\"")
            }
            predictions.map {
                PlaceSuggestion(
                    id = it.placeId,
                    primaryText = it.getPrimaryText(null).toString(),
                    secondaryText = it.getSecondaryText(null).toString(),
                )
            }
        }.onFailure { Log.w(TAG, "autocomplete failed for \"$query\": ${it.message}") }.toApiResult()
    }

    override suspend fun placeDetails(placeId: String): ApiResult<PlaceLocation> = runCatching {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val response = placesClient.fetchPlace(FetchPlaceRequest.builder(placeId, fields).setSessionToken(sessionToken).build()).await()
        // New session after a details fetch completes a billing session.
        sessionToken = AutocompleteSessionToken.newInstance()
        val place = response.place
        PlaceLocation(
            name = place.name.orEmpty(),
            address = place.address.orEmpty(),
            latitude = place.latLng?.latitude ?: 0.0,
            longitude = place.latLng?.longitude ?: 0.0,
        )
    }.toApiResult()

    // Device location + geocoding + routing live in the map service (the single owner of map/location ops).
    override suspend fun currentLocation(): ApiResult<PlaceLocation> = mapService.currentLocation()

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<PlaceLocation> =
        mapService.reverseGeocode(latitude, longitude)

    override suspend fun getRoute(origin: PlaceLocation, destination: PlaceLocation): ApiResult<List<LatLngPoint>> =
        mapService.routeBetween(origin, destination)

    override fun locationUpdates(): kotlinx.coroutines.flow.Flow<LatLngPoint> = mapService.locationUpdates()

    private companion object {
        const val TAG = "PlacesAutocomplete"
        // Country to bias autocomplete to (matches ride-android's WhereToGo/Pickup test region).
        // Switch to "SA" for the Saudi production market.
        const val AUTOCOMPLETE_COUNTRY = "PK"
    }
}
