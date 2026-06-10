package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.DeviceInfoProvider
import com.mytm.darrbi.core.common.map
import com.mytm.darrbi.core.network.safeApiCall
import com.mytm.darrbi.core.network.unwrapMain
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.remote.dto.SendOtpRequest
import com.mytm.darrbi.data.remote.dto.VerifyOtpRequest
import com.mytm.darrbi.data.remote.service.AuthApi
import com.mytm.darrbi.domain.model.AuthSession
import com.mytm.darrbi.domain.model.OtpRequest
import com.mytm.darrbi.domain.repository.AuthRepository
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val deviceInfoProvider: DeviceInfoProvider,
) : AuthRepository {

    override suspend fun requestOtp(mobileNo: String): ApiResult<OtpRequest> =
        safeApiCall { authApi.sendOtp(SendOtpRequest(mobileNo)) }
            .unwrapMain()
            .map { it.toDomain() }

    override suspend fun verifyOtp(
        mobileNo: String,
        transactionId: String,
        otp: String,
        language: String,
    ): ApiResult<AuthSession> =
        safeApiCall {
            authApi.verifyOtp(
                deviceId = deviceInfoProvider.deviceId,
                body = VerifyOtpRequest(
                    mobileNo = mobileNo,
                    otp = otp,
                    tId = transactionId,
                    prefferedLanguage = language,
                ),
            )
        }.unwrapMain().map { it.toDomain() }
}
