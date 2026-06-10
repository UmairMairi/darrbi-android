package com.mytm.darrbi.presentation.onboarding

import com.mytm.darrbi.core.common.SaudiId
import com.mytm.darrbi.domain.model.CarInfo

enum class OnboardingStep {
    Language,
    Mobile,
    Otp,
    UserType,
    // Rider branch
    Name,
    // Captain branch (shared)
    CaptainRideType,
    CaptainCarOption,
    // Captain: "Use Our Service Drive-A-Derrbi"
    CaptainDetails,
    // Captain: "I Own A Car"
    CarSequence,
    CarDetails,
    ApplicationStatus,
}

enum class UserType { Rider, Captain }

enum class CarOption { OwnCar, DriveADarrbi }

enum class RideType { Passenger, Pickup }

enum class ApplicationState { UnderReview, Rejected }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Language,
    val language: String = "en",
    val countryCode: String = "+966",
    val countryIso: String = "SA",
    val phoneMaxLength: Int = 9,
    val mobile: String = "",
    val whatsappOptIn: Boolean = false,
    val transactionId: String = "",
    val otp: String = "",
    val resendSeconds: Int = 0,
    val userType: UserType = UserType.Rider,
    val name: String = "",
    val referralCode: String = "",
    // Captain branch
    val rideType: RideType = RideType.Passenger,
    val carOption: CarOption = CarOption.OwnCar,
    val nidNumber: String = "",
    val licenseExpiry: String = "",
    // Captain: "I Own A Car"
    val carSequenceNo: String = "",
    val notOwnerAuthorized: Boolean = false,
    val carOwnerId: String = "",
    val foundCar: CarInfo? = null,
    val applicationState: ApplicationState = ApplicationState.UnderReview,
    /** Set when the host should leave onboarding for the dashboard (returning user login, or app approved). */
    val navigateToDashboard: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val completed: Boolean = false,
) {
    val canSubmitMobile: Boolean get() = mobile.length == phoneMaxLength && !isLoading
    val canVerifyOtp: Boolean get() = otp.length == OTP_LENGTH && !isLoading
    val canSubmitName: Boolean get() = name.isNotBlank() && !isLoading
    val canContinueRideType: Boolean get() = !isLoading
    val canSubmitCaptainDetails: Boolean get() = SaudiId.isValid(nidNumber) && licenseExpiry.isNotBlank() && !isLoading
    /** Show an inline error once a full 10-digit id is entered that fails validation. */
    val nidError: Boolean get() = nidNumber.length == 10 && !SaudiId.isValid(nidNumber)
    val canSubmitCarSequence: Boolean
        get() = carSequenceNo.length == CAR_SEQUENCE_LENGTH &&
            (!notOwnerAuthorized || SaudiId.isValid(carOwnerId)) && !isLoading
    /** Car Owner ID is a NID/Iqama — flag a complete (10-digit) but invalid entry. */
    val carOwnerIdError: Boolean get() = notOwnerAuthorized && carOwnerId.length == 10 && !SaudiId.isValid(carOwnerId)

    companion object {
        const val OTP_LENGTH = 4
        const val CAR_SEQUENCE_LENGTH = 9
    }
}

sealed interface OnboardingEvent {
    data class SelectLanguage(val tag: String) : OnboardingEvent
    data object ContinueFromLanguage : OnboardingEvent
    data class MobileChanged(val value: String) : OnboardingEvent
    data class SelectCountry(val dialCode: String, val iso: String, val phoneLength: Int) : OnboardingEvent
    data object ToggleWhatsapp : OnboardingEvent
    data object SubmitMobile : OnboardingEvent
    data class OtpChanged(val value: String) : OnboardingEvent
    data object VerifyOtp : OnboardingEvent
    data object ResendOtp : OnboardingEvent
    data class SelectUserType(val type: UserType) : OnboardingEvent
    data object ContinueFromUserType : OnboardingEvent
    // Rider
    data class NameChanged(val value: String) : OnboardingEvent
    data class ReferralChanged(val value: String) : OnboardingEvent
    data object SubmitName : OnboardingEvent
    // Captain (shared)
    data class SelectRideType(val type: RideType) : OnboardingEvent
    data object ContinueFromRideType : OnboardingEvent
    data class SelectCarOption(val option: CarOption) : OnboardingEvent
    data object ContinueFromCarOption : OnboardingEvent
    // Captain: Drive-A-Derrbi
    data class NidChanged(val value: String) : OnboardingEvent
    data class LicenseExpiryChanged(val value: String) : OnboardingEvent
    data object SubmitCaptainDetails : OnboardingEvent
    // Captain: I Own A Car
    data class CarSequenceChanged(val value: String) : OnboardingEvent
    data object ToggleNotOwnerAuthorized : OnboardingEvent
    data class CarOwnerIdChanged(val value: String) : OnboardingEvent
    data object SubmitCarSequence : OnboardingEvent
    data object ConfirmCar : OnboardingEvent
    data object UseDriveADerrbi : OnboardingEvent
    data object FinishApplication : OnboardingEvent

    data object Back : OnboardingEvent
    data object ConsumeError : OnboardingEvent
}
