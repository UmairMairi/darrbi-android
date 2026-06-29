package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.BidDto
import com.mytm.darrbi.data.remote.dto.CourierContactDto
import com.mytm.darrbi.data.remote.dto.CourierMatchDto
import com.mytm.darrbi.data.remote.dto.CourierSummaryDto
import com.mytm.darrbi.data.remote.dto.CreateBidTripData
import com.mytm.darrbi.data.remote.dto.FareRangeDto
import com.mytm.darrbi.data.remote.dto.OpenTripDto
import com.mytm.darrbi.data.remote.dto.SelectBidData
import com.mytm.darrbi.data.remote.dto.VehicleDto
import com.mytm.darrbi.domain.model.Bid
import com.mytm.darrbi.domain.model.BidStatus
import com.mytm.darrbi.domain.model.BidTrip
import com.mytm.darrbi.domain.model.BidType
import com.mytm.darrbi.domain.model.CourierContact
import com.mytm.darrbi.domain.model.CourierMatch
import com.mytm.darrbi.domain.model.CourierSummary
import com.mytm.darrbi.domain.model.FareRange
import com.mytm.darrbi.domain.model.OpenTrip
import com.mytm.darrbi.domain.model.ParcelType
import com.mytm.darrbi.domain.model.ParcelWeightBucket
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.SelectedBid

private const val DEFAULT_CURRENCY = "SAR"
private const val STATUS_AWAITING_BIDS = 15
private const val STATUS_ACCEPTED = 2

fun FareRangeDto.toDomain(offeredFallback: Double? = null): FareRange = FareRange(
    currency = currency ?: DEFAULT_CURRENCY,
    recommended = recommended ?: 0.0,
    min = min ?: 0.0,
    max = max ?: 0.0,
    riderOfferedFare = riderOfferedFare ?: offeredFallback ?: 0.0,
)

/** null when the trip has no id or usable pickup/dropoff coordinates. */
fun OpenTripDto.toDomain(): OpenTrip? {
    val id = tripId?.takeIf { it.isNotBlank() } ?: return null
    val pLat = pickup?.latitude ?: return null
    val pLng = pickup.longitude ?: return null
    val dLat = dropoff?.latitude ?: return null
    val dLng = dropoff.longitude ?: return null
    return OpenTrip(
        tripId = id,
        cabId = cabId.orEmpty(),
        pickup = PlaceLocation(name = "", address = pickupAddress.orEmpty(), latitude = pLat, longitude = pLng),
        dropoff = PlaceLocation(name = "", address = dropAddress.orEmpty(), latitude = dLat, longitude = dLng),
        tripDistanceKm = tripDistanceKm ?: 0.0,
        pickupDistanceKm = pickupDistanceKm,
        etaToPickupSec = etaToPickupSec,
        fareRange = (fareRange ?: FareRangeDto()).toDomain(riderOfferedFare),
        riderOfferedFare = riderOfferedFare ?: fareRange?.riderOfferedFare ?: 0.0,
        expiresAtMillis = parseV2IsoMillis(requestExpiresAt),
        createdAtMillis = parseV2IsoMillis(createdAt),
        // Prefer the nested rider PII block (guide §5.1); fall back to the legacy flat fields.
        riderId = rider?.riderId?.takeIf { it.isNotBlank() },
        riderName = (rider?.name ?: riderName)?.takeIf { it.isNotBlank() },
        riderArabicName = rider?.arabicName?.takeIf { it.isNotBlank() },
        riderRating = (rider?.rating ?: riderRating)?.takeIf { it > 0.0 },
        riderTotalReviews = rider?.totalReviews,
        riderImageUrl = (rider?.profileImage ?: riderImage)?.takeIf { it.isNotBlank() },
        courier = courier?.toDomain(),
    )
}

/** Maps the driver-facing parcel SUMMARY (guide §7); never carries sender/receiver phones. */
fun CourierSummaryDto.toDomain(): CourierSummary = CourierSummary(
    parcelType = ParcelType.from(parcelType),
    parcelTypeLabel = parcelTypeLabel?.takeIf { it.isNotBlank() },
    parcelWeightKg = parcelWeightKg ?: 0.0,
    weightBucket = ParcelWeightBucket.from(weightBucket),
    note = note?.takeIf { it.isNotBlank() },
    lengthCm = dimensions?.lengthCm,
    widthCm = dimensions?.widthCm,
    heightCm = dimensions?.heightCm,
)

