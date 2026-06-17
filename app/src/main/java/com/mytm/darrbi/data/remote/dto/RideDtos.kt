package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ride-booking DTOs, mirroring ride-android exactly:
 * - `GET /master/cab-type/all` → [CabTypeData] (the displayed fare is each cab's `estimateCost`).
 * - `POST /promo-code/validate/` → [PromoData] (`amount` is a flat SAR discount).
 * - `POST /trips` → [CreateTripData] (success when `message == "Trip added successfully"`).
 */

@Serializable
data class CabTypeData(
    val cabs: List<CabDto> = emptyList(),
    val estimate: EstimateDto? = null,
)

/**
 * `GET captains/cab-type-category/all` → one rider home service category. Field names are unconfirmed
 * (the sample tokens were expired), so every field is nullable and the mapper picks the first non-null
 * icon field; unknown keys are ignored (`Json.ignoreUnknownKeys = true`).
 */
@Serializable
data class CabCategoryDto(
    val id: String? = null,
    val name: String? = null,
    val nameArabic: String? = null,
    val description: String? = null,
    val descriptionArabic: String? = null,
    val categoryIcon: String? = null,
    val categoryIconUrl: String? = null,
    val icon: String? = null,
    val image: String? = null,
    /** A stable key/type ("taxi", "rental", …) when the server provides one; used to spot the taxi tile. */
    val type: String? = null,
    val order: Int? = null,
    val status: Boolean? = null,
)

/** `GET trips/rider-recent-addresses` → `data` wraps the list under `recentAddresses`. */
@Serializable
data class RecentAddressesData(
    val recentAddresses: List<RecentAddressDto> = emptyList(),
)

/** One recent address row. The live API provides no label — just the address + coordinates. */
@Serializable
data class RecentAddressDto(
    val label: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val addressType: String? = null,
)

@Serializable
data class CabDto(
    val id: String? = null,
    val name: String? = null,
    val nameArabic: String? = null,
    val noOfSeats: Int? = null,
    /** The SAR fare shown in the row (server-computed; ride-android rounds to ≤2 decimals). */
    val estimateCost: Double? = null,
    /** Image URL Glide/Coil loads for the row. */
    val categoryIcon: String? = null,
    /** "X min away"; null in ride-android renders the "no captain" line. */
    val estimatedArrivalTime: String? = null,
    val shareEstimatedTimeArrival: Int? = null,
    val available: Boolean? = null,
)

@Serializable
data class EstimateDto(
    val distance: Double? = null,
    val time: Double? = null,
    val formattedDistance: String? = null,
    val formattedTime: String? = null,
)

/** `POST /promo-code/validate/` body. Wire key is literally `long` for the longitude. */
@Serializable
data class PromoValidateRequest(
    val promoCode: String,
    val amount: Double,
    val userId: String,
    val lat: Double,
    @SerialName("long") val lng: Double,
    val applyingTo: Int = 1,
    val cabId: String,
)

@Serializable
data class PromoData(
    val valid: Boolean? = null,
    /** Flat SAR discount the client subtracts from the fare. */
    val amount: Double? = null,
    val promoCodeId: String? = null,
    val code: String? = null,
)

/** `POST /trips` body. `promoCode`/`cardId` are omitted when null (explicitNulls = false). */
@Serializable
data class CreateTripRequest(
    val addresses: List<TripAddressBody>,
    val cabId: String,
    val promoCode: String? = null,
    /** 1 = card, 2 = wallet/cash. */
    val paymentMethod: Int,
    val cardId: String? = null,
)

/** `PATCH trips/driver-rejected/{tripId}` body — the captain declines a request (ride-android: DeclineRequestModel). */
@Serializable
data class DeclineTripRequest(
    val declinedReason: String,
    val dropAddress: DropAddress,
) {
    @Serializable
    data class DropAddress(
        val address: String,
        val latitude: Double,
        val longitude: Double,
    )
}

/** `POST reviews/rider` body — the rider's star rating for the captain (ride-android: ReviewRequestModel). */
@Serializable
data class ReviewRequest(
    val title: String,
    val description: String,
    val rating: Float,
    val tripId: String,
)

/** `PATCH trips/change-destination/{tripId}` body — the new drop-off (ride-android: ChangeDestinationRequestModel). */
@Serializable
data class ChangeDestinationRequest(
    val address: String,
    val cityNameInArabic: String,
    val latitude: Double,
    val longitude: Double,
    /** 1 = card, 2 = wallet/cash. */
    val paymentMethod: Int,
)

@Serializable
data class TripAddressBody(
    val address: String,
    /** 1 = pickup, 2 = destination. */
    val addressType: Int,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class CreateTripData(
    val id: String? = null,
    val message: String? = null,
    val tripRequestTimeLimit: String? = null,
)

/**
 * `GET trips/exists` → the active trip id (nested in the envelope's `data`), or null when no ride is in
 * progress. ride-android reads it recursively as `tripId`; some payloads carry it as `id` instead.
 */
@Serializable
data class TripExistsData(
    val tripId: String? = null,
    val id: String? = null,
)

/**
 * `GET trips/socket/{tripId}` → the live trip snapshot (same shape as the `trip-detail` socket push).
 * The numeric [status] is mapped to the rider's restored screen on dashboard entry (ride-android:
 * `openRideScreenAccordingToTripStatus`); cab/driver/source/destination populate the on-the-way screen.
 */
@Serializable
data class OngoingTripData(
    val id: String? = null,
    val status: Int? = null,
    val action: String? = null,
    val driverId: String? = null,
    /** The current rider fare (SAR) — baseline for a drop-change "pay remaining". */
    val riderAmount: Double? = null,
    /** Loyalty points earned (shown on the completed/rating screen). */
    val loyaltyPoints: Int? = null,
    /** ISO-8601 timestamp the captain reached pickup (e.g. "2026-06-15T11:00:05.000Z"). */
    val driverReachedAt: String? = null,
    val tripOtp: String? = null,
    /** The captain's earning (SAR) — used to restore the driver's navigate screen. */
    val driverAmount: Double? = null,
    /** 1 = card, 2 = wallet/cash (or `tripType`). */
    val paymentMethod: Int? = null,
    val tripType: Int? = null,
    /** Pickup → destination distance (km) and time (minutes). */
    val tripDistance: Double? = null,
    val estimatedTripTime: Double? = null,
    val source: TripPointDto? = null,
    val destination: TripPointDto? = null,
    @SerialName("destinationNew") val destinationNew: TripPointDto? = null,
    val cabInfo: TripCabInfoDto? = null,
    val driverInfo: TripDriverInfoDto? = null,
    val riderInfo: TripRiderInfoDto? = null,
)

@Serializable
data class TripRiderInfoDto(
    val id: String? = null,
    val name: String? = null,
    val profileImage: String? = null,
    val mobile: String? = null,
    val rating: Double? = null,
)

@Serializable
data class TripPointDto(
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class TripCabInfoDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val noOfSeats: Int? = null,
    val estimatedTimeArrival: Int? = null,
    val cancellationCharge: Double? = null,
)

@Serializable
data class TripDriverInfoDto(
    val id: String? = null,
    val name: String? = null,
    val carPlateNo: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val profileImage: String? = null,
    val rating: Double? = null,
    val mobile: String? = null,
)
