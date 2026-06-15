package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.CarInfo
import com.mytm.darrbi.domain.model.IbanInfo
import com.mytm.darrbi.domain.model.Notification
import com.mytm.darrbi.domain.model.Ride
import com.mytm.darrbi.domain.model.TopupTransaction
import com.mytm.darrbi.domain.model.UserReport

/** Onboarding backend calls that run after OTP verification (same endpoints as ride-android). */
interface OnboardingRepository {
    /** Rider: `POST /user/set-rider-name`. */
    suspend fun setRiderName(mobileNo: String, name: String, referralCode: String?): ApiResult<Unit>

    /**
     * Captain: `POST /captains/guest-to-driver` — registers the verified guest as a driver with their
     * National ID ([nationalId]) and license expiry, before the car-registration steps.
     */
    suspend fun guestToDriver(
        mobileNo: String,
        nationalId: String,
        licenseExpiry: String,
        referralCode: String?,
    ): ApiResult<Unit>

    /**
     * Captain (I own a car): `POST /car-info-by-sequence`.
     * [ownerNationalId] is the car owner's National ID (the request's `userid`) — sent only when the captain
     * is NOT the owner (registering on the owner's authorization). Pass null when the captain owns the car,
     * so `userid` is omitted from the request.
     */
    suspend fun getCarBySequence(sequenceNumber: String, ownerNationalId: String?): ApiResult<CarInfo>

    /** Captain application: `POST /captains/become-captain`. */
    suspend fun submitCaptainApplication(application: CaptainApplication): ApiResult<Unit>

    /** Captain account: `GET /captains` — fetched after login for a returning captain. */
    suspend fun getCaptainDetails(): ApiResult<CaptainDetails>

    /** Flips the active mode (rider ⇄ captain) server-side via `POST /captains/change-driver-mode`. */
    suspend fun changeDriverMode(): ApiResult<Unit>

    /** Registers the device's FCM/push token with the server via `POST /user/update-customer`. */
    suspend fun updateDeviceToken(fcmToken: String): ApiResult<Unit>

    /** Validate an IBAN and resolve its bank: `GET /user/validate-iban/{iban}`. */
    suspend fun validateIban(iban: String): ApiResult<IbanInfo>

    /** The saved IBAN + bank for the current user: `GET /user/get-iban`. */
    suspend fun getIban(): ApiResult<IbanInfo>

    /** The user's wallet balance: `GET /user/get-balance`. */
    suspend fun getBalance(): ApiResult<Double>

    /**
     * Legal content as HTML: `GET /master/pages/{privacy-policy|terms-and-conditions}/{language}`.
     * [privacy] selects privacy policy vs terms & conditions; [language] is "en"/"ar".
     */
    suspend fun getLegalContent(privacy: Boolean, language: String): ApiResult<String>

    /** Top-up transaction history: `POST /user/top-up-history`. */
    suspend fun getTopupHistory(): ApiResult<List<TopupTransaction>>

    /** Refund part/all of a top-up: `POST /user/top-up/refund`. */
    suspend fun refundTopup(transactionId: String, amount: Double): ApiResult<Unit>

    /** Requests the ClickPay hosted-page URL for a top-up of [amount] (`method = 1`). */
    suspend fun hostedTopUpUrl(amount: Int): ApiResult<String>

    /** Rides the user took as a passenger: `GET /trips/rider`. */
    suspend fun getRidesTaken(): ApiResult<List<Ride>>

    /** Rides the user gave as a captain: `GET /trips/driver`. */
    suspend fun getRidesGiven(): ApiResult<List<Ride>>

    /** Support tickets the user raised: CMS `POST /api/v1/ticket/by-customer`. */
    suspend fun getUserReports(): ApiResult<List<UserReport>>

    /** Rate a resolved ticket: CMS `POST /api/v1/ticket/store-ratings`. */
    suspend fun rateReport(ticketId: Long, rating: Int): ApiResult<Unit>

    /** User notifications: `GET /user/notifications`. */
    suspend fun getNotifications(): ApiResult<List<Notification>>
}

/** Fields the onboarding flow collects for the captain application. */
data class CaptainApplication(
    val nationalId: String,
    val carSequenceNo: String,
    val carPlateNo: String,
    val carLicenceType: Int,
)
