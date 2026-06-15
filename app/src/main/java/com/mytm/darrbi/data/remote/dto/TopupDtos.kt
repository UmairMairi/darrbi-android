package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST user/clickpay-hosted-method-top-up` request. Body: `{method, amount, name, email, city, country}`
 * — `method = 4` selects the ClickPay hosted payment page; name/email/city/country are billing details
 * ClickPay prefills (the customer can still edit them on the hosted page).
 */
@Serializable
data class HostedTopUpRequest(
    val method: Int,
    val amount: Int,
    val name: String,
    val email: String,
    val city: String,
    val country: String,
) {
    companion object {
        const val HOSTED_METHOD_CLICKPAY = 1
    }
}

/**
 * `POST user/clickpay-hosted-method-top-up` `data` payload: `{checkout_url, txnId, order_id}` (wrapped in
 * the standard MainEnvelope). The hosted page (`checkout_url`) is opened in a WebView; success/failure is
 * read from the final page. `redirect_url` is kept as a fallback for other gateway configs.
 */
@Serializable
data class HostedTopUpData(
    @SerialName("checkout_url") val checkoutUrl: String? = null,
    @SerialName("redirect_url") val redirectUrl: String? = null,
    val txnId: String? = null,
    @SerialName("order_id") val orderId: String? = null,
)

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
