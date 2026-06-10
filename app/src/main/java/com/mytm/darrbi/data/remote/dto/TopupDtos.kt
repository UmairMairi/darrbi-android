package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.Serializable

/** `POST /user/top-up-history` request — entityType "3" filters to top-up transactions (ride-android). */
@Serializable
data class TopupHistoryRequest(val filters: Filters = Filters()) {
    @Serializable
    data class Filters(val entityType: String = "3")
}

@Serializable
data class TopupHistoryData(
    val transactions: List<TopupTransactionDto> = emptyList(),
    val totalCount: Long? = null,
)

@Serializable
data class TopupTransactionDto(
    val id: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val transactionAmount: Double? = null,
    /** Payment source: 1 wallet, 2 ClickPay, 3 Tabby, 4 Tamara, 5 Tap, 6 promotion. */
    val source: Int? = null,
    /** Transaction status: 2 completed, 4 refunded, else failed. */
    val status: Int? = null,
    val isRefundable: Boolean? = null,
    val transactionId: String? = null,
    /** Raw gateway response JSON (card brand + masked number) for card payments. */
    val eWalletAPIResponse: String? = null,
)

/** `POST /user/top-up/refund` request. */
@Serializable
data class RefundRequest(val transactionId: String, val refundAmount: Double)
