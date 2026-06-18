package com.mytm.darrbi.domain.model

/** A selectable trip-cancellation reason (`master/rejected-reason/type/{reasonType}`). */
data class CancelReason(
    val id: String,
    val text: String,
    val textArabic: String?,
)

/** `reasonType` values for the cancellation-reasons endpoint (mirrors ride-android's constants). */
object CancelReasonType {
    const val CAPTAIN = 2
    const val RIDER = 3
}
