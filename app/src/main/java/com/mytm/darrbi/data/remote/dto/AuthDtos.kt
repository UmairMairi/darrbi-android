package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendOtpRequest(val mobileNo: String)

@Serializable
data class SendOtpData(
    val tId: String? = null,
    @SerialName("SmsApiResponse") val smsApiResponse: SmsApiResponse? = null,
) {
    @Serializable
    data class SmsApiResponse(val message: String? = null)
}

@Serializable
data class VerifyOtpRequest(
    val mobileNo: String,
    val otp: String,
    val tId: String,
    val prefferedLanguage: String,
)

@Serializable
data class VerifyOtpData(
    val token: String? = null,
    val userId: String? = null,
    val details: UserDetails? = null,
) {
    @Serializable
    data class UserDetails(
        val id: String? = null,
        /**
         * The canonical user id used for ALL socket/chat routing (ride-android: `data.details.userId`).
         * This is the numeric account id (the trip's riderId), NOT the top-level session [userId].
         */
        val userId: String? = null,
        /** Whether the user has set their name/profile (false = brand-new user, continue onboarding). */
        val isNameUpdated: Boolean? = null,
        /** 1 = rider, 2 = captain. */
        val userType: Int? = null,
        val firstName: String? = null,
        val lastName: String? = null,
        val fullName: String? = null,
        val dateOfBirth: String? = null,
        val referralCode: String? = null,
        val mobileNo: String? = null,
        val profileImage: String? = null,
    )
}
