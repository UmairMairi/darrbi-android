package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Car-rental (RAC) renter DTOs — see `RAC_MOBILE_INTEGRATION_GUIDE.md`. The renter surface is REST-only on
 * the Main gateway (`/v2/rac/renter/...`), reusing the same [com.mytm.darrbi.core.network.MainEnvelope]
 * `{ statusCode, data, message }` contract and the platform `sessionId` auth as V2. Every server-optional
 * field is nullable; the parser ignores unknown keys. Money is the company-country currency (defaults to SAR).
 */

/** `GET /v2/rac/renter/filters` → search-screen dropdown LOVs (guide §3.1). */
@Serializable
data class RacFiltersDto(
    val cities: List<String>? = null,
    val categories: List<String>? = null,
    val cdwTiers: List<String>? = null,
    val transmissions: List<String>? = null,
    val sortOptions: List<String>? = null,
)

/** `GET /v2/rac/renter/vehicles` → `data:{ items, total }` (guide §3.2). */
@Serializable
data class RacVehicleSearchData(
    val items: List<RacVehicleSummaryDto>? = null,
    val total: Int? = null,
)

/** A vehicle card in the search results (guide §3.2). */
@Serializable
data class RacVehicleSummaryDto(
    val vehicleId: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val category: String? = null,
    val photo: String? = null,
    val seats: Int? = null,
    val transmission: String? = null,
    val ratingAvg: Double? = null,
    val ratingCount: Int? = null,
    val companyName: String? = null,
    val city: String? = null,
    val perDay: Double? = null,
    val currency: String? = null,
    val featured: Boolean? = null,
)

/** `GET /v2/rac/renter/vehicles/:id` → full listing for the detail screen (guide §3.3). */
@Serializable
data class RacVehicleDetailDto(
    val vehicleId: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val color: String? = null,
    val category: String? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val engineCc: Int? = null,
    val specs: RacSpecsDto? = null,
    val features: List<String>? = null,
    val photos: List<String>? = null,
    val ratingAvg: Double? = null,
    val ratingCount: Int? = null,
    val featured: Boolean? = null,
    val pricing: RacPricingDto? = null,
    val mileagePolicy: RacMileagePolicyDto? = null,
    val company: RacCompanyDto? = null,
    val pickupBranches: List<RacBranchDto>? = null,
    val cdwTiers: List<RacCdwTierDto>? = null,
    val addons: List<RacAddonDto>? = null,
    val reviews: List<RacReviewDto>? = null,
    val currency: String? = null,
)

@Serializable
data class RacSpecsDto(
    val seats: Int? = null,
    val doors: Int? = null,
    val smallLuggage: Int? = null,
    val largeLuggage: Int? = null,
)

/** All 7 pricing periods (guide §3.3). */
@Serializable
data class RacPricingDto(
    val perHour: Double? = null,
    val h2to3: Double? = null,
    val h4to5: Double? = null,
    val h6to12: Double? = null,
    val perDay: Double? = null,
    val perWeek: Double? = null,
    val perMonth: Double? = null,
)

@Serializable
data class RacMileagePolicyDto(
    /** "limited" | "unlimited". */
    val policy: String? = null,
    val includedKmPerDay: Int? = null,
    val extraKmRate: Double? = null,
)

@Serializable
data class RacCompanyDto(
    val id: String? = null,
    val name: String? = null,
    val logo: String? = null,
    val ratingAvg: Double? = null,
    val ratingCount: Int? = null,
)

@Serializable
data class RacBranchDto(
    val id: String? = null,
    val name: String? = null,
    val city: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val parkingInstructions: String? = null,
)

/** A Derrbi-controlled CDW tier with its deposit + excess (guide §3.3). */
@Serializable
data class RacCdwTierDto(
    val tier: String? = null,
    val dailyRate: Double? = null,
    val deposit: Double? = null,
    val excess: Double? = null,
)

/** Optional paid add-on (shape is server-defined; kept flexible). */
@Serializable
data class RacAddonDto(
    val id: String? = null,
    val name: String? = null,
    val dailyRate: Double? = null,
    val amount: Double? = null,
)

@Serializable
data class RacReviewDto(
    val id: String? = null,
    val rating: Int? = null,
    val text: String? = null,
    val renterName: String? = null,
    val companyResponse: String? = null,
    val createdAt: String? = null,
)

/** `POST /v2/rac/renter/quote` body (guide §3.4). */
@Serializable
data class RacQuoteRequest(
    val vehicleId: String,
    /** ISO-8601 UTC. */
    val pickupAt: String,
    /** ISO-8601 UTC; must be after [pickupAt]. */
    val returnAt: String,
    val cdwTier: String,
    val addonIds: List<String> = emptyList(),
    val couponCode: String? = null,
)

/** `POST /v2/rac/renter/quote` → authoritative price breakdown (guide §3.4). */
@Serializable
data class RacQuoteData(
    val durationHours: Int? = null,
    val days: Int? = null,
    val baseAmount: Double? = null,
    val cdwAmount: Double? = null,
    val addonsAmount: Double? = null,
    val discount: Double? = null,
    val couponAmount: Double? = null,
    val vatAmount: Double? = null,
    val totalAmount: Double? = null,
    val depositAmount: Double? = null,
    val breakdown: List<RacQuoteLineDto>? = null,
    val cdwTier: String? = null,
    val excess: Double? = null,
    /** false → the window is taken; do not proceed to booking. */
    val available: Boolean? = null,
    val currency: String? = null,
)

