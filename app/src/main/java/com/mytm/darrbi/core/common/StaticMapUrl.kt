package com.mytm.darrbi.core.common

import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.PlaceLocation
import java.util.Locale

/** Most route points to keep in a Static Maps `path` (URLs are length-bounded; routes are thinned to fit). */
private const val MAX_PATH_POINTS = 100

/**
 * Builds a Google Static Maps API URL picturing a trip: pickup (P) and drop-off (D) markers, the current
 * vehicle position (when known), and the route polyline (falling back to a straight pickup→drop line).
 * This URL is what we submit to the server on each trip step — it later surfaces as the trip's map image.
 *
 * Returns null when there's no API key or nothing to draw. The key must have the **Static Maps API**
 * enabled; production usage should additionally URL-sign requests.
 */
fun buildStaticMapUrl(
    apiKey: String,
    pickup: PlaceLocation?,
    dropoff: PlaceLocation?,
    routePoints: List<LatLngPoint> = emptyList(),
    carLocation: LatLngPoint? = null,
    sizePx: String = "640x360",
): String? {
    if (apiKey.isBlank()) return null
    if (pickup == null && dropoff == null && routePoints.isEmpty() && carLocation == null) return null

    fun point(lat: Double, lng: Double): String = "%.6f,%.6f".format(Locale.US, lat, lng)

    val sb = StringBuilder("https://maps.googleapis.com/maps/api/staticmap?size=$sizePx&scale=2&maptype=roadmap")
    pickup?.let { sb.append("&markers=color:0x2E7D32|label:P|").append(point(it.latitude, it.longitude)) }
    dropoff?.let { sb.append("&markers=color:0xC62828|label:D|").append(point(it.latitude, it.longitude)) }
    carLocation?.let { sb.append("&markers=color:0x1565C0|").append(point(it.latitude, it.longitude)) }

    val path = routePoints.ifEmpty {
        listOfNotNull(
            pickup?.let { LatLngPoint(it.latitude, it.longitude) },
            dropoff?.let { LatLngPoint(it.latitude, it.longitude) },
        )
    }
    if (path.size >= 2) {
        sb.append("&path=weight:4|color:0x1E88E5C8")
        downsample(path, MAX_PATH_POINTS).forEach { sb.append("|").append(point(it.latitude, it.longitude)) }
    }
    sb.append("&key=").append(apiKey)
    return sb.toString()
}

/** Evenly thins [points] to at most [max] entries, always keeping the first and last. */
private fun downsample(points: List<LatLngPoint>, max: Int): List<LatLngPoint> {
    if (points.size <= max) return points
    val step = (points.size - 1).toDouble() / (max - 1)
    return (0 until max).map { points[(it * step).toInt()] }
}
