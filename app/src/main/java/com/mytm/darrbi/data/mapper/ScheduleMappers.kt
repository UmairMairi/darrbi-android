package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.C2cCityDto
import com.mytm.darrbi.data.remote.dto.C2cOpenTripDto
import com.mytm.darrbi.data.remote.dto.C2cQuoteData
import com.mytm.darrbi.data.remote.dto.CancelScheduleData
import com.mytm.darrbi.data.remote.dto.CreateScheduleData
import com.mytm.darrbi.data.remote.dto.UpcomingScheduleDto
import com.mytm.darrbi.domain.model.C2cCity
import com.mytm.darrbi.domain.model.C2cOpenScheduledTrip
import com.mytm.darrbi.domain.model.C2cQuote
import com.mytm.darrbi.domain.model.FareRange
import com.mytm.darrbi.domain.model.ScheduleCancelResult
import com.mytm.darrbi.domain.model.ScheduledTrip
import com.mytm.darrbi.domain.model.ScheduledTripState
import com.mytm.darrbi.domain.model.UpcomingScheduledTrip

private const val DEFAULT_CURRENCY = "SAR"
private const val STATUS_AWAITING_BIDS = 15

/** null when the city has no id (unusable in the picker). */
fun C2cCityDto.toDomain(): C2cCity? {
    val cityId = id?.takeIf { it.isNotBlank() } ?: return null
    return C2cCity(
        id = cityId,
        name = name.orEmpty(),
        nameArabic = nameAr?.takeIf { it.isNotBlank() },
        centroidLat = centroidLat,
        centroidLng = centroidLng,
    )
}

fun C2cQuoteData.toDomain(): C2cQuote = C2cQuote(
    recommendedFare = recommendedFare ?: 0.0,
    minFare = minFare ?: 0.0,
    maxFare = maxFare ?: 0.0,
    routeDistanceKm = routeDistanceKm,
    currency = currency ?: DEFAULT_CURRENCY,
    pricedBy = pricedBy?.takeIf { it.isNotBlank() },
)

/** [offeredFare] is the fare the rider submitted (echoed back into the fare range). */
fun CreateScheduleData.toDomain(offeredFare: Double): ScheduledTrip = ScheduledTrip(
    tripId = id.orEmpty(),
    tripStatus = tripStatus ?: STATUS_AWAITING_BIDS,
    scheduledState = ScheduledTripState.from(scheduledState),
    originCityId = originCityId.orEmpty(),
    destinationCityId = destinationCityId.orEmpty(),
    seatsRequested = seatsRequested ?: 1,
    scheduledDepartureAtMillis = parseV2IsoMillis(scheduledDepartureAt),
    biddingClosesAtMillis = parseV2IsoMillis(biddingClosesAt),
    fareRange = FareRange(
        currency = currency ?: DEFAULT_CURRENCY,
        recommended = recommendedFare ?: 0.0,
        min = minFare ?: 0.0,
        max = maxFare ?: 0.0,
        riderOfferedFare = riderOfferedFare ?: offeredFare,
    ),
    currency = currency ?: DEFAULT_CURRENCY,
)

/** null when the row has no trip id. */
fun UpcomingScheduleDto.toDomain(): UpcomingScheduledTrip? {
    val id = tripId?.takeIf { it.isNotBlank() } ?: return null
    return UpcomingScheduledTrip(
        tripId = id,
        tripNo = tripNo,
        tripStatus = status ?: STATUS_AWAITING_BIDS,
        scheduledState = ScheduledTripState.from(scheduledState),
        originCityId = originCityId.orEmpty(),
        destinationCityId = destinationCityId.orEmpty(),
        seatsRequested = seatsRequested ?: 1,
        seatsConfirmed = seatsConfirmed,
        scheduledDepartureAtMillis = parseV2IsoMillis(scheduledDepartureAt),
        biddingClosesAtMillis = parseV2IsoMillis(biddingClosesAt),
        riderOfferedFare = riderOfferedFare ?: 0.0,
        driverId = driverId?.takeIf { it.isNotBlank() },
        currency = currency ?: DEFAULT_CURRENCY,
    )
}

/** null when the row has no trip/cab id (unbiddable). */
fun C2cOpenTripDto.toDomain(): C2cOpenScheduledTrip? {
    val id = tripId?.takeIf { it.isNotBlank() } ?: return null
    val cab = cabId?.takeIf { it.isNotBlank() } ?: return null
    return C2cOpenScheduledTrip(
        tripId = id,
        cabId = cab,
        originCityId = originCityId.orEmpty(),
        destinationCityId = destinationCityId.orEmpty(),
        scheduledDepartureAtMillis = parseV2IsoMillis(scheduledDepartureAt),
        seatsRequested = seatsRequested ?: 1,
        riderOfferedFare = riderOfferedFare ?: 0.0,
        recommendedFare = recommendedFare ?: riderOfferedFare ?: 0.0,
        pickupLat = pickup?.latitude,
        pickupLng = pickup?.longitude,
        dropoffLat = dropoff?.latitude,
        dropoffLng = dropoff?.longitude,
        riderId = riderId?.takeIf { it.isNotBlank() },
        currency = currency ?: DEFAULT_CURRENCY,
    )
}

fun CancelScheduleData.toDomain(): ScheduleCancelResult = ScheduleCancelResult(
    tripStatus = tripStatus ?: 0,
    scheduledState = ScheduledTripState.from(scheduledState),
    feeApplied = feeApplied ?: false,
)