@Serializable
data class RacQuoteLineDto(
    val label: String? = null,
    val amount: Double? = null,
)

/** `POST /v2/rac/renter/nafath/verify` request/response (guide §3.5). */
@Serializable
data class RacNafathRequest(val nationalId: String)

@Serializable
data class RacNafathData(
    val verified: Boolean? = null,
    val verifiedAt: String? = null,
)

/** `POST /v2/rac/renter/bookings` body — payment + deposit happen here (guide §3.6). */
@Serializable
data class RacBookingRequest(
    val vehicleId: String,
    val pickupAt: String,
    val returnAt: String,
    val cdwTier: String,
    val addonIds: List<String> = emptyList(),
    val couponCode: String? = null,
    val nafathVerified: Boolean = false,
    val renterNationalId: String? = null,
    val renterLicenseNo: String? = null,
    /** Saved-card token for a real PSP card flow; null in SIMULATE mode (guide §4). */
    val cardId: String? = null,
)

/** Booking record returned by create (guide §3.6) and the list (guide §3.7). */
@Serializable
data class RacBookingDto(
    val bookingId: String? = null,
    val bookingRef: String? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val vehicleId: String? = null,
    val vehicle: RacBookingVehicleDto? = null,
    val pickupAt: String? = null,
    val returnAt: String? = null,
    val durationHours: Int? = null,
    val cdwTier: String? = null,
    val baseAmount: Double? = null,
    val cdwAmount: Double? = null,
    val addonsAmount: Double? = null,
    val addons: List<RacAddonDto>? = null,
    val discount: Double? = null,
    val couponAmount: Double? = null,
    val couponCode: String? = null,
    val vatAmount: Double? = null,
    val totalAmount: Double? = null,
    val depositAmount: Double? = null,
    val refundAmount: Double? = null,
    val currency: String? = null,
)

@Serializable
data class RacBookingVehicleDto(
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val category: String? = null,
    val photo: String? = null,
)

/** `GET /v2/rac/renter/bookings` → `data:{ items, total }` (guide §3.7). */
@Serializable
data class RacBookingListData(
    val items: List<RacBookingDto>? = null,
    val total: Int? = null,
)

/** `GET /v2/rac/renter/bookings/:id` → full record (guide §3.8). */
@Serializable
data class RacBookingDetailDto(
    val bookingId: String? = null,
    val bookingRef: String? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val vehicle: RacBookingVehicleDto? = null,
    val pickupAt: String? = null,
    val returnAt: String? = null,
    val actualPickupAt: String? = null,
    val actualReturnAt: String? = null,
    val durationHours: Int? = null,
    val cdwTier: String? = null,
    val baseAmount: Double? = null,
    val cdwAmount: Double? = null,
    val addonsAmount: Double? = null,
    val addons: List<RacAddonDto>? = null,
    val discount: Double? = null,
    val couponAmount: Double? = null,
    val couponCode: String? = null,
    val vatAmount: Double? = null,
    val totalAmount: Double? = null,
    val depositAmount: Double? = null,
    val refundAmount: Double? = null,
    val companyName: String? = null,
    val pickupBranch: RacBranchDto? = null,
    val renter: RacRenterDto? = null,
    val penalties: RacPenaltiesDto? = null,
    val tripData: RacTripDataDto? = null,
    val extensionRequest: RacExtensionDto? = null,
    val paymentRef: String? = null,
    val authorizationRef: String? = null,
    val currency: String? = null,
)

@Serializable
data class RacRenterDto(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val nationalId: String? = null,
    val licenseNo: String? = null,
    val nafathVerified: Boolean? = null,
)

@Serializable
data class RacPenaltiesDto(
    val lateReturn: Double? = null,
    val lowFuel: Double? = null,
    val outOfZone: Double? = null,
    val penaltiesCollected: Boolean? = null,
)

/** Live telemetry during an active rental (guide §3.8). */
@Serializable
data class RacTripDataDto(
    val tajeerContractId: String? = null,
    val tajeerQrUrl: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val fuelStart: Int? = null,
    val fuelNow: Int? = null,
    val distanceKm: Double? = null,
    val lastPing: String? = null,
)

/** `POST /v2/rac/renter/bookings/:id/extend` request/response (guide §3.10). */
@Serializable
data class RacExtendRequest(val additionalHours: Int)

@Serializable
data class RacExtensionDto(
    val hours: Int? = null,
    val additionalCost: Double? = null,
    /** "pending" | "approved" | "rejected". */
    val status: String? = null,
)

/** `POST /v2/rac/renter/bookings/:id/cancel` body + `data:{ refundAmount }` (guide §3.9). */
@Serializable
data class RacCancelRequest(val reason: String? = null)

@Serializable
data class RacCancelData(val refundAmount: Double? = null)

/** `POST /v2/rac/renter/bookings/:id/rate` body (guide §3.11). */
@Serializable
data class RacRateRequest(
    /** 1–5. */
    val rating: Int,
    val text: String? = null,
)
