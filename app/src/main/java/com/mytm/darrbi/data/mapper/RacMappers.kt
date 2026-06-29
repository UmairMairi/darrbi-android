package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.RacAddonDto
import com.mytm.darrbi.data.remote.dto.RacBookingDetailDto
import com.mytm.darrbi.data.remote.dto.RacBookingDto
import com.mytm.darrbi.data.remote.dto.RacBranchDto
import com.mytm.darrbi.data.remote.dto.RacCancelData
import com.mytm.darrbi.data.remote.dto.RacCdwTierDto
import com.mytm.darrbi.data.remote.dto.RacCompanyDto
import com.mytm.darrbi.data.remote.dto.RacExtensionDto
import com.mytm.darrbi.data.remote.dto.RacFiltersDto
import com.mytm.darrbi.data.remote.dto.RacMileagePolicyDto
import com.mytm.darrbi.data.remote.dto.RacNafathData
import com.mytm.darrbi.data.remote.dto.RacPenaltiesDto
import com.mytm.darrbi.data.remote.dto.RacPricingDto
import com.mytm.darrbi.data.remote.dto.RacQuoteData
import com.mytm.darrbi.data.remote.dto.RacRenterDto
import com.mytm.darrbi.data.remote.dto.RacReviewDto
import com.mytm.darrbi.data.remote.dto.RacSpecsDto
import com.mytm.darrbi.data.remote.dto.RacTripDataDto
import com.mytm.darrbi.data.remote.dto.RacVehicleDetailDto
import com.mytm.darrbi.data.remote.dto.RacVehicleSummaryDto
import com.mytm.darrbi.domain.model.BookingPenalties
import com.mytm.darrbi.domain.model.BookingRenter
import com.mytm.darrbi.domain.model.BookingVehicle
import com.mytm.darrbi.domain.model.CdwTierOption
import com.mytm.darrbi.domain.model.ExtensionResult
import com.mytm.darrbi.domain.model.MileagePolicy
import com.mytm.darrbi.domain.model.NafathResult
import com.mytm.darrbi.domain.model.PickupBranch
import com.mytm.darrbi.domain.model.QuoteLine
import com.mytm.darrbi.domain.model.RentalAddon
import com.mytm.darrbi.domain.model.RentalBooking
import com.mytm.darrbi.domain.model.RentalBookingDetail
import com.mytm.darrbi.domain.model.RentalBookingStatus
import com.mytm.darrbi.domain.model.RentalCancelResult
import com.mytm.darrbi.domain.model.RentalCompany
import com.mytm.darrbi.domain.model.RentalFilters
import com.mytm.darrbi.domain.model.RentalPricing
import com.mytm.darrbi.domain.model.RentalQuote
import com.mytm.darrbi.domain.model.RentalReview
import com.mytm.darrbi.domain.model.RentalSpecs
import com.mytm.darrbi.domain.model.RentalVehicleDetail
import com.mytm.darrbi.domain.model.RentalVehicleSummary
import com.mytm.darrbi.domain.model.TripTelemetry

/** Default when the server omits currency (the SA-region default; real currency is region-driven). */
private const val RAC_DEFAULT_CURRENCY = "SAR"

fun RacFiltersDto.toDomain(): RentalFilters = RentalFilters(
    cities = cities.orEmpty(),
    categories = categories.orEmpty(),
    cdwTiers = cdwTiers.orEmpty(),
    transmissions = transmissions.orEmpty(),
    sortOptions = sortOptions.orEmpty(),
)

/** null when the row has no usable vehicle id. */
fun RacVehicleSummaryDto.toDomain(): RentalVehicleSummary? {
    val id = vehicleId?.takeIf { it.isNotBlank() } ?: return null
    return RentalVehicleSummary(
        id = id,
        make = make.orEmpty(),
        model = model.orEmpty(),
        year = year,
        category = category?.takeIf { it.isNotBlank() },
        photo = photo?.takeIf { it.isNotBlank() },
        seats = seats,
        transmission = transmission?.takeIf { it.isNotBlank() },
        ratingAvg = ratingAvg,
        ratingCount = ratingCount,
        companyName = companyName?.takeIf { it.isNotBlank() },
        city = city?.takeIf { it.isNotBlank() },
        perDay = perDay ?: 0.0,
        currency = currency ?: RAC_DEFAULT_CURRENCY,
        featured = featured ?: false,
    )
}

