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

/**
 * Rider PII embedded in every driver-facing open-trip item (guide §5.1, rev 2026-06-18) so the captain
 * sees who is requesting before bidding. `rating`/`totalReviews` are 0 for a brand-new rider.
 */
@Serializable
data class RiderProfileDto(
    val riderId: String? = null,
    val name: String? = null,
    val arabicName: String? = null,
    val profileImage: String? = null,
    val rating: Double? = null,
    val totalReviews: Int? = null,
)

/** The driver's vehicle embedded in the `driver` bid block (`null` when no cab/plate on file yet). */
@Serializable
data class VehicleDto(
    val plateNo: String? = null,
    val sequenceNo: String? = null,
    val model: String? = null,
    val color: String? = null,
)

/**
 * Driver PII embedded in every rider-facing bid (guide §6.2) so the rider can choose a driver. Includes
 * the vehicle; `rating`/`totalReviews` are 0 for a new driver.
 */
@Serializable
data class DriverProfileDto(
    val driverId: String? = null,
    val name: String? = null,
    val arabicName: String? = null,
    val profileImage: String? = null,
    val mobile: String? = null,
    val rating: Double? = null,
    val totalReviews: Int? = null,
    val vehicle: VehicleDto? = null,
)

/** Parcel dimensions (cm) on a courier summary / match block; null when the rider didn't provide them. */
@Serializable
data class CourierDimensionsDto(
    val lengthCm: Int? = null,
    val widthCm: Int? = null,
    val heightCm: Int? = null,
)

/**
 * Driver-facing parcel SUMMARY on an open-trip item (guide §7) — type/weight/note/dimensions, never the
 * sender/receiver phones. Present only for courier trips.
 */
@Serializable
data class CourierSummaryDto(
    val parcelType: Int? = null,
    val parcelTypeLabel: String? = null,
    val parcelWeightKg: Double? = null,
    val weightBucket: Int? = null,
    val note: String? = null,
    val dimensions: CourierDimensionsDto? = null,
)

/** A courier party (sender/receiver) `{name, phone}` released to the winner at match (guide §8). */
@Serializable
data class CourierContactDto(
    val name: String? = null,
    val phone: String? = null,
)

/**
 * Courier MATCH block on `v2/bid-won` / `v2/bid-accepted` (guide §8.2/§8.3): parcel info plus the
 * sender/receiver contacts and the `deliveryOtp` needed to COMPLETE the trip at drop-off.
 */
@Serializable
data class CourierMatchDto(
    val parcelType: Int? = null,
    val parcelTypeLabel: String? = null,
    val parcelWeightKg: Double? = null,
    val note: String? = null,
    val deliveryOtp: Int? = null,
    val sender: CourierContactDto? = null,
    val receiver: CourierContactDto? = null,
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
    /** Rider PII block (guide §5.1). Preferred over the legacy flat fields below. */
    val rider: RiderProfileDto? = null,
    /** Parcel SUMMARY for a courier trip (guide §7); null for a normal ride. */
    val courier: CourierSummaryDto? = null,
    // Legacy flat fields — kept as a fallback for older payloads that don't nest the rider block.
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
    /** Driver PII block incl. vehicle (guide §6.2). Preferred over the legacy flat fields below. */
    val driver: DriverProfileDto? = null,
    // Legacy flat fields — kept as a fallback for older payloads that don't nest the driver block.
    val driverName: String? = null,
    val driverRating: Double? = null,
    val driverImage: String? = null,
    val driverCar: String? = null,
    val carName: String? = null,
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

/**
 * Courier `courier{}` block sent in the create-trip body (guide §4/§6.1). Required (with sender+receiver
 * phone + parcelType + parcelWeightKg) when the chosen cab is a courier cab; omitted otherwise.
 */
@Serializable
data class CourierBody(
    val senderPhone: String,
    val senderName: String? = null,
    val receiverPhone: String,
    val receiverName: String? = null,
    /** [ParcelType] wire value (1–9). */
    val parcelType: Int,
    /** Authoritative for capacity limits & fare. */
    val parcelWeightKg: Double,
    /** Optional UI weight bucket (1–5); limits/fare ignore it. */
    val weightBucket: Int? = null,
    val lengthCm: Int? = null,
    val widthCm: Int? = null,
    val heightCm: Int? = null,
    val parcelNote: String? = null,
)

/** `POST v2/trips` body — the V1 create body plus `riderOfferedFare` (+ `categoryId` from the home screen). */
@Serializable
data class CreateBidTripRequest(
    val cabId: String,
    val categoryId: String? = null,
    val paymentMethod: Int,
    val tripType: Int = 1,
    val riderOfferedFare: Double,
    val addresses: List<TripAddressBody>,
    /** Courier (parcel) metadata (guide §6.1); omitted (explicitNulls = false) for a normal ride. */
    val courier: CourierBody? = null,
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
