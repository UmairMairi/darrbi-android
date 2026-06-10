package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.TopupTransactionDto
import com.mytm.darrbi.domain.model.PaymentSource
import com.mytm.darrbi.domain.model.TopupStatus
import com.mytm.darrbi.domain.model.TopupTransaction

private const val SOURCE_WALLET = 1
private const val SOURCE_CLICKPAY = 2
private const val SOURCE_TABBY = 3
private const val SOURCE_TAMARA = 4
private const val SOURCE_TAP = 5
private const val SOURCE_PROMOTION = 6

private const val STATUS_COMPLETED = 2
private const val STATUS_REFUNDED = 4

// "payment_method":"Visa" → "Visa"
private val brandRegex = Regex(""""payment_method"\s*:\s*"([^"]+)"""")
// last run of 4 digits in the masked card description, e.g. "4111 11## #### 1111" → "1111"
private val last4Regex = Regex("""(\d{4})(?!.*\d)""")

fun TopupTransactionDto.toDomain(): TopupTransaction {
    val src = when (source) {
        SOURCE_WALLET -> PaymentSource.Wallet
        SOURCE_CLICKPAY, SOURCE_TAP -> PaymentSource.Card
        SOURCE_TABBY -> PaymentSource.Tabby
        SOURCE_TAMARA -> PaymentSource.Tamara
        SOURCE_PROMOTION -> PaymentSource.Promotion
        else -> PaymentSource.Unknown
    }
    val st = when (status) {
        STATUS_COMPLETED -> TopupStatus.Successful
        STATUS_REFUNDED -> TopupStatus.Refunded
        else -> TopupStatus.Failed
    }
    val brand = eWalletAPIResponse?.let { brandRegex.find(it)?.groupValues?.get(1) }
    val last4 = if (src == PaymentSource.Card) {
        eWalletAPIResponse?.let { last4Regex.find(it)?.groupValues?.get(1) }
    } else {
        null
    }
    return TopupTransaction(
        id = id.orEmpty(),
        amount = transactionAmount ?: 0.0,
        timestampIso = updatedAt ?: createdAt,
        source = src,
        status = st,
        isRefundable = isRefundable == true,
        cardBrand = brand,
        cardLast4 = last4,
    )
}
