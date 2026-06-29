package com.mytm.darrbi.domain.model

/** A driver's bid kind: take the rider's fare as-is, or counter with their own price. */
enum class BidType(val wire: Int) { AcceptFare(1), Counter(2) }

/** Bid lifecycle status (wire values per the V2 guide). */
enum class BidStatus(val wire: Int) {
    Pending(1), Accepted(2), Rejected(3), Withdrawn(4), Expired(5), Cancelled(6), LostRace(7), Unknown(-1);

    companion object {
        fun from(value: Int?): BidStatus = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/** The rider's offered fare and the server's allowed band around the recommended meter fare. */
data class FareRange(
    val currency: String,
    val recommended: Double,
    val min: Double,
    val max: Double,
    val riderOfferedFare: Double,
)

/** One open (awaiting-bids) trip shown in the driver's broadcast list. */
data class OpenTrip(
    val tripId: String,
    val cabId: String,
    val pickup: PlaceLocation,
    val dropoff: PlaceLocation,
    val tripDistanceKm: Double,
    val pickupDistanceKm: Double?,
    val etaToPickupSec: Int?,
    val fareRange: FareRange,
    val riderOfferedFare: Double,
    val expiresAtMillis: Long?,
    val createdAtMillis: Long?,
    /** Rider PII (guide §5.1) shown on the broadcast card so the captain knows who's requesting. */
    val riderId: String?,
    val riderName: String?,
    val riderArabicName: String?,
    val riderRating: Double?,
    val riderTotalReviews: Int?,
    val riderImageUrl: String?,
    /** Parcel SUMMARY for a courier trip (guide §7); null for a normal ride. No sender/receiver phones. */
    val courier: CourierSummary? = null,
)

/** A competing bid (rider's view) or a driver's own placed bid. */
data class Bid(
    val bidId: String,
    val driverId: String,
    val bidType: BidType,
    val fare: Double,
    val currency: String,
    val status: BidStatus,
    val etaToPickupSec: Int?,
    val pickupDistanceKm: Double?,
    val message: String?,
    /** Driver PII (guide §6.2) shown on the bid card so the rider can choose. */
    val driverName: String?,
    val driverArabicName: String? = null,
    val driverMobile: String? = null,
    val driverRating: Double?,
    val driverTotalReviews: Int? = null,
    val driverImageUrl: String?,
    /** Driver's vehicle, e.g. "Camry · Grey" (built from the bid's vehicle block when provided). */
    val driverCar: String? = null,
    val driverPlateNo: String? = null,
)

/** Result of creating a BID trip: the open trip id, status, and the authoritative fare range. */
data class BidTrip(
    val tripId: String,
    val status: Int,
    val fareRange: FareRange,
    val requestTimeLimit: String?,
)

/** Result of selecting/accepting a bid — the agreed driver + fare; trip is now ACCEPTED_BY_DRIVER (2). */
data class SelectedBid(
    val tripId: String,
    val bidId: String,
    val driverId: String,
    val agreedFare: Double,
    val currency: String,
    val tripStatus: Int,
)
