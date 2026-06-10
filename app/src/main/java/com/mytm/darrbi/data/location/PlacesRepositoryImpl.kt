package com.mytm.darrbi.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.AppError
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.PlaceSuggestion
import com.mytm.darrbi.domain.repository.PlacesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlacesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlacesRepository {

    private val placesClient by lazy { Places.createClient(context) }
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    // A session token keeps autocomplete + details billed as one session.
    private var sessionToken = AutocompleteSessionToken.newInstance()

    override suspend fun autocomplete(query: String): ApiResult<List<PlaceSuggestion>> {
        if (query.isBlank()) return ApiResult.Success(emptyList())
        return runCatching {
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query)
                .build()
            val response = placesClient.findAutocompletePredictions(request).await()
            response.autocompletePredictions.map {
                PlaceSuggestion(
                    id = it.placeId,
                    primaryText = it.getPrimaryText(null).toString(),
                    secondaryText = it.getSecondaryText(null).toString(),
                )
            }
        }.toResult()
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
    }.toResult()

    override suspend fun currentLocation(): ApiResult<PlaceLocation> {
        if (!hasLocationPermission()) {
            return ApiResult.Failure(AppError.Unknown("Location permission not granted"))
        }
        return runCatching {
            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token,
            ).await() ?: error("Current location unavailable")
            PlaceLocation(
                name = "",
                address = geocodeAddress(location.latitude, location.longitude),
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }.toResult()
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<PlaceLocation> = runCatching {
        PlaceLocation(
            name = "",
            address = geocodeAddress(latitude, longitude),
            latitude = latitude,
            longitude = longitude,
        )
    }.toResult()

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun geocodeAddress(lat: Double, lng: Double): String = runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
            ?.firstOrNull()
            ?.getAddressLine(0)
            .orEmpty()
    }.getOrDefault("")
}

/** Suspend bridge for Play-services [Task] without the coroutines-play-services artifact. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

private fun <T> Result<T>.toResult(): ApiResult<T> = fold(
    onSuccess = { ApiResult.Success(it) },
    onFailure = { ApiResult.Failure(AppError.Unknown(it.message)) },
)