fun RacVehicleDetailDto.toDomain(): RentalVehicleDetail? {
    val id = vehicleId?.takeIf { it.isNotBlank() } ?: return null
    return RentalVehicleDetail(
        id = id,
        make = make.orEmpty(),
        model = model.orEmpty(),
        year = year,
        color = color?.takeIf { it.isNotBlank() },
        category = category?.takeIf { it.isNotBlank() },
        fuelType = fuelType?.takeIf { it.isNotBlank() },
        transmission = transmission?.takeIf { it.isNotBlank() },
        engineCc = engineCc,
        specs = specs?.toDomain() ?: RentalSpecs(null, null, null, null),
        features = features.orEmpty(),
        photos = photos.orEmpty().filter { it.isNotBlank() },
        ratingAvg = ratingAvg,
        ratingCount = ratingCount,
        featured = featured ?: false,
        pricing = pricing?.toDomain() ?: RentalPricing(null, null, null, null, null, null, null),
        mileagePolicy = mileagePolicy?.toDomain() ?: MileagePolicy(null, null, null),
        company = company?.toDomain(),
        pickupBranches = pickupBranches.orEmpty().mapNotNull { it.toDomain() },
        cdwTiers = cdwTiers.orEmpty().mapNotNull { it.toDomain() },
        addons = addons.orEmpty().mapNotNull { it.toDomain() },
        reviews = reviews.orEmpty().map { it.toDomain() },
        currency = currency ?: RAC_DEFAULT_CURRENCY,
    )
}

private fun RacSpecsDto.toDomain() = RentalSpecs(seats, doors, smallLuggage, largeLuggage)

private fun RacPricingDto.toDomain() = RentalPricing(perHour, h2to3, h4to5, h6to12, perDay, perWeek, perMonth)

private fun RacMileagePolicyDto.toDomain() = MileagePolicy(policy, includedKmPerDay, extraKmRate)

private fun RacCompanyDto.toDomain(): RentalCompany? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    return RentalCompany(id, name.orEmpty(), logo?.takeIf { it.isNotBlank() }, ratingAvg, ratingCount)
}

private fun RacBranchDto.toDomain(): PickupBranch? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    return PickupBranch(id, name.orEmpty(), city, address, latitude, longitude, parkingInstructions)
}

/** null when the tier has no name (unusable in the picker). */
fun RacCdwTierDto.toDomain(): CdwTierOption? {
    val tier = tier?.takeIf { it.isNotBlank() } ?: return null
    return CdwTierOption(tier, dailyRate ?: 0.0, deposit ?: 0.0, excess ?: 0.0)
}

private fun RacAddonDto.toDomain(): RentalAddon? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    return RentalAddon(id, name.orEmpty(), dailyRate, amount)
}

fun RacReviewDto.toDomain(): RentalReview = RentalReview(
    id = id?.takeIf { it.isNotBlank() },
    rating = rating ?: 0,
    text = text?.takeIf { it.isNotBlank() },
    renterName = renterName?.takeIf { it.isNotBlank() },
    companyResponse = companyResponse?.takeIf { it.isNotBlank() },
    createdAtMillis = parseV2IsoMillis(createdAt),
)

fun RacQuoteData.toDomain(): RentalQuote = RentalQuote(
    durationHours = durationHours ?: 0,
    days = days ?: 0,
    baseAmount = baseAmount ?: 0.0,
    cdwAmount = cdwAmount ?: 0.0,
    addonsAmount = addonsAmount ?: 0.0,
    discount = discount ?: 0.0,
    couponAmount = couponAmount ?: 0.0,
    vatAmount = vatAmount ?: 0.0,
    totalAmount = totalAmount ?: 0.0,
    depositAmount = depositAmount ?: 0.0,
    breakdown = breakdown.orEmpty().mapNotNull { line ->
        val label = line.label?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        QuoteLine(label, line.amount ?: 0.0)
    },
    cdwTier = cdwTier.orEmpty(),
    excess = excess ?: 0.0,
    available = available ?: true,
    currency = currency ?: RAC_DEFAULT_CURRENCY,
)

fun RacNafathData.toDomain(): NafathResult = NafathResult(verified ?: false, parseV2IsoMillis(verifiedAt))

private fun RacBookingDto.vehicleDomain(): BookingVehicle? = vehicle?.let {
    BookingVehicle(it.make.orEmpty(), it.model.orEmpty(), it.year, it.category, it.photo?.takeIf { p -> p.isNotBlank() })
}

