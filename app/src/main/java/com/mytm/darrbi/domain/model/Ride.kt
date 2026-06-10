package com.mytm.darrbi.domain.model

/** A past ride shown on the My Rides list / Ride Details (from `/trips/rider` or `/trips/driver`). */
data class Ride(
    val id: String,
    val timestampIso: String?,
    val amount: Double,
    /** Static map snapshot of the route, if the backend stored one. */
    val mapImageUrl: String?,
    /** Rider's review rating (0–5), if reviewed. */
    val rating: Double?,
    val pickupAddress: String?,
    val dropoffAddress: String?,
    /** The other party: the driver for a taken ride, the rider for a given ride. */
    val personName: String?,
    val personImageUrl: String?,
    /** Cab/category name, e.g. "Original Ride". */
    val carType: String?,
)
