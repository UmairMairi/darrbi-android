package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /trips/rider` and `GET /trips/driver` response. */
@Serializable
data class TripHistoryData(val trips: List<TripDto> = emptyList())

@Serializable
data class TripDto(
    val id: String? = null,
    val createdAt: String? = null,
    val riderAmount: Double? = null,
    val driverAmount: Double? = null,
    val status: Int? = null,
    val addresses: List<TripAddressDto> = emptyList(),
    val images: List<TripImageDto> = emptyList(),
    val driver: TripPersonDto? = null,
    val rider: TripPersonDto? = null,
    val riderReview: TripReviewDto? = null,
    val cab: TripCabDto? = null,
)

@Serializable
data class TripAddressDto(val addressType: Int? = null, val address: String? = null)

@Serializable
data class TripImageDto(val url: String? = null, val imageType: Int? = null)

@Serializable
data class TripPersonDto(
    val name: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val profileImage: String? = null,
)

@Serializable
data class TripReviewDto(val rating: Double? = null)

@Serializable
data class TripCabDto(val name: String? = null)
