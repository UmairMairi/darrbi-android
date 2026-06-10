package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.AuthSession
import com.mytm.darrbi.domain.model.OtpRequest
import com.mytm.darrbi.domain.repository.AuthRepository
import com.mytm.darrbi.domain.repository.SessionRepository
import javax.inject.Inject

class RequestOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(mobileNo: String): ApiResult<OtpRequest> =
        authRepository.requestOtp(mobileNo)
}

class VerifyOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(
        mobileNo: String,
        transactionId: String,
        otp: String,
        language: String,
    ): ApiResult<AuthSession> {
        val result = authRepository.verifyOtp(mobileNo, transactionId, otp, language)
        if (result is ApiResult.Success) {
            sessionRepository.saveSession(result.data.token, result.data.userId)
            sessionRepository.saveUser(result.data.profile)
        }
        return result
    }
}
