package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * V2 broadcast-dispatch + bidding DTOs (see `V2_MOBILE_INTEGRATION_GUIDE.md`). All server-optional fields
 * are nullable; the JSON parser ignores unknown keys, so tolerant fields (rider name/rating/address on the
 * open-trip card) are read when present and otherwise null.
 */

@Serializable
data class FareRangeDto(
    val currency: String? = null,
    val recommended: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val riderOfferedFare: Double? = null,
)

@Serializable
data class V2PointDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** One open (awaiting-bids) trip in the driver's broadcast list. */
@Serializable
data class OpenTripDto(
    val tripId: String? = null,
    val cabId: String? = null,
    val dispatchMode: String? = null,
    val pickup: V2PointDto? = null,
    val dropoff: V2PointDto? = null,
    val tripDistanceKm: Double? = null,
    val pickupDistanceKm: Double? = null,
    val etaToPickupSec: Int? = null,
    val fareRange: FareRangeDto? = null,
    val riderOfferedFare: Double? = null,
    val requestExpiresAt: String? = null,
    val createdAt: String? = null,
    // Tolerant optional — the reference card shows these but they aren't in the documented payload yet.
    val riderName: String? = null,
    val riderRating: Double? = null,
    val riderImage: String? = null,
    val pickupAddress: String? = null,
    val dropAddress: String? = null,
)

/** `GET v2/trips/open-in-range` and `v2/sync-open-trips` ack → `data:{ trips, now }`. */
@Serializable
data class OpenTripsData(
    val trips: List<OpenTripDto> = emptyList(),
    val now: String? = null,
)

@Serializable
data class BidDto(
    val bidId: String? = null,
    val tripId: String? = null,
    val driverId: String? = null,
    val bidType: Int? = null,
    val bidFare: Double? = null,
    val currency: String? = null,
    val status: Int? = null,
    val etaToPickupSec: Int? = null,
    val pickupDistanceKm: Double? = null,
    val message: String? = null,
    // Tolerant optional driver display info for the rider's bid list.
    val driverName: String? = null,
    val driverRating: Double? = null,
    val driverImage: String? = null,
)

/** `GET v2/trips/:id/bids` → `data:{ tripId, tripStatus, riderOfferedFare, currency, bids }`. */
@Serializable
data class TripBidsData(
    val tripId: String? = null,
    val tripStatus: Int? = null,
    val riderOfferedFare: Double? = null,
    val currency: String? = null,
    val bids: List<BidDto> = emptyList(),
)

/** `POST v2/trips` → `MainEnvelope<CreateBidTripData>` (statusCode/data). */
@Serializable
data class CreateBidTripData(
    val id: String? = null,
    val message: String? = null,
    val status: Int? = null,
    val riderOfferedFare: Double? = null,
    val recommendedFare: Double? = null,
    val minFare: Double? = null,
    val maxFare: Double? = null,
    val currency: String? = null,
    val tripRequestTimeLimit: String? = null,
)

/** `PATCH v2/trips/:id/bids/:bidId/accept` ack → `data:{ tripId, bidId, driverId, agreedFare, tripStatus }`. */
@Serializable
data class SelectBidData(
    val tripId: String? = null,
    val bidId: String? = null,
    val driverId: String? = null,
    val agreedFare: Double? = null,
    val currency: String? = null,
    val tripStatus: Int? = null,
)

// ---- request bodies ----

/** `POST v2/trips` body — the V1 create body plus `riderOfferedFare` (+ `categoryId` from the home screen). */
@Serializable
data class CreateBidTripRequest(
    val cabId: String,
    val categoryId: String? = null,
    val paymentMethod: Int,
    val tripType: Int = 1,
    val riderOfferedFare: Double,
    val addresses: List<TripAddressBody>,
)

/** `POST v2/trips/:id/bids` body. `bidFare` required for COUNTER (bidType=2); omitted for ACCEPT (1). */
@Serializable
data class PlaceBidRequest(
    val bidType: Int,
    val bidFare: Double? = null,
    val etaToPickupSec: Int? = null,
    val message: String? = null,
    val cabId: String? = null,
)

@Serializable
data class RaiseOfferRequest(val newOfferedFare: Double)

@Serializable
data class CancelOpenTripRequest(val declinedReason: String? = null)

/** Placeholder for V2 action responses whose body the app doesn't need (withdraw/reject/raise/cancel). */
typealias V2Ignored = JsonElement
