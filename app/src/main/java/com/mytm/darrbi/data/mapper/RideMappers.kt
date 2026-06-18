package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.CabCategoryDto
import com.mytm.darrbi.data.remote.dto.CabDto
import com.mytm.darrbi.data.remote.dto.OngoingTripData
import com.mytm.darrbi.data.remote.dto.RecentAddressDto
import com.mytm.darrbi.data.remote.dto.PromoData
import com.mytm.darrbi.data.remote.dto.TripDto
import com.mytm.darrbi.data.remote.dto.TripPersonDto
import com.mytm.darrbi.data.remote.dto.TripPointDto
import com.mytm.darrbi.domain.model.AcceptedTrip
import com.mytm.darrbi.domain.model.AppliedPromo
import com.mytm.darrbi.domain.model.CabOption
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.OngoingTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.RecentLocation
import com.mytm.darrbi.domain.model.Ride
import com.mytm.darrbi.domain.model.RideCategory
import com.mytm.darrbi.domain.model.RideRequest
import com.mytm.darrbi.domain.model.TripStage

private const val ADDRESS_PICKUP = 1

/** [given] = true for a "Rides Given" (driver) trip — use the driver amount and show the rider. */
fun TripDto.toRide(given: Boolean): Ride {
    val pickup = addresses.firstOrNull { it.addressType == ADDRESS_PICKUP } ?: addresses.firstOrNull()
    val dropoff = addresses.lastOrNull { it.addressType != ADDRESS_PICKUP } ?: addresses.lastOrNull()
    val person = if (given) rider else driver
    return Ride(
        id = id.orEmpty(),
        timestampIso = createdAt,
        amount = (if (given) driverAmount else riderAmount) ?: 0.0,
        mapImageUrl = images.firstOrNull()?.url,
        rating = riderReview?.rating,
        pickupAddress = pickup?.address,
        dropoffAddress = dropoff?.address,
        personName = person?.displayName(),
        personImageUrl = person?.profileImage,
        carType = cab?.name,
    )
}

private fun TripPersonDto.displayName(): String? =
    name?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(firstName, lastName).joinToString(" ").trim().takeIf { it.isNotBlank() }

/** The displayed fare is the server-computed `estimateCost`; other nullable fields are coerced. */
fun CabDto.toDomain(): CabOption = CabOption(
    id = id.orEmpty(),
    name = name.orEmpty(),
    nameArabic = nameArabic,
    seats = noOfSeats ?: 0,
    fare = estimateCost ?: 0.0,
    imageUrl = categoryIcon,
    etaMinutes = estimatedArrivalTime,
    available = available ?: true,
)

fun PromoData.toDomain(): AppliedPromo = AppliedPromo(
    code = code.orEmpty(),
    discount = amount ?: 0.0,
    promoCodeId = promoCodeId,
)

/** Picks the first non-null icon URL; [key] is the server `type` or a lowercased name (for the taxi badge). */
fun CabCategoryDto.toDomain(): RideCategory? {
    val safeName = name?.takeIf { it.isNotBlank() } ?: return null
    return RideCategory(
        id = id.orEmpty(),
        name = safeName,
        nameArabic = nameArabic?.takeIf { it.isNotBlank() },
        subtitle = (description ?: descriptionArabic)?.takeIf { it.isNotBlank() },
        imageUrl = listOfNotNull(categoryIconUrl, categoryIcon, image, icon).firstOrNull { it.isNotBlank() },
        order = order ?: Int.MAX_VALUE,
        key = (type?.takeIf { it.isNotBlank() } ?: safeName).lowercase(),
    )
}

/** Maps a recent-address row to a [RecentLocation]; null when it has no usable coordinates/address. */
fun RecentAddressDto.toDomain(): RecentLocation? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    val addr = address?.takeIf { it.isNotBlank() } ?: return null
    return RecentLocation(
        label = label?.takeIf { it.isNotBlank() },
        place = PlaceLocation(name = label.orEmpty(), address = addr, latitude = lat, longitude = lng),
    )
}

