package com.mytm.darrbi.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import android.os.Looper
import com.mytm.darrbi.BuildConfig
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.AppError
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.repository.MapService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Device location + geocoding via FusedLocationProvider and [Geocoder]. */
@Singleton
class MapServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MapService {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun currentLocation(): ApiResult<PlaceLocation> {
        if (!hasLocationPermission()) {
            return ApiResult.Failure(AppError.Unknown("Location permission not granted"))
        }
        return runCatching {
            // Prefer a fresh fix; fall back to the last known location if the device can't produce one.
            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token,
            ).await()
                ?: fusedClient.lastLocation.await()
                ?: error("Current location unavailable")
            PlaceLocation(
                name = "",
                address = geocodeAddress(location.latitude, location.longitude),
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }.toApiResult()
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<PlaceLocation> = runCatching {
        PlaceLocation(
            name = "",
            address = geocodeAddress(latitude, longitude),
            latitude = latitude,
            longitude = longitude,
        )
    }.toApiResult()

    @Suppress("DEPRECATION")
    private fun geocodeAddress(lat: Double, lng: Double): String = runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
            ?.firstOrNull()
            ?.getAddressLine(0)
            .orEmpty()
    }.getOrDefault("")

    override suspend fun routeBetween(
        origin: PlaceLocation,
        destination: PlaceLocation,
    ): ApiResult<List<LatLngPoint>> = withContext(Dispatchers.IO) {
        runCatching {
            // Routes API computeRoutes (the legacy Directions API can't be enabled on new projects).
            val languageCode = if (Locale.getDefault().language == "ar") "ar" else "en"
            val body = JSONObject().apply {
                put("origin", waypoint(origin.latitude, origin.longitude))
                put("destination", waypoint(destination.latitude, destination.longitude))
                put("travelMode", "DRIVE")
                put("routingPreference", "TRAFFIC_AWARE")
                put("polylineQuality", "HIGH_QUALITY")
                put("languageCode", languageCode)
                put("units", "METRIC")
            }.toString()

            val connection = (URL(ROUTES_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
                setRequestProperty("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use(BufferedReader::readText)
            connection.disconnect()

            val encoded = JSONObject(response)
                .optJSONArray("routes")
                ?.optJSONObject(0)
                ?.optJSONObject("polyline")
                ?.optString("encodedPolyline")
                .orEmpty()
            decodePolyline(encoded).ifEmpty { error("No route polyline") }
        }.toApiResult()
    }

    override fun locationUpdates(): Flow<LatLngPoint> {
        if (!hasLocationPermission()) return emptyFlow()
        return callbackFlow {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateDistanceMeters(LOCATION_DISPLACEMENT_M)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { trySend(LatLngPoint(it.latitude, it.longitude)) }
                }
            }
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            awaitClose { fusedClient.removeLocationUpdates(callback) }
        }
    }

    private fun waypoint(lat: Double, lng: Double): JSONObject =
        JSONObject().put("location", JSONObject().put("latLng", JSONObject().put("latitude", lat).put("longitude", lng)))

    /** Decodes a Google encoded polyline string into points (standard algorithm). */
    private fun decodePolyline(encoded: String): List<LatLngPoint> {
        val points = mutableListOf<LatLngPoint>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var result = 1
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63 - 1
                result += b shl shift
                shift += 5
            } while (b >= 0x1f)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 1
            shift = 0
            do {
                b = encoded[index++].code - 63 - 1
                result += b shl shift
                shift += 5
            } while (b >= 0x1f)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(LatLngPoint(lat / 1e5, lng / 1e5))
        }
        return points
    }

    private companion object {
        const val ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        // Live captain tracking cadence (matches ride-android: 1s interval, 20m displacement).
        const val LOCATION_INTERVAL_MS = 1000L
        const val LOCATION_DISPLACEMENT_M = 20f
    }
}
