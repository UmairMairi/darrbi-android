package com.mytm.darrbi.domain.model

/**
 * The rider's stage in an in-progress ride, restored on dashboard entry. Mirrors ride-android's numeric
 * trip status (`openRideScreenAccordingToTripStatus`): searching for a captain, a captain assigned and on
 * the way / arrived, the trip in progress, or a terminal state (completed / cancelled / expired).
 */
enum class TripStage { AwaitingBids, Searching, DriverAssigned, DriverArrived, InProgress, Completed, Cancelled, Expired, Unknown }

/**
 * A ride already in progress when the rider returns to the dashboard (`GET trips/exists` →
 * `GET trips/socket/{id}`). [acceptedTrip] / [driverLocation] are present once a captain is assigned;
 * [arrivedAtMillis] is the epoch-ms the captain reached pickup (drives the "arrived" wait timer).
 */
data class OngoingTrip(
    val tripId: String,
    val stage: TripStage,
    val pickup: PlaceLocation?,
    val destination: PlaceLocation?,
    val acceptedTrip: AcceptedTrip?,
    val driverLocation: LatLngPoint?,
    val arrivedAtMillis: Long?,
    /** The driver-facing view of this trip (rider + earning + route) — used to restore the captain screen. */
    val rideRequest: RideRequest?,
    /** The rider's offered fare while awaiting bids (status 15) — used to restore the bidding screen. */
    val offeredFare: Double? = null,
)