/**
 * Maps the live trip snapshot to the rider's restorable [OngoingTrip]. The numeric `status` mirrors
 * ride-android's `openRideScreenAccordingToTripStatus` (rider perspective):
 * 1/7 = trip started, 2 = driver accepted, 3 = searching, 5 = driver arrived,
 * 4/6/11 = cancelled, 8 = completed, 9 = expired.
 */
fun OngoingTripData.toOngoingTrip(): OngoingTrip = OngoingTrip(
    tripId = id.orEmpty(),
    stage = when (status) {
        15 -> TripStage.AwaitingBids
        3 -> TripStage.Searching
        2, 16 -> TripStage.DriverAssigned
        5 -> TripStage.DriverArrived
        1, 7 -> TripStage.InProgress
        8 -> TripStage.Completed
        4, 6, 11 -> TripStage.Cancelled
        9 -> TripStage.Expired
        else -> TripStage.Unknown
    },
    arrivedAtMillis = parseIsoMillis(driverReachedAt),
    pickup = source?.toPlaceLocation(),
    destination = (destinationNew ?: destination)?.takeIf { (it.latitude ?: 0.0) != 0.0 }?.toPlaceLocation()
        ?: destination?.toPlaceLocation(),
    acceptedTrip = if (driverInfo != null || cabInfo != null) {
        AcceptedTrip(
            tripId = id.orEmpty(),
            pin = tripOtp.orEmpty(),
            etaMinutes = cabInfo?.estimatedTimeArrival,
            cabName = cabInfo?.name.orEmpty(),
            cabDescription = cabInfo?.description.orEmpty(),
            seats = cabInfo?.noOfSeats ?: 0,
            plateNo = driverInfo?.carPlateNo.orEmpty(),
            driverId = driverInfo?.id?.takeIf { it.isNotBlank() } ?: driverId.orEmpty(),
            driverName = driverInfo?.name.orEmpty(),
            driverRating = driverInfo?.rating,
            driverImageUrl = driverInfo?.profileImage,
            driverMobile = driverInfo?.mobile,
            driverLatitude = driverInfo?.latitude,
            driverLongitude = driverInfo?.longitude,
            cancellationFee = cabInfo?.cancellationCharge ?: 0.0,
            cabId = cabInfo?.id.orEmpty(),
            originalFare = riderAmount ?: 0.0,
            loyaltyPoints = loyaltyPoints ?: 0,
        )
    } else {
        null
    },
    driverLocation = driverInfo?.let { d ->
        if (d.latitude != null && d.longitude != null) LatLngPoint(d.latitude, d.longitude) else null
    },
    rideRequest = run {
        val pickup = source?.toPlaceLocation()
        val dest = (destinationNew ?: destination)?.takeIf { (it.latitude ?: 0.0) != 0.0 }?.toPlaceLocation()
            ?: destination?.toPlaceLocation()
        if (pickup != null && dest != null) {
            RideRequest(
                tripId = id.orEmpty(),
                riderId = riderInfo?.id.orEmpty(),
                riderName = riderInfo?.name.orEmpty(),
                riderImageUrl = riderInfo?.profileImage?.takeIf { it.isNotBlank() },
                riderMobile = riderInfo?.mobile?.takeIf { it.isNotBlank() },
                riderRating = riderInfo?.rating,
                estimateEarning = driverAmount ?: riderAmount ?: 0.0,
                paymentMethod = paymentMethod ?: tripType ?: 2,
                pickup = pickup,
                destination = dest,
                destDistanceKm = tripDistance ?: 0.0,
                destTimeMinutes = estimatedTripTime ?: 0.0,
            )
        } else {
            null
        }
    },
    offeredFare = riderAmount,
)

private fun TripPointDto.toPlaceLocation(): PlaceLocation? {
    if (latitude == null || longitude == null) return null
    return PlaceLocation(name = "", address = address.orEmpty(), latitude = latitude, longitude = longitude)
}

/** Parses an ISO-8601 UTC timestamp ("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") to epoch millis, or null. */
private fun parseIsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        format.parse(iso)?.time
    }.getOrNull()
}
