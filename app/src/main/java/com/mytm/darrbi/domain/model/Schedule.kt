package com.mytm.darrbi.domain.model

/**
 * Scheduled City-to-City (C2C) domain types — see `V2_CITY_TO_CITY_MOBILE_INTEGRATION_GUIDE.md`. C2C is a
 * `(tripType=SCHEDULED, dispatchMode=BID)` trip: the rider schedules a future intercity ride, it opens for
 * bids immediately, the rider selects a bid (payment held), then near departure it activates into the
 * normal assigned-trip lifecycle. The bid/select phase and post-activation flow REUSE the V2 bidding
 * engine ([Bid]/[SelectedBid]) and the existing active-trip screens verbatim; only the scheduling wrapper
 * (cities, quote, scheduled sub-state, upcoming list, window-aware cancel) is new.
 */

/** A selectable City-to-City city (`GET /v2/schedule/cities`) with its centroid. */
data class C2cCity(
    val id: String,
    val name: String,
    val nameArabic: String?,
    val centroidLat: Double?,
    val centroidLng: Double?,
)

/**
 * A fare band for an (origin, destination, cab, seats) tuple (`GET /v2/schedule/quote`). The rider's
 * proposed fare must satisfy `minFare <= offer <= maxFare`. [pricedBy] is "route" (matched an
 * `intercity_routes` row) or "formula" (centroid-distance fallback).
 */
data class C2cQuote(
    val recommendedFare: Double,
    val minFare: Double,
    val maxFare: Double,
    val routeDistanceKm: Double?,
    val currency: String,
    val pricedBy: String?,
)

/**
 * Scheduled sub-state, orthogonal to [TripStatus] and set only on C2C rows (guide §2). The app branches
 * the scheduling phase on this; once [Activated] it switches to the V1 assigned-trip flow.
 */
enum class ScheduledTripState(val wire: Int) {
    Open(1),        // open for bids (TripStatus AWAITING_BIDS=15)
    Matched(2),     // a bid was selected, payment held (TripStatus DRIVER_SELECTED=16)
    Reminded(3),    // pre-departure reminder sent (still DRIVER_SELECTED=16)
    Activated(4),   // handed to the V1 lifecycle (TripStatus ACCEPTED_BY_DRIVER=2)
    Expired(5),     // window passed with no match, or activation guard failed
    Cancelled(6),   // rider cancelled before activation
    Unknown(-1);

    companion object {
        fun from(value: Int?): ScheduledTripState = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/**
 * The created (or in-flight) C2C trip — the result of `POST /v2/schedule/city-to-city` and the shape the
 * rider watches while the request is open / matched. [fareRange] carries the authoritative recommended/
 * min/max band (reused from the bidding engine).
 */
data class ScheduledTrip(
    val tripId: String,
    val tripStatus: Int,
    val scheduledState: ScheduledTripState,
    val originCityId: String,
    val destinationCityId: String,
    val seatsRequested: Int,
    val scheduledDepartureAtMillis: Long?,
    val biddingClosesAtMillis: Long?,
    val fareRange: FareRange,
    val currency: String,
)

/** A matched/open future C2C trip in the rider's `GET /v2/schedule/my-upcoming` list. */
data class UpcomingScheduledTrip(
    val tripId: String,
    val tripNo: Long?,
    val tripStatus: Int,
    val scheduledState: ScheduledTripState,
    val originCityId: String,
    val destinationCityId: String,
    val seatsRequested: Int,
    val seatsConfirmed: Int?,
    val scheduledDepartureAtMillis: Long?,
    val biddingClosesAtMillis: Long?,
    val riderOfferedFare: Double,
    val driverId: String?,
    val currency: String,
)

/**
 * An open C2C request a DRIVER can bid on (`GET /v2/schedule/open-in-route`, guide §7.2). The bid itself
 * reuses the V2 bidding engine (`/v2/trips/:id/bids`); accepting the offered fare or countering within the
 * quote band ([C2cQuote]). C2C bids carry a long TTL so a bid placed days early stays alive.
 */
data class C2cOpenScheduledTrip(
    val tripId: String,
    val cabId: String,
    val originCityId: String,
    val destinationCityId: String,
    val scheduledDepartureAtMillis: Long?,
    val seatsRequested: Int,
    val riderOfferedFare: Double,
    val recommendedFare: Double,
    val pickupLat: Double?,
    val pickupLng: Double?,
    val dropoffLat: Double?,
    val dropoffLng: Double?,
    val riderId: String?,
    val currency: String,
)

/** Result of `PATCH /v2/schedule/:tripId/cancel`. [feeApplied] = cancelled inside the no-free-cancel window. */
data class ScheduleCancelResult(
    val tripStatus: Int,
    val scheduledState: ScheduledTripState,
    val feeApplied: Boolean,
)
