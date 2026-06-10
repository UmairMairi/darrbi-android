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
