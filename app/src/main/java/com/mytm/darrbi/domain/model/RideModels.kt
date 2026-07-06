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
    /** The cab's service category (COURIER cabs carry parcel weight/size limits). */
    val categoryType: CategoryType? = null,
    /** Courier capacity limits (guide §3). NULL columns mean "no limit / not a courier cab". */
    val maxWeightKg: Double? = null,
    val maxLengthCm: Int? = null,
    val maxWidthCm: Int? = null,
    val maxHeightCm: Int? = null,
    val maxDimSumCm: Int? = null,
) {
    /**
     * Whether a captain is currently available for this cab — the same signal the UI shows as
     * "No Captain Available" ([etaMinutes] is null when no captain is online). A ride must NOT be created
     * for a cab with no available captain.
     */
    val hasCaptain: Boolean get() = etaMinutes != null

    /**
     * Whether this cab can carry a parcel of the given weight/dimensions (guide §3.2 UX gate). A NULL cab
     * limit is treated as "no limit". The server re-runs the same gate authoritatively at create-trip.
     */
    fun canCarry(
        weightKg: Double?,
        lengthCm: Int? = null,
        widthCm: Int? = null,
        heightCm: Int? = null,
    ): Boolean {
        if (weightKg != null && maxWeightKg != null && weightKg > maxWeightKg) return false
        if (lengthCm != null && maxLengthCm != null && lengthCm > maxLengthCm) return false
        if (widthCm != null && maxWidthCm != null && widthCm > maxWidthCm) return false
        if (heightCm != null && maxHeightCm != null && heightCm > maxHeightCm) return false
        if (maxDimSumCm != null && lengthCm != null && widthCm != null && heightCm != null &&
            (lengthCm + widthCm + heightCm) > maxDimSumCm
        ) {
            return false
        }
        return true
    }
}

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
    /** Service-category discriminator; a courier category collects parcel info before create-trip. */
    val categoryType: CategoryType = CategoryType.Unknown,
) {
    /** True for a COURIER (or CARGO) category — the parcel-delivery create-trip flow. */
    val isCourier: Boolean get() = categoryType.isCourier

    /** True for the SCHEDULE category — the scheduled City-to-City flow (separate `/v2/schedule` surface). */
    val isSchedule: Boolean get() = categoryType == CategoryType.Schedule

    /** True for the RENT-A-CAR category — the self-drive car-rental flow (separate `/v2/rac/renter` surface). */
    val isRental: Boolean get() = categoryType == CategoryType.RentACar || key.contains("rental")
}

/** A recent/saved rider address shown as a quick-pick on the home page. [label] is null when the API
 * provides no name for it (then only the address is shown). */
data class RecentLocation(
    val label: String?,
    val place: PlaceLocation,
)
