package com.mytm.darrbi.domain.model

/**
 * Car-rental (RAC) renter domain types — see `RAC_MOBILE_INTEGRATION_GUIDE.md`. The renter is the SAME
 * platform user as a rider (no separate account); the whole flow is REST on `/v2/rac/renter/...`. Lifecycle:
 * `search → quote → (nafath) → create booking (pay + hold deposit) → pending → active → completed → rate`.
 * CDW tiers, deposit and excess are Derrbi-controlled and read-only; the server is authoritative for all
 * pricing and availability (the client never trusts its own math).
 */

/** Search-screen filter LOVs (`GET /filters`). */
data class RentalFilters(
    val cities: List<String>,
    val categories: List<String>,
    val cdwTiers: List<String>,
    val transmissions: List<String>,
    val sortOptions: List<String>,
)

/** How search results are ordered (`sortBy` query param). */
enum class RentalSort(val wire: String) {
    PriceLow("price_low"),
    PriceHigh("price_high"),
    Rating("rating");

    companion object {
        fun from(value: String?): RentalSort? = entries.firstOrNull { it.wire == value }
    }
}

/** A page of search results: the visible [items] plus the server [total] (shown as "Total N"). */
data class RentalSearchResult(
    val items: List<RentalVehicleSummary>,
    val total: Int,
)

/** A vehicle card in the search results (`GET /vehicles`). */
data class RentalVehicleSummary(
    val id: String,
    val make: String,
    val model: String,
    val year: Int?,
    val category: String?,
    val photo: String?,
    val seats: Int?,
    val transmission: String?,
    val ratingAvg: Double?,
    val ratingCount: Int?,
    val companyName: String?,
    val city: String?,
    val perDay: Double,
    val currency: String,
    val featured: Boolean,
) {
    val displayName: String get() = listOf(make, model).filter { it.isNotBlank() }.joinToString(" ")
}

/** Full vehicle listing for the detail screen (`GET /vehicles/:id`). */
data class RentalVehicleDetail(
    val id: String,
    val make: String,
    val model: String,
    val year: Int?,
    val color: String?,
    val category: String?,
    val fuelType: String?,
    val transmission: String?,
    val engineCc: Int?,
    val specs: RentalSpecs,
    val features: List<String>,
    val photos: List<String>,
    val ratingAvg: Double?,
    val ratingCount: Int?,
    val featured: Boolean,
    val pricing: RentalPricing,
    val mileagePolicy: MileagePolicy,
    val company: RentalCompany?,
    val pickupBranches: List<PickupBranch>,
    val cdwTiers: List<CdwTierOption>,
    val addons: List<RentalAddon>,
    val reviews: List<RentalReview>,
    val currency: String,
) {
    val displayName: String get() = listOf(make, model).filter { it.isNotBlank() }.joinToString(" ")
}

data class RentalSpecs(
    val seats: Int?,
    val doors: Int?,
    val smallLuggage: Int?,
    val largeLuggage: Int?,
)

/** The 7 pricing periods; [perDay] anchors the search card. */
data class RentalPricing(
    val perHour: Double?,
    val h2to3: Double?,
    val h4to5: Double?,
    val h6to12: Double?,
    val perDay: Double?,
    val perWeek: Double?,
    val perMonth: Double?,
)

data class MileagePolicy(
    val policy: String?,
    val includedKmPerDay: Int?,
    val extraKmRate: Double?,
) {
    val isUnlimited: Boolean get() = policy?.equals("unlimited", ignoreCase = true) == true
}

data class RentalCompany(
    val id: String,
    val name: String,
    val logo: String?,
    val ratingAvg: Double?,
    val ratingCount: Int?,
)

data class PickupBranch(
    val id: String,
    val name: String,
    val city: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val parkingInstructions: String?,
)

/**
 * A Derrbi-controlled Collision-Damage-Waiver tier. [deposit] is pre-authorized on the renter's card at
 * booking and released on clean return; [excess] is the renter's max liability for that tier.
 */
data class CdwTierOption(
    val tier: String,
    val dailyRate: Double,
    val deposit: Double,
    val excess: Double,
)

data class RentalAddon(
    val id: String,
    val name: String,
    val dailyRate: Double?,
    val amount: Double?,
)

data class RentalReview(
    val id: String?,
    val rating: Int,
    val text: String?,
    val renterName: String?,
    val companyResponse: String?,
    val createdAtMillis: Long?,
)

