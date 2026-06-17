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

/** A re-quote for changing the drop-off mid-trip: the new fare and an arrival estimate (minutes). */
data class DropChangeQuote(
    val newFare: Double,
    val arrivalMinutes: Int?,
)

/**
 * A rider home service category (Darrbi Taxi, Car Rental, Cargo, …) from `captains/cab-type-category/all`.
 * [key] is a lowercased identifier ("taxi", "rental", …) used to spot the taxi tile for the nearby-captains
 * badge; [imageUrl] is loaded with Coil in the grid.
 */
data class RideCategory(
    val id: String,
    val name: String,
    val nameArabic: String?,
    val subtitle: String?,
    val imageUrl: String?,
    val order: Int,
    val key: String,
)

/** A recent/saved rider address shown as a quick-pick on the home page. [label] is null when the API
 * provides no name for it (then only the address is shown). */
data class RecentLocation(
    val label: String?,
    val place: PlaceLocation,
)
