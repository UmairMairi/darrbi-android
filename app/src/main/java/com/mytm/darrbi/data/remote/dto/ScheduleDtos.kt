package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Scheduled City-to-City (C2C) DTOs (see `V2_CITY_TO_CITY_MOBILE_INTEGRATION_GUIDE.md`). All server-optional
 * fields are nullable; the parser ignores unknown keys. The bid/select phase reuses the V2 bidding DTOs
 * (the `/v2/trips/:id/bids` endpoints); these cover only the new `/v2/schedule` surface.
 */

/** `GET /v2/schedule/cities` item (guide §5.1). */
@Serializable
data class C2cCityDto(
    val id: String? = null,
    val name: String? = null,
    val nameAr: String? = null,
    val centroidLat: Double? = null,
    val centroidLng: Double? = null,
    val countryId: String? = null,
)

/** `GET /v2/schedule/quote` → `data:{ recommendedFare, minFare, maxFare, routeDistanceKm, pricedBy }` (guide §5.2). */
@Serializable
data class C2cQuoteData(
    val originCityId: String? = null,
    val destinationCityId: String? = null,
    val seats: Int? = null,
    val routeDistanceKm: Double? = null,
    val recommendedFare: Double? = null,
    val minFare: Double? = null,
    val maxFare: Double? = null,
    val currency: String? = null,
    /** "route" = matched an intercity_routes row; "formula" = centroid-distance fallback. */
    val pricedBy: String? = null,
)

/** `POST /v2/schedule/city-to-city` body (guide §6.1). `addresses` = [pickup (type 1), dropoff (type 2)]. */
@Serializable
data class CreateScheduleRequest(
    val cabId: String,
    val originCityId: String,
    val destinationCityId: String,
    /** ISO-8601 UTC; must be within [now + min lead, now + max lead]. */
    val scheduledDepartureAt: String,
    val seats: Int,
    val riderOfferedFare: Double,
    /** 1 = card, 2 = wallet. */
    val paymentMethod: Int,
    val cardId: String? = null,
    val addresses: List<TripAddressBody>,
)

/** `POST /v2/schedule/city-to-city` → `MainEnvelope<CreateScheduleData>` (guide §6.1). */
@Serializable
data class CreateScheduleData(
    val id: String? = null,
    val tripStatus: Int? = null,
    val scheduledState: Int? = null,
    val originCityId: String? = null,
    val destinationCityId: String? = null,
    val seatsRequested: Int? = null,
    val scheduledDepartureAt: String? = null,
    val biddingClosesAt: String? = null,
    val riderOfferedFare: Double? = null,
    val recommendedFare: Double? = null,
    val minFare: Double? = null,
    val maxFare: Double? = null,
    val currency: String? = null,
)

/** `GET /v2/schedule/my-upcoming` item (guide §9). */
@Serializable
data class UpcomingScheduleDto(
    val tripId: String? = null,
    val tripNo: Long? = null,
    val status: Int? = null,
    val scheduledState: Int? = null,
    val originCityId: String? = null,
    val destinationCityId: String? = null,
    val seatsRequested: Int? = null,
    val seatsConfirmed: Int? = null,
    val scheduledDepartureAt: String? = null,
    val biddingClosesAt: String? = null,
    val riderOfferedFare: Double? = null,
    val driverId: String? = null,
    val currency: String? = null,
)

/** `GET /v2/schedule/open-in-route` → `data:{ trips:[…] }` — driver discovery of open C2C requests (guide §7.2). */
@Serializable
data class C2cOpenInRouteData(val trips: List<C2cOpenTripDto>? = null)

/** An open (AWAITING_BIDS) scheduled request the driver can bid on (guide §7.2). */
@Serializable
data class C2cOpenTripDto(
    val tripId: String? = null,
    val cabId: String? = null,
    val originCityId: String? = null,
    val destinationCityId: String? = null,
    val scheduledDepartureAt: String? = null,
    val seatsRequested: Int? = null,
    val riderOfferedFare: Double? = null,
    val recommendedFare: Double? = null,
    val pickup: C2cPointDto? = null,
    val dropoff: C2cPointDto? = null,
    val riderId: String? = null,
    val currency: String? = null,
)

@Serializable
data class C2cPointDto(val latitude: Double? = null, val longitude: Double? = null)

/** `PATCH /v2/schedule/:tripId/cancel` body (guide §11.1). */
@Serializable
data class CancelScheduleRequest(val reason: String? = null)

/** `PATCH /v2/schedule/:tripId/cancel` → `data:{ tripStatus, scheduledState, feeApplied }` (guide §11.1). */
@Serializable
data class CancelScheduleData(
    val tripId: String? = null,
    val tripStatus: Int? = null,
    val scheduledState: Int? = null,
    val feeApplied: Boolean? = null,
)
