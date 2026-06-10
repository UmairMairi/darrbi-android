package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.AuthSession
import com.mytm.darrbi.domain.model.OtpRequest

interface AuthRepository {
    suspend fun requestOtp(mobileNo: String): ApiResult<OtpRequest>

    suspend fun verifyOtp(
        mobileNo: String,
        transactionId: String,
        otp: String,
        language: String,
    ): ApiResult<AuthSession>
}
