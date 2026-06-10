package com.mytm.darrbi.domain.model

/** How a top-up was paid. */
enum class PaymentSource { Wallet, Card, Tabby, Tamara, Promotion, Unknown }

/** Display status of a top-up transaction. */
enum class TopupStatus { Successful, Refunded, Failed }

/** A single top-up transaction shown on the Topup Details screen. */
data class TopupTransaction(
    val id: String,
    val amount: Double,
    /** Raw ISO timestamp from the backend; formatted for display in the UI. */
    val timestampIso: String?,
    val source: PaymentSource,
    val status: TopupStatus,
    val isRefundable: Boolean,
    /** Card brand label (e.g. "Visa") for card payments, if known. */
    val cardBrand: String?,
    /** Last 4 digits of the card, if known. */
    val cardLast4: String?,
)
