package com.mytm.darrbi.domain.model

/**
 * A trip a captain has accepted (`driver_accepted` socket push) — drives the rider's "on the way"
 * screen: PIN, ETA, cab + driver details, and the captain's location for the pickup→driver route.
 */
data class AcceptedTrip(
    val tripId: String,
    val pin: String,
    val etaMinutes: Int?,
    val cabName: String,
    val cabDescription: String,
    val seats: Int,
    val plateNo: String,
    /** Captain's user id — the chat `receiverId` and the key for fetching chat history. */
    val driverId: String,
    val driverName: String,
    val driverRating: Double?,
    val driverImageUrl: String?,
    val driverMobile: String?,
    val driverLatitude: Double?,
    val driverLongitude: Double?,
    val cancellationFee: Double,
    /** Cab type id — used to re-quote the fare when the rider changes the drop-off. */
    val cabId: String,
    /** The original rider fare (SAR) — the baseline for the "pay remaining" amount on a drop change. */
    val originalFare: Double,
    /** Loyalty points earned on this trip (shown on the completed/rating screen; 0 if not provided). */
    val loyaltyPoints: Int,
)
