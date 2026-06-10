package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.SendOtpData
import com.mytm.darrbi.data.remote.dto.VerifyOtpData
import com.mytm.darrbi.domain.model.AuthSession
import com.mytm.darrbi.domain.model.OtpRequest
import com.mytm.darrbi.domain.model.UserProfile

fun SendOtpData.toDomain(): OtpRequest = OtpRequest(
    transactionId = tId.orEmpty(),
    message = smsApiResponse?.message,
)

fun VerifyOtpData.toDomain(): AuthSession = AuthSession(
    token = token.orEmpty(),
    userId = userId ?: details?.id,
    isNameUpdated = details?.isNameUpdated ?: false,
    userType = 1 ?: details?.userType,
    profile = details.toUserProfile(),
)

private fun VerifyOtpData.UserDetails?.toUserProfile(): UserProfile {
    val name = this?.fullName?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(this?.firstName, this?.lastName).joinToString(" ").trim()
    return UserProfile(
        name = name,
        dateOfBirth = this?.dateOfBirth,
        referralCode = this?.referralCode,
        mobileNo = this?.mobileNo,
        profileImageUrl = this?.profileImage,
    )
}
