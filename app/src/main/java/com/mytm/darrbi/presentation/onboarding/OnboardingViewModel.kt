package com.mytm.darrbi.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.datastore.LanguageStore
import com.mytm.darrbi.domain.model.AuthSession
import com.mytm.darrbi.domain.repository.CaptainApplication
import com.mytm.darrbi.domain.usecase.GetCaptainDetailsUseCase
import com.mytm.darrbi.domain.usecase.GetCarBySequenceUseCase
import com.mytm.darrbi.domain.usecase.GuestToDriverUseCase
import com.mytm.darrbi.domain.usecase.RequestOtpUseCase
import com.mytm.darrbi.domain.usecase.SetRiderNameUseCase
import com.mytm.darrbi.domain.usecase.SubmitCaptainApplicationUseCase
import com.mytm.darrbi.domain.usecase.UpdateDeviceTokenUseCase
import com.mytm.darrbi.domain.usecase.VerifyOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val requestOtp: RequestOtpUseCase,
    private val verifyOtp: VerifyOtpUseCase,
    private val updateDeviceToken: UpdateDeviceTokenUseCase,
    private val setRiderName: SetRiderNameUseCase,
    private val guestToDriver: GuestToDriverUseCase,
    private val getCaptainDetails: GetCaptainDetailsUseCase,
    private val getCarBySequence: GetCarBySequenceUseCase,
    private val submitCaptainApplication: SubmitCaptainApplicationUseCase,
    private val languageStore: LanguageStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(language = languageStore.language))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var resendJob: Job? = null

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.SelectLanguage -> selectLanguage(event.tag)
            OnboardingEvent.ContinueFromLanguage -> goTo(OnboardingStep.Mobile)
            is OnboardingEvent.MobileChanged ->
                _state.update { it.copy(mobile = event.value.filter(Char::isDigit).take(it.phoneMaxLength)) }
            is OnboardingEvent.SelectCountry -> _state.update {
                it.copy(
                    countryCode = event.dialCode,
                    countryIso = event.iso,
                    phoneMaxLength = event.phoneLength,
                    // Trim any digits beyond the newly selected country's allowed length.
                    mobile = it.mobile.take(event.phoneLength),
                )
            }
            OnboardingEvent.ToggleWhatsapp -> _state.update { it.copy(whatsappOptIn = !it.whatsappOptIn) }
            OnboardingEvent.SubmitMobile -> submitMobile()
            is OnboardingEvent.OtpChanged -> _state.update { it.copy(otp = event.value) }
            OnboardingEvent.VerifyOtp -> doVerifyOtp()
            OnboardingEvent.ResendOtp -> resend()
            is OnboardingEvent.SelectUserType -> _state.update { it.copy(userType = event.type) }
            OnboardingEvent.ContinueFromUserType -> continueFromUserType()
            is OnboardingEvent.NameChanged -> _state.update { it.copy(name = event.value) }
            is OnboardingEvent.ReferralChanged -> _state.update { it.copy(referralCode = event.value) }
            OnboardingEvent.SubmitName -> submitName()
            is OnboardingEvent.SelectRideType -> _state.update { it.copy(rideType = event.type) }
            OnboardingEvent.ContinueFromRideType ->
                if (_state.value.canContinueRideType) goTo(OnboardingStep.CaptainCarOption)
            is OnboardingEvent.SelectCarOption -> _state.update { it.copy(carOption = event.option) }
            OnboardingEvent.ContinueFromCarOption -> goTo(OnboardingStep.CaptainDetails)
            is OnboardingEvent.NidChanged ->
                _state.update { it.copy(nidNumber = event.value.filter(Char::isDigit).take(10)) }
            is OnboardingEvent.LicenseExpiryChanged -> _state.update { it.copy(licenseExpiry = event.value) }
            OnboardingEvent.SubmitCaptainDetails -> submitCaptainDetails()
            is OnboardingEvent.CarSequenceChanged ->
                _state.update { it.copy(carSequenceNo = event.value.filter(Char::isDigit).take(OnboardingUiState.CAR_SEQUENCE_LENGTH)) }
            OnboardingEvent.ToggleNotOwnerAuthorized ->
                _state.update { it.copy(notOwnerAuthorized = !it.notOwnerAuthorized) }
            is OnboardingEvent.CarOwnerIdChanged ->
                _state.update { it.copy(carOwnerId = event.value.filter(Char::isDigit).take(10)) }
            OnboardingEvent.SubmitCarSequence -> submitCarSequence()
            OnboardingEvent.ConfirmCar -> submitApplication()
            OnboardingEvent.UseDriveADerrbi ->
                _state.update { it.copy(carOption = CarOption.DriveADarrbi, foundCar = null, step = OnboardingStep.CaptainDetails) }
            OnboardingEvent.FinishApplication -> _state.update { it.copy(completed = true) }
            OnboardingEvent.Back -> back()
            OnboardingEvent.ConsumeError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun selectLanguage(tag: String) {
        _state.update { it.copy(language = tag) }
        viewModelScope.launch { languageStore.setLanguage(tag) }
    }

    private fun submitMobile() {
        val current = _state.value
        if (!current.canSubmitMobile) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = requestOtp(current.fullMobile())) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            transactionId = result.data.transactionId,
                            otp = "",
                            step = OnboardingStep.Otp,
                        )
                    }
                    startResendTimer()
                }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun doVerifyOtp() {
        val current = _state.value
        if (!current.canVerifyOtp) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = verifyOtp(
                mobileNo = current.fullMobile(),
                transactionId = current.transactionId,
                otp = current.otp,
                language = current.language,
            )
            when (result) {
                is ApiResult.Success -> {
                    resendJob?.cancel()
                    // Token is now saved → register the FCM/device token with the server (best-effort).
                    updateDeviceToken()
                    routeAfterOtp(result.data)
                }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    /**
     * Post-OTP routing (matches ride-android):
     * - name not set yet → continue onboarding at the user-type step.
     * - userType 1 → rider home.
     * - userType 2 (has a driver profile) → the *current* status comes from `GET /captains`:
     *   `driverModeSwitch` true → captain dashboard (driver), false → rider home.
     */
    private suspend fun routeAfterOtp(session: AuthSession) {
        if (!session.isNameUpdated) {
            _state.update { it.copy(isLoading = false, step = OnboardingStep.UserType) }
            return
        }
        if (session.userType == USER_TYPE_CAPTAIN) {
            val inDriverMode = (getCaptainDetails() as? ApiResult.Success)?.data?.driverModeSwitch == true
            _state.update { it.copy(isLoading = false, navigateToDashboard = inDriverMode, navigateToRiderHome = !inDriverMode) }
        } else {
            _state.update { it.copy(isLoading = false, navigateToRiderHome = true) }
        }
    }

    private fun resend() {
        if (_state.value.resendSeconds > 0) return
        submitMobile()
    }

    private fun continueFromUserType() {
        when (_state.value.userType) {
            UserType.Rider -> goTo(OnboardingStep.Name)
            // "Select Your Preference" (ride type + referral) is skipped for drivers — go straight to the
            // car-option step. CaptainRideType is now unreachable (kept for a possible future re-enable).
            UserType.Captain -> goTo(OnboardingStep.CaptainCarOption)
        }
    }

    private fun submitCaptainDetails() {
        val current = _state.value
        if (!current.canSubmitCaptainDetails) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Register the verified guest as a driver (NID + license expiry) before the car steps,
            // exactly like ride-android's EnterDriverDetails → guest-to-driver call.
            val result = guestToDriver(
                mobileNo = current.fullMobile(),
                nationalId = current.nidNumber,
                licenseExpiry = current.licenseExpiry,
                referralCode = current.referralCode,
            )
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isLoading = false) }
                    when (current.carOption) {
                        // "I own a car": continue to register the car by sequence number.
                        CarOption.OwnCar -> goTo(OnboardingStep.CarSequence)
                        // "Drive-A-Derrbi": no car to register — submit the application for review.
                        CarOption.DriveADarrbi -> submitApplication()
                    }
                }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun submitName() {
        val current = _state.value
        if (!current.canSubmitName) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = setRiderName(current.fullMobile(), current.name, current.referralCode)) {
                // New rider finished onboarding → go straight to the rider home (booking) flow.
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, navigateToRiderHome = true) }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun submitApplication() {
        val current = _state.value
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val application = CaptainApplication(
            nationalId = current.nidNumber,
            carSequenceNo = current.foundCar?.sequenceNo ?: current.carSequenceNo,
            carPlateNo = current.foundCar?.carPlateNo.orEmpty(),
            carLicenceType = current.foundCar?.plateTypeCode ?: 1,
        )
        viewModelScope.launch {
            when (val result = submitCaptainApplication(application)) {
                // Approved submission routes to the captain dashboard (map + under-review card).
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        applicationState = ApplicationState.UnderReview,
                        navigateToDashboard = true,
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(
                        isLoading = false,
                        applicationState = ApplicationState.Rejected,
                        step = OnboardingStep.ApplicationStatus,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun submitCarSequence() {
        val current = _state.value
        if (!current.canSubmitCarSequence) return
        // `userid` is the owner's National ID: the entered owner ID when authorized (checkbox on),
        // otherwise the captain's own NID when they own the car (matches ride-android).
        val ownerNationalId = if (current.notOwnerAuthorized) current.carOwnerId else current.nidNumber
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getCarBySequence(current.carSequenceNo, ownerNationalId)) {
                is ApiResult.Success ->
                    _state.update { it.copy(isLoading = false, foundCar = result.data, step = OnboardingStep.CarDetails) }
                // No car registered for this sequence/NID — show the empty "no cars" state.
                is ApiResult.Error ->
                    _state.update { it.copy(isLoading = false, foundCar = null, step = OnboardingStep.CarDetails) }
                is ApiResult.Failure ->
                    _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun back() {
        _state.update { state ->
            val previous = when (state.step) {
                OnboardingStep.Language -> OnboardingStep.Language
                OnboardingStep.Mobile -> OnboardingStep.Language
                OnboardingStep.Otp -> OnboardingStep.Mobile
                OnboardingStep.UserType -> OnboardingStep.Otp
                OnboardingStep.Name -> OnboardingStep.UserType
                OnboardingStep.CaptainRideType -> OnboardingStep.UserType
                // CaptainRideType is skipped, so the car-option step steps back to the user-type screen.
                OnboardingStep.CaptainCarOption -> OnboardingStep.UserType
                OnboardingStep.CaptainDetails -> OnboardingStep.CaptainCarOption
                OnboardingStep.CarSequence -> OnboardingStep.CaptainDetails
                OnboardingStep.CarDetails -> OnboardingStep.CarSequence
                OnboardingStep.ApplicationStatus ->
                    if (state.carOption == CarOption.OwnCar) OnboardingStep.CarDetails else OnboardingStep.CaptainDetails
            }
            state.copy(step = previous, errorMessage = null)
        }
    }

    private fun goTo(step: OnboardingStep) = _state.update { it.copy(step = step, errorMessage = null) }

    private fun startResendTimer() {
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            var seconds = RESEND_SECONDS
            _state.update { it.copy(resendSeconds = seconds) }
            while (seconds > 0) {
                delay(1000)
                seconds -= 1
                _state.update { it.copy(resendSeconds = seconds) }
            }
        }
    }

    private fun OnboardingUiState.fullMobile(): String = countryCode.removePrefix("+") + mobile

    companion object {
        private const val RESEND_SECONDS = 30
        private const val USER_TYPE_CAPTAIN = 2
    }
}
