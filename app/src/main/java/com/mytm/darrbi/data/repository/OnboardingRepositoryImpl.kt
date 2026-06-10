package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.core.common.map
import com.mytm.darrbi.core.network.safeApiCall
import com.mytm.darrbi.core.network.unwrapCms
import com.mytm.darrbi.core.network.unwrapMain
import com.mytm.darrbi.core.network.unwrapMainUnit
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.mapper.toRide
import com.mytm.darrbi.data.remote.dto.BecomeCaptainRequest
import com.mytm.darrbi.data.remote.dto.CarBySequenceRequest
import com.mytm.darrbi.data.remote.dto.DrivingMode
import com.mytm.darrbi.data.remote.dto.GuestToDriverRequest
import com.mytm.darrbi.data.remote.dto.RefundRequest
import com.mytm.darrbi.data.remote.dto.SetRiderNameRequest
import com.mytm.darrbi.data.remote.dto.StoreRatingRequest
import com.mytm.darrbi.data.remote.dto.TopupHistoryRequest
import com.mytm.darrbi.data.remote.dto.UserReportsRequest
import com.mytm.darrbi.data.remote.service.CmsApi
import com.mytm.darrbi.data.remote.service.OnboardingApi
import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.CarInfo
import com.mytm.darrbi.domain.model.IbanInfo
import com.mytm.darrbi.domain.model.Notification
import com.mytm.darrbi.domain.model.Ride
import com.mytm.darrbi.domain.model.TopupTransaction
import com.mytm.darrbi.domain.model.UserReport
import com.mytm.darrbi.domain.repository.CaptainApplication
import com.mytm.darrbi.domain.repository.OnboardingRepository
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    private val api: OnboardingApi,
    private val cmsApi: CmsApi,
    private val userIdProvider: UserIdProvider,
) : OnboardingRepository {

    override suspend fun setRiderName(
        mobileNo: String,
        name: String,
        referralCode: String?,
    ): ApiResult<Unit> =
        safeApiCall {
            api.setRiderName(SetRiderNameRequest(mobileNo = mobileNo, name = name, referalCode = referralCode))
        }.unwrapMainUnit()

    override suspend fun guestToDriver(
        mobileNo: String,
        nationalId: String,
        licenseExpiry: String,
        referralCode: String?,
    ): ApiResult<Unit> =
        safeApiCall {
            api.guestToDriver(
                GuestToDriverRequest(
                    mobileNo = mobileNo,
                    userId = nationalId,
                    licExpiry = licenseExpiry,
                    referalCode = referralCode,
                ),
            )
        }.unwrapMainUnit()

    override suspend fun getCarBySequence(sequenceNumber: String, ownerNationalId: String?): ApiResult<CarInfo> =
        safeApiCall {
            // `userid` carries the car owner's National ID only when registering on someone else's
            // authorization; when the captain owns the car it is null and omitted from the body.
            api.getCarBySequence(
                CarBySequenceRequest(sequenceNumber = sequenceNumber, userid = ownerNationalId),
            )
        }.unwrapMain().map { it.toDomain() }

    override suspend fun submitCaptainApplication(application: CaptainApplication): ApiResult<Unit> =
        safeApiCall {
            api.becomeCaptain(
                BecomeCaptainRequest(
                    subscriptionId = DEFAULT_SUBSCRIPTION_ID,
                    autoRenewal = false,
                    driverNationalId = application.nationalId,
                    carPlateNo = application.carPlateNo,
                    carSequenceNo = application.carSequenceNo,
                    carLicenceType = application.carLicenceType,
                    cab = DEFAULT_CAB_ID,
                    drivingModes = listOf(DrivingMode(drivingMode = 1)),
                    acceptTC = true,
                ),
            )
        }.unwrapMainUnit()

    override suspend fun getCaptainDetails(): ApiResult<CaptainDetails> =
        safeApiCall { api.getCaptainDetails() }.unwrapMain().map { it.toDomain() }

    override suspend fun validateIban(iban: String): ApiResult<IbanInfo> =
        safeApiCall { api.validateIban(iban) }.unwrapMain().map { it.toDomain() }

    override suspend fun getIban(): ApiResult<IbanInfo> =
        safeApiCall { api.getIban() }.unwrapMain().map { it.toDomain() }

    override suspend fun getBalance(): ApiResult<Double> =
        safeApiCall { api.getBalance() }.unwrapMain().map { it.balance ?: 0.0 }

    override suspend fun getLegalContent(privacy: Boolean, language: String): ApiResult<String> =
        safeApiCall { if (privacy) api.getPrivacyPolicy(language) else api.getTerms(language) }
            .unwrapMain()
            .map { legal ->
                // Concatenate the sections into one HTML document (mirrors ride-android's rendering).
                legal.data.joinToString("") { item ->
                    val heading = item.title?.takeIf { it.isNotBlank() }?.let { "<h3>$it</h3>" }.orEmpty()
                    "$heading${item.description.orEmpty()}<br><br>"
                }
            }

    override suspend fun getTopupHistory(): ApiResult<List<TopupTransaction>> =
        safeApiCall { api.getTopupHistory(TopupHistoryRequest()) }
            .unwrapMain()
            .map { data -> data.transactions.map { it.toDomain() } }

    override suspend fun refundTopup(transactionId: String, amount: Double): ApiResult<Unit> =
        safeApiCall { api.refundTopup(RefundRequest(transactionId = transactionId, refundAmount = amount)) }
            .unwrapMainUnit()

    override suspend fun getRidesTaken(): ApiResult<List<Ride>> =
        safeApiCall { api.getRiderTrips() }.unwrapMain().map { data -> data.trips.map { it.toRide(given = false) } }

    override suspend fun getRidesGiven(): ApiResult<List<Ride>> =
        safeApiCall { api.getDriverTrips() }.unwrapMain().map { data -> data.trips.map { it.toRide(given = true) } }

    override suspend fun getUserReports(): ApiResult<List<UserReport>> =
        safeApiCall { cmsApi.getUserReports(UserReportsRequest(rideCustomerId = userIdProvider.userId.orEmpty())) }
            .unwrapCms()
            .map { groups -> groups.flatMap { it.tickets }.map { it.toDomain() } }

    override suspend fun rateReport(ticketId: Long, rating: Int): ApiResult<Unit> =
        safeApiCall { cmsApi.storeRating(StoreRatingRequest(ticketId = ticketId, rating = rating)) }
            .unwrapCms()
            .map { }

    override suspend fun getNotifications(): ApiResult<List<Notification>> =
        safeApiCall { api.getNotifications(1, 50) }.unwrapMain().map { list -> list.map { it.toDomain() } }

    private companion object {
        // ride-android sends these fixed values; cab-type + subscription-plan selection live in steps
        // not yet part of this onboarding flow.
        const val DEFAULT_CAB_ID = "db8db63e-1501-44c2-ac1b-c4bc213e92dc"
        const val DEFAULT_SUBSCRIPTION_ID = "testSubscriptionId"
    }
}