/** Maps the courier MATCH block on `v2/bid-won` / `v2/bid-accepted` (guide §8) incl. the delivery OTP. */
fun CourierMatchDto.toDomain(): CourierMatch = CourierMatch(
    parcelType = ParcelType.from(parcelType),
    parcelTypeLabel = parcelTypeLabel?.takeIf { it.isNotBlank() },
    parcelWeightKg = parcelWeightKg ?: 0.0,
    note = note?.takeIf { it.isNotBlank() },
    deliveryOtp = deliveryOtp,
    sender = sender?.toDomain(),
    receiver = receiver?.toDomain(),
)

/** null when the contact carries no phone (a courier party always has a phone when present). */
private fun CourierContactDto.toDomain(): CourierContact? {
    val number = phone?.takeIf { it.isNotBlank() } ?: return null
    return CourierContact(name = name?.takeIf { it.isNotBlank() }, phone = number)
}

/** null when the bid has no id. */
fun BidDto.toDomain(): Bid? {
    val id = bidId?.takeIf { it.isNotBlank() } ?: return null
    return Bid(
        bidId = id,
        driverId = driverId.orEmpty(),
        bidType = if (bidType == BidType.AcceptFare.wire) BidType.AcceptFare else BidType.Counter,
        fare = bidFare ?: 0.0,
        currency = currency ?: DEFAULT_CURRENCY,
        status = BidStatus.from(status),
        etaToPickupSec = etaToPickupSec,
        pickupDistanceKm = pickupDistanceKm,
        message = message?.takeIf { it.isNotBlank() },
        // Prefer the nested driver PII block (guide §6.2); fall back to the legacy flat fields.
        driverName = (driver?.name ?: driverName)?.takeIf { it.isNotBlank() },
        driverArabicName = driver?.arabicName?.takeIf { it.isNotBlank() },
        driverMobile = driver?.mobile?.takeIf { it.isNotBlank() },
        driverRating = (driver?.rating ?: driverRating)?.takeIf { it > 0.0 },
        driverTotalReviews = driver?.totalReviews,
        driverImageUrl = (driver?.profileImage ?: driverImage)?.takeIf { it.isNotBlank() },
        driverCar = driver?.vehicle?.displayName()
            ?: listOfNotNull(driverCar, carName).firstOrNull { it.isNotBlank() },
        driverPlateNo = driver?.vehicle?.plateNo?.takeIf { it.isNotBlank() },
    )
}

/** "Model · Color" (whichever parts are present), or null when the vehicle carries no displayable text. */
private fun VehicleDto.displayName(): String? =
    listOfNotNull(model?.takeIf { it.isNotBlank() }, color?.takeIf { it.isNotBlank() })
        .joinToString(" · ").takeIf { it.isNotBlank() }

fun CreateBidTripData.toDomain(offeredFallback: Double): BidTrip = BidTrip(
    tripId = id.orEmpty(),
    status = status ?: STATUS_AWAITING_BIDS,
    fareRange = FareRange(
        currency = currency ?: DEFAULT_CURRENCY,
        recommended = recommendedFare ?: 0.0,
        min = minFare ?: 0.0,
        max = maxFare ?: 0.0,
        riderOfferedFare = riderOfferedFare ?: offeredFallback,
    ),
    requestTimeLimit = tripRequestTimeLimit,
)

fun SelectBidData.toDomain(): SelectedBid = SelectedBid(
    tripId = tripId.orEmpty(),
    bidId = bidId.orEmpty(),
    driverId = driverId.orEmpty(),
    agreedFare = agreedFare ?: 0.0,
    currency = currency ?: DEFAULT_CURRENCY,
    tripStatus = tripStatus ?: STATUS_ACCEPTED,
)

/** Parses an ISO-8601 UTC timestamp ("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") to epoch millis, or null. */
internal fun parseV2IsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        format.parse(iso)?.time
    }.getOrNull()
}
