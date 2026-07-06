package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.MainEnvelope
import com.mytm.darrbi.data.remote.dto.BalanceData
import com.mytm.darrbi.data.remote.dto.BecomeCaptainRequest
import com.mytm.darrbi.data.remote.dto.CaptainDetailsData
import com.mytm.darrbi.data.remote.dto.CarBySequenceData
import com.mytm.darrbi.data.remote.dto.CarBySequenceRequest
import com.mytm.darrbi.data.remote.dto.GuestToDriverRequest
import com.mytm.darrbi.data.remote.dto.IbanValidationData
import com.mytm.darrbi.data.remote.dto.LegalData
import com.mytm.darrbi.data.remote.dto.NotificationDto
import com.mytm.darrbi.data.remote.dto.RefundRequest
import com.mytm.darrbi.data.remote.dto.HostedTopUpData
import com.mytm.darrbi.data.remote.dto.HostedTopUpRequest
import com.mytm.darrbi.data.remote.dto.TopupHistoryData
import com.mytm.darrbi.data.remote.dto.TopupHistoryRequest
import com.mytm.darrbi.data.remote.dto.TripHistoryData
import com.mytm.darrbi.data.remote.dto.UpdateCustomerData
import com.mytm.darrbi.data.remote.dto.UpdateCustomerRequest
import com.mytm.darrbi.data.remote.dto.UserDetailsData
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Post-OTP onboarding endpoints on the Main API (same as ride-android). */
interface OnboardingApi {
    @POST("user/update-customer")
    suspend fun updateCustomer(@Body body: UpdateCustomerRequest): MainEnvelope<UpdateCustomerData>

    @POST("captains/guest-to-driver")
    suspend fun guestToDriver(@Body body: GuestToDriverRequest): MainEnvelope<JsonElement>

    @POST("car-info-by-sequence")
    suspend fun getCarBySequence(@Body body: CarBySequenceRequest): MainEnvelope<CarBySequenceData>

    @POST("captains/become-captain")
    suspend fun becomeCaptain(@Body body: BecomeCaptainRequest): MainEnvelope<JsonElement>

    @GET("captains")
    suspend fun getCaptainDetails(): MainEnvelope<CaptainDetailsData>

    /** The signed-in user's full profile (ride-android's `/getuserdetails`). */
    @GET("getuserdetails")
    suspend fun getUserDetails(): MainEnvelope<UserDetailsData>

    /** Server-side toggle of the active mode (rider ⇄ captain); no body, body ignored. */
    @POST("captains/change-driver-mode")
    suspend fun changeDriverMode(): MainEnvelope<JsonElement>

    @GET("user/validate-iban/{iban}")
    suspend fun validateIban(@Path("iban") iban: String): MainEnvelope<IbanValidationData>

    @GET("user/get-iban")
    suspend fun getIban(): MainEnvelope<IbanValidationData>

    @GET("user/get-balance")
    suspend fun getBalance(): MainEnvelope<BalanceData>

    @GET("master/pages/terms-and-conditions/{language}")
    suspend fun getTerms(@Path("language") language: String): MainEnvelope<LegalData>

    @GET("master/pages/privacy-policy/{language}")
    suspend fun getPrivacyPolicy(@Path("language") language: String): MainEnvelope<LegalData>

    @POST("user/top-up-history")
    suspend fun getTopupHistory(@Body body: TopupHistoryRequest): MainEnvelope<TopupHistoryData>

    @POST("user/clickpay-hosted-method-top-up")
    suspend fun clickpayHostedTopUp(
        @Header("Authorization") authorization: String,
        @Body body: HostedTopUpRequest,
    ): MainEnvelope<HostedTopUpData>

    @POST("user/top-up/refund")
    suspend fun refundTopup(@Body body: RefundRequest): MainEnvelope<JsonElement>

    @GET("trips/rider")
    suspend fun getRiderTrips(): MainEnvelope<TripHistoryData>

    @GET("trips/driver")
    suspend fun getDriverTrips(): MainEnvelope<TripHistoryData>

    @GET("user/notifications")
    suspend fun getNotifications(@Query("page") page: Int, @Query("limit") limit: Int): MainEnvelope<List<NotificationDto>>
}
