package com.mytm.darrbi.domain.model

import kotlinx.serialization.Serializable

/** User profile fields carried in the verify-OTP response, kept in the session for the profile screen. */
@Serializable
data class UserProfile(
    val name: String,
    val dateOfBirth: String?,
    val referralCode: String?,
    val mobileNo: String?,
    val profileImageUrl: String?,
    /** Average rating shown on the profile header (null when not provided by the backend). */
    val rating: Double? = null,
)
