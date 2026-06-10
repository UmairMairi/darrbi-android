package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /api/v1/ticket/by-customer` request — scoped to the current ride customer. */
@Serializable
data class UserReportsRequest(
    @SerialName("ride_customer_id") val rideCustomerId: String,
)

/** Each entry groups a customer's tickets (ride-android's UserReportsModel.Datum). */
@Serializable
data class ReportGroupDto(
    val tickets: List<TicketDto> = emptyList(),
)

@Serializable
data class TicketDto(
    val id: Long? = null,
    val type: String? = null,
    val description: String? = null,
    @SerialName("status_id") val statusId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val rating: Int? = null,
)

/** `POST /api/v1/ticket/store-ratings` request. */
@Serializable
data class StoreRatingRequest(
    @SerialName("ticket_id") val ticketId: Long,
    val rating: Int,
)
