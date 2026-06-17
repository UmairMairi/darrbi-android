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
    val riderName: String?,
    val riderRating: Double?,
    val riderImageUrl: String?,
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
    val driverName: String?,
    val driverRating: Double?,
    val driverImageUrl: String?,
    /** Driver's vehicle, e.g. "Toyota - Corolla" (when the backend provides it). */
    val driverCar: String? = null,
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
