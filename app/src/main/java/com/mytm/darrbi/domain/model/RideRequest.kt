package com.mytm.darrbi.domain.model

/**
 * An incoming ride request shown to the captain (`trip_request` socket push). Drives the accept/decline
 * overlay: rider + estimated earning + payment method, the pickup→destination route, and trip distance/time.
 * The driver→pickup distance/time is computed on-device from the captain's current location.
 */
data class RideRequest(
    val tripId: String,
    val riderName: String,
    val riderImageUrl: String?,
    val riderMobile: String?,
    val riderRating: Double?,
    /** The captain's estimated earning for this trip (SAR). */
    val estimateEarning: Double,
    /** 1 = card, 2 = wallet/cash (mirrors the create-trip payment method). */
    val paymentMethod: Int,
    val pickup: PlaceLocation,
    val destination: PlaceLocation,
    /** Pickup → destination distance (km) and time (minutes), from the payload. */
    val destDistanceKm: Double,
    val destTimeMinutes: Double,
)
