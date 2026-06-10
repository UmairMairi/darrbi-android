package com.mytm.darrbi.domain.model

/** A selectable cab type with its server-computed fare. */
data class CabOption(
    val id: String,
    val name: String,
    val nameArabic: String?,
    val seats: Int,
    val fare: Double,
    val imageUrl: String?,
    /** "X min away" label source; null means no captain currently available. */
    val etaMinutes: String?,
    val available: Boolean,
)

/** A validated promo code and the flat discount it applies to the fare. */
data class AppliedPromo(
    val code: String,
    val discount: Double,
    val promoCodeId: String?,
)

/** Result of creating a trip. */
data class BookedTrip(
    val tripId: String,
    val requestTimeLimit: String?,
)
