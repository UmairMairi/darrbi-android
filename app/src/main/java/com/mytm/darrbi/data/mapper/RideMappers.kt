package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.TripDto
import com.mytm.darrbi.data.remote.dto.TripPersonDto
import com.mytm.darrbi.domain.model.Ride

private const val ADDRESS_PICKUP = 1

/** [given] = true for a "Rides Given" (driver) trip — use the driver amount and show the rider. */
fun TripDto.toRide(given: Boolean): Ride {
    val pickup = addresses.firstOrNull { it.addressType == ADDRESS_PICKUP } ?: addresses.firstOrNull()
    val dropoff = addresses.lastOrNull { it.addressType != ADDRESS_PICKUP } ?: addresses.lastOrNull()
    val person = if (given) rider else driver
    return Ride(
        id = id.orEmpty(),
        timestampIso = createdAt,
        amount = (if (given) driverAmount else riderAmount) ?: 0.0,
        mapImageUrl = images.firstOrNull()?.url,
        rating = riderReview?.rating,
        pickupAddress = pickup?.address,
        dropoffAddress = dropoff?.address,
        personName = person?.displayName(),
        personImageUrl = person?.profileImage,
        carType = cab?.name,
    )
}

private fun TripPersonDto.displayName(): String? =
    name?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(firstName, lastName).joinToString(" ").trim().takeIf { it.isNotBlank() }
