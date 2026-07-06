package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.DeviceInfoProvider
import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.CarInfo
import com.mytm.darrbi.domain.model.IbanInfo
import com.mytm.darrbi.domain.model.Notification
import com.mytm.darrbi.domain.model.Ride
import com.mytm.darrbi.domain.model.TopupTransaction
import com.mytm.darrbi.domain.model.UserProfile
import com.mytm.darrbi.domain.model.UserReport
import com.mytm.darrbi.domain.repository.CaptainApplication
import com.mytm.darrbi.domain.repository.OnboardingRepository
import com.mytm.darrbi.domain.repository.SessionRepository
import javax.inject.Inject

class SetRiderNameUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(mobileNo: String, name: String, referralCode: String?): ApiResult<Unit> =
        repository.setRiderName(mobileNo, name, referralCode?.takeIf { it.isNotBlank() })
}

class GuestToDriverUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(
        mobileNo: String,
        nationalId: String,
        licenseExpiry: String,
        referralCode: String?,
    ): ApiResult<Unit> =
        repository.guestToDriver(mobileNo, nationalId, licenseExpiry, referralCode?.takeIf { it.isNotBlank() })
}

class GetCarBySequenceUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(sequenceNumber: String, ownerNationalId: String?): ApiResult<CarInfo> =
        repository.getCarBySequence(sequenceNumber, ownerNationalId)
}

class SubmitCaptainApplicationUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(application: CaptainApplication): ApiResult<Unit> =
        repository.submitCaptainApplication(application)
}

class ValidateIbanUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(iban: String): ApiResult<IbanInfo> =
        repository.validateIban(iban.filter { !it.isWhitespace() })
}

class GetIbanUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<IbanInfo> = repository.getIban()
}

class GetBalanceUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<Double> = repository.getBalance()
}

class GetLegalContentUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(privacy: Boolean, language: String): ApiResult<String> =
        repository.getLegalContent(privacy, language)
}

class GetTopupHistoryUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<List<TopupTransaction>> = repository.getTopupHistory()
}

class RefundTopupUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(transactionId: String, amount: Double): ApiResult<Unit> =
        repository.refundTopup(transactionId, amount)
}

class GetRidesTakenUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<List<Ride>> = repository.getRidesTaken()
}

class GetRidesGivenUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<List<Ride>> = repository.getRidesGiven()
}

class GetUserReportsUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<List<UserReport>> = repository.getUserReports()
}

class RateReportUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(ticketId: Long, rating: Int): ApiResult<Unit> =
        repository.rateReport(ticketId, rating)
}

class GetNotificationsUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<List<Notification>> = repository.getNotifications()
}

/** Fetches `GET /captains` and, on success, stores the details in the current app session. */
class GetCaptainDetailsUseCase @Inject constructor(
    private val repository: OnboardingRepository,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(): ApiResult<CaptainDetails> {
        val result = repository.getCaptainDetails()
        if (result is ApiResult.Success) sessionRepository.saveCaptain(result.data)
        return result
    }
}

/** The signed-in user's profile: `GET /getuserdetails`. */
class GetUserDetailsUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<UserProfile> = repository.getUserDetails()
}

/** Flips the active mode (rider ⇄ captain): `POST /captains/change-driver-mode`. */
class ChangeDriverModeUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(): ApiResult<Unit> = repository.changeDriverMode()
}

/** Requests the ClickPay hosted-page URL to top up the wallet by [amount] (opened in a WebView). */
class GetHostedTopUpUrlUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(amount: Int): ApiResult<String> = repository.hostedTopUpUrl(amount)
}

/** Reads the device's FCM token and registers it with the server (best-effort, post-login). */
class UpdateDeviceTokenUseCase @Inject constructor(
    private val repository: OnboardingRepository,
    private val deviceInfo: DeviceInfoProvider,
) {
    suspend operator fun invoke(): ApiResult<Unit> = repository.updateDeviceToken(deviceInfo.fcmToken())
}
