package com.mytm.darrbi.domain.model

/** Result of requesting an OTP: a transaction id to echo back on verify, plus an optional SMS message. */
data class OtpRequest(
    val transactionId: String,
    val message: String?,
)

/** An authenticated session established after verifying an OTP. */
data class AuthSession(
    val token: String,
    val userId: String?,
    /** False for a brand-new user who must finish onboarding (select user type, set name). */
    val isNameUpdated: Boolean,
    /** 1 = rider, 2 = captain; null if the backend didn't return it. */
    val userType: Int?,
    /** Profile fields from the verify-OTP response (for the profile screen). */
    val profile: UserProfile,
)
