package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.MainEnvelope
import com.mytm.darrbi.data.remote.dto.SendOtpData
import com.mytm.darrbi.data.remote.dto.SendOtpRequest
import com.mytm.darrbi.data.remote.dto.VerifyOtpData
import com.mytm.darrbi.data.remote.dto.VerifyOtpRequest
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** Auth endpoints on the Main API (ride-android: `/api/v2/sendotp`, `/api/v2/verifyotp`). */
interface AuthApi {
    @POST("api/v2/sendotp")
    suspend fun sendOtp(@Body body: SendOtpRequest): MainEnvelope<SendOtpData>

    @POST("api/v2/verifyotp")
    suspend fun verifyOtp(
        @Header("deviceId") deviceId: String,
        @Body body: VerifyOtpRequest,
    ): MainEnvelope<VerifyOtpData>
}