/** null when the row has no booking id. */
fun RacBookingDto.toDomain(): RentalBooking? {
    val id = bookingId?.takeIf { it.isNotBlank() } ?: return null
    return RentalBooking(
        bookingId = id,
        bookingRef = bookingRef?.takeIf { it.isNotBlank() },
        status = RentalBookingStatus.from(status),
        paymentStatus = paymentStatus?.takeIf { it.isNotBlank() },
        vehicle = vehicleDomain(),
        pickupAtMillis = parseV2IsoMillis(pickupAt),
        returnAtMillis = parseV2IsoMillis(returnAt),
        durationHours = durationHours,
        cdwTier = cdwTier?.takeIf { it.isNotBlank() },
        totalAmount = totalAmount ?: 0.0,
        depositAmount = depositAmount ?: 0.0,
        refundAmount = refundAmount ?: 0.0,
        currency = currency ?: RAC_DEFAULT_CURRENCY,
    )
}

fun RacBookingDetailDto.toDomain(): RentalBookingDetail? {
    val id = bookingId?.takeIf { it.isNotBlank() } ?: return null
    return RentalBookingDetail(
        bookingId = id,
        bookingRef = bookingRef?.takeIf { it.isNotBlank() },
        status = RentalBookingStatus.from(status),
        paymentStatus = paymentStatus?.takeIf { it.isNotBlank() },
        vehicle = vehicle?.let {
            BookingVehicle(it.make.orEmpty(), it.model.orEmpty(), it.year, it.category, it.photo?.takeIf { p -> p.isNotBlank() })
        },
        pickupAtMillis = parseV2IsoMillis(pickupAt),
        returnAtMillis = parseV2IsoMillis(returnAt),
        actualPickupAtMillis = parseV2IsoMillis(actualPickupAt),
        actualReturnAtMillis = parseV2IsoMillis(actualReturnAt),
        durationHours = durationHours,
        cdwTier = cdwTier?.takeIf { it.isNotBlank() },
        baseAmount = baseAmount ?: 0.0,
        cdwAmount = cdwAmount ?: 0.0,
        addonsAmount = addonsAmount ?: 0.0,
        discount = discount ?: 0.0,
        couponAmount = couponAmount ?: 0.0,
        couponCode = couponCode?.takeIf { it.isNotBlank() },
        vatAmount = vatAmount ?: 0.0,
        totalAmount = totalAmount ?: 0.0,
        depositAmount = depositAmount ?: 0.0,
        refundAmount = refundAmount ?: 0.0,
        companyName = companyName?.takeIf { it.isNotBlank() },
        pickupBranch = pickupBranch?.toDomain(),
        renter = renter?.toDomain(),
        penalties = penalties?.toDomain(),
        tripData = tripData?.toDomain(),
        extensionRequest = extensionRequest?.toDomain(),
        paymentRef = paymentRef?.takeIf { it.isNotBlank() },
        authorizationRef = authorizationRef?.takeIf { it.isNotBlank() },
        currency = currency ?: RAC_DEFAULT_CURRENCY,
    )
}

private fun RacRenterDto.toDomain() = BookingRenter(name, phone, email, nationalId, licenseNo, nafathVerified ?: false)

private fun RacPenaltiesDto.toDomain() = BookingPenalties(
    lateReturn = lateReturn ?: 0.0,
    lowFuel = lowFuel ?: 0.0,
    outOfZone = outOfZone ?: 0.0,
    collected = penaltiesCollected ?: false,
)

private fun RacTripDataDto.toDomain() = TripTelemetry(
    tajeerContractId = tajeerContractId?.takeIf { it.isNotBlank() },
    tajeerQrUrl = tajeerQrUrl?.takeIf { it.isNotBlank() },
    latitude = lat,
    longitude = lng,
    fuelStartPct = fuelStart,
    fuelNowPct = fuelNow,
    distanceKm = distanceKm,
    lastPingMillis = parseV2IsoMillis(lastPing),
)

/** Returns null when the booking carries no extension request. */
fun RacExtensionDto.toDomain(): ExtensionResult? {
    if (hours == null && additionalCost == null && status.isNullOrBlank()) return null
    return ExtensionResult(hours ?: 0, additionalCost ?: 0.0, status.orEmpty())
}

fun RacCancelData.toDomain(): RentalCancelResult = RentalCancelResult(refundAmount ?: 0.0)