/** Authoritative server-computed quote (`POST /quote`). [available] false → the window is taken. */
data class RentalQuote(
    val durationHours: Int,
    val days: Int,
    val baseAmount: Double,
    val cdwAmount: Double,
    val addonsAmount: Double,
    val discount: Double,
    val couponAmount: Double,
    val vatAmount: Double,
    val totalAmount: Double,
    val depositAmount: Double,
    val breakdown: List<QuoteLine>,
    val cdwTier: String,
    val excess: Double,
    val available: Boolean,
    val currency: String,
)

data class QuoteLine(val label: String, val amount: Double)

/** Nafath verification result. */
data class NafathResult(val verified: Boolean, val verifiedAtMillis: Long?)

/** RAC booking lifecycle status (guide §5). */
enum class RentalBookingStatus(val wire: String) {
    Pending("pending"),
    Active("active"),
    Completed("completed"),
    LateReturn("late_return"),
    CancelledByRenter("cancelled_by_renter"),
    CancelledByCompany("cancelled_by_company"),
    Disputed("disputed"),
    Unknown("");

    val isCancellable: Boolean get() = this == Pending
    val isActive: Boolean get() = this == Active || this == LateReturn
    val isRateable: Boolean get() = this == Completed

    companion object {
        fun from(value: String?): RentalBookingStatus =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: Unknown
    }
}

/** Vehicle summary embedded in a booking. */
data class BookingVehicle(
    val make: String,
    val model: String,
    val year: Int?,
    val category: String?,
    val photo: String?,
) {
    val displayName: String get() = listOf(make, model).filter { it.isNotBlank() }.joinToString(" ")
}

/** A booking row in the renter's list, and the create-booking result. */
data class RentalBooking(
    val bookingId: String,
    val bookingRef: String?,
    val status: RentalBookingStatus,
    val paymentStatus: String?,
    val vehicle: BookingVehicle?,
    val pickupAtMillis: Long?,
    val returnAtMillis: Long?,
    val durationHours: Int?,
    val cdwTier: String?,
    val totalAmount: Double,
    val depositAmount: Double,
    val refundAmount: Double,
    val currency: String,
)

/** Full booking detail (`GET /bookings/:id`). */
data class RentalBookingDetail(
    val bookingId: String,
    val bookingRef: String?,
    val status: RentalBookingStatus,
    val paymentStatus: String?,
    val vehicle: BookingVehicle?,
    val pickupAtMillis: Long?,
    val returnAtMillis: Long?,
    val actualPickupAtMillis: Long?,
    val actualReturnAtMillis: Long?,
    val durationHours: Int?,
    val cdwTier: String?,
    val baseAmount: Double,
    val cdwAmount: Double,
    val addonsAmount: Double,
    val discount: Double,
    val couponAmount: Double,
    val couponCode: String?,
    val vatAmount: Double,
    val totalAmount: Double,
    val depositAmount: Double,
    val refundAmount: Double,
    val companyName: String?,
    val pickupBranch: PickupBranch?,
    val renter: BookingRenter?,
    val penalties: BookingPenalties?,
    val tripData: TripTelemetry?,
    val extensionRequest: ExtensionResult?,
    val paymentRef: String?,
    val authorizationRef: String?,
    val currency: String,
)

data class BookingRenter(
    val name: String?,
    val phone: String?,
    val email: String?,
    val nationalId: String?,
    val licenseNo: String?,
    val nafathVerified: Boolean,
)

data class BookingPenalties(
    val lateReturn: Double,
    val lowFuel: Double,
    val outOfZone: Double,
    val collected: Boolean,
) {
    val total: Double get() = lateReturn + lowFuel + outOfZone
    val hasAny: Boolean get() = total > 0.0
}

/** Live telemetry during an active rental (incl. the Tajeer e-contract). */
data class TripTelemetry(
    val tajeerContractId: String?,
    val tajeerQrUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val fuelStartPct: Int?,
    val fuelNowPct: Int?,
    val distanceKm: Double?,
    val lastPingMillis: Long?,
)

/** Result of `POST /bookings/:id/extend` (and the `extensionRequest` block on detail). */
data class ExtensionResult(
    val hours: Int,
    val additionalCost: Double,
    val status: String,
)

/** Result of `POST /bookings/:id/cancel`. */
data class RentalCancelResult(val refundAmount: Double)
