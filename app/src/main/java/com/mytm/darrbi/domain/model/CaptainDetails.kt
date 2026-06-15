package com.mytm.darrbi.domain.model

/**
 * Captain account details from `GET /captains`, kept in the app session after login for a captain user.
 * A pragmatic subset of ride-android's CaptainDetailsModel — extend as screens need more fields.
 */
data class CaptainDetails(
    val id: String?,
    val driverName: String?,
    val driverNationalId: String?,
    val carPlateNo: String?,
    val carSequenceNo: String?,
    val approved: Boolean?,
    val isWaslApproved: Int?,
    val driverSubStatus: Int?,
    /** Active-mode flag from `GET /captains`: true = captain mode active, false = rider mode active. */
    val driverModeSwitch: Boolean?,
    /** Saved IBAN; null until the captain adds one. */
    val iban: String?,
    val mobileNo: String?,
    val dateOfBirth: String?,
    val overallRating: Double?,
)
