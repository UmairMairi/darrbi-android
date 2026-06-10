package com.mytm.darrbi.presentation.onboarding

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiCheckbox
import com.mytm.darrbi.core.designsystem.components.DarrbiOtpInput
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSelectableOption
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSwitch
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField
import com.mytm.darrbi.core.designsystem.components.LicensePlate

@Composable
fun OnboardingScreen(
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToRiderHome: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Locale (strings + RTL + font) is applied at the app root from the persisted language; selecting a
    // language here persists it, which re-localizes the whole tree.
    LaunchedEffect(state.navigateToDashboard) {
        if (state.navigateToDashboard) onNavigateToDashboard()
    }
    LaunchedEffect(state.navigateToRiderHome) {
        if (state.navigateToRiderHome) onNavigateToRiderHome()
    }
    OnboardingContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onEvent(OnboardingEvent.ConsumeError)
        }
    }

    BackHandler(enabled = state.step != OnboardingStep.Language && !state.completed) {
        onEvent(OnboardingEvent.Back)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarrbiTheme.colors.splashBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.darrbi_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(fraction = 0.5f),
                    contentScale = ContentScale.Fit,
                )
            }

            com.mytm.darrbi.core.designsystem.components.DarrbiCard {
                // Fade between steps for smooth transitions. `null` target = the completed state.
                // Key only on the step/completed flag (not the whole state) so typing doesn't re-trigger
                // the fade; the inner Column restores vertical stacking that Crossfade's Box would drop.
                val target = if (state.completed) null else state.step
                Crossfade(
                    targetState = target,
                    animationSpec = tween(durationMillis = 250),
                    label = "onboardingStep",
                ) { step ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (step) {
                            null -> CompletedStep()
                            OnboardingStep.Language -> LanguageStep(state, onEvent)
                            OnboardingStep.Mobile -> MobileStep(state, onEvent)
                            OnboardingStep.Otp -> OtpStep(state, onEvent)
                            OnboardingStep.UserType -> UserTypeStep(state, onEvent)
                            OnboardingStep.Name -> NameStep(state, onEvent)
                            OnboardingStep.CaptainRideType -> CaptainRideTypeStep(state, onEvent)
                            OnboardingStep.CaptainCarOption -> CaptainCarOptionStep(state, onEvent)
                            OnboardingStep.CaptainDetails -> CaptainDetailsStep(state, onEvent)
                            OnboardingStep.CarSequence -> CarSequenceStep(state, onEvent)
                            OnboardingStep.CarDetails -> CarDetailsStep(state, onEvent)
                            OnboardingStep.ApplicationStatus -> ApplicationStatusStep(state, onEvent)
                        }
                    }
                }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                color = DarrbiTheme.colors.primary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun CardTitle(text: String, showBack: Boolean = false, onBack: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarrbiTheme.colors.surface)
                    .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.icon_back),
                    contentDescription = stringResource(R.string.cd_back),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { scaleX = if (isRtl) -1f else 1f },
                    colorFilter = ColorFilter.tint(DarrbiTheme.colors.onSurface),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = text,
            style = DarrbiTheme.typography.title,
            color = DarrbiTheme.colors.onSurface,
        )
    }
}

@Composable
private fun LanguageStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    CardTitle(text = stringResource(R.string.onboarding_select_language))
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = DarrbiTheme.colors.outline)
    Spacer(Modifier.height(8.dp))
    LanguageOption(
        label = stringResource(R.string.language_english),
        tag = "en",
        selected = state.language == "en",
        onEvent = onEvent,
    )
    LanguageOption(
        label = stringResource(R.string.language_arabic),
        tag = "ar",
        selected = state.language == "ar",
        onEvent = onEvent,
    )
}

@Composable
private fun LanguageOption(label: String, tag: String, selected: Boolean, onEvent: (OnboardingEvent) -> Unit) {
    DarrbiSelectableOption(
        text = label,
        selected = selected,
        onClick = {
            onEvent(OnboardingEvent.SelectLanguage(tag))
            onEvent(OnboardingEvent.ContinueFromLanguage)
        },
    )
}

@Composable
private fun MobileStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    var showCountrySheet by remember { mutableStateOf(false) }
    CardTitle(
        text = stringResource(R.string.onboarding_enter_mobile_title),
        showBack = true,
        onBack = { onEvent(OnboardingEvent.Back) },
    )
    Spacer(Modifier.height(16.dp))
    DarrbiTextField(
        value = state.mobile,
        onValueChange = { onEvent(OnboardingEvent.MobileChanged(it)) },
        label = stringResource(R.string.onboarding_mobile_label),
        keyboardType = KeyboardType.Phone,
        leadingContent = {
            CountryCodeChip(
                code = state.countryCode,
                iso = state.countryIso,
                onClick = { showCountrySheet = true },
            )
        },
    )
    Spacer(Modifier.height(16.dp))
    DarrbiCheckbox(
        checked = state.whatsappOptIn,
        onCheckedChange = { onEvent(OnboardingEvent.ToggleWhatsapp) },
        label = stringResource(R.string.onboarding_whatsapp_optin),
    )
    Spacer(Modifier.height(16.dp))
    DarrbiPrimaryButton(
        text = stringResource(R.string.common_next),
        enabled = state.canSubmitMobile,
        onClick = { onEvent(OnboardingEvent.SubmitMobile) },
    )

    if (showCountrySheet) {
        CountryPickerSheet(
            onDismiss = { showCountrySheet = false },
            onSelect = {
                onEvent(OnboardingEvent.SelectCountry(dialCode = it.dialCode, iso = it.iso, phoneLength = it.phoneLength))
                showCountrySheet = false
            },
        )
    }
}

@Composable
private fun CountryCodeChip(code: String, iso: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = countryFlag(iso), style = DarrbiTheme.typography.body)
        Spacer(Modifier.width(6.dp))
        Text(text = code, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = DarrbiTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun OtpStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    CardTitle(
        text = stringResource(R.string.onboarding_enter_otp_title),
        showBack = true,
        onBack = { onEvent(OnboardingEvent.Back) },
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.onboarding_otp_subtitle, "${state.countryCode} ${state.mobile}"),
        style = DarrbiTheme.typography.label,
        color = DarrbiTheme.colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    DarrbiOtpInput(
        otp = state.otp,
        onOtpChange = { onEvent(OnboardingEvent.OtpChanged(it)) },
    )
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.onboarding_didnt_receive_otp),
            style = DarrbiTheme.typography.label,
            color = DarrbiTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        if (state.resendSeconds > 0) {
            Text(
                text = formatTimer(state.resendSeconds),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
            )
        } else {
            Text(
                text = stringResource(R.string.onboarding_resend_otp),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.primary,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .clickable { onEvent(OnboardingEvent.ResendOtp) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    DarrbiPrimaryButton(
        text = stringResource(R.string.common_verify),
        enabled = state.canVerifyOtp,
        onClick = { onEvent(OnboardingEvent.VerifyOtp) },
    )
}

@Composable
private fun UserTypeStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    CardTitle(
        text = stringResource(R.string.onboarding_continue_as),
        showBack = true,
        onBack = { onEvent(OnboardingEvent.Back) },
    )
    Spacer(Modifier.height(12.dp))
    UserTypeOption(stringResource(R.string.onboarding_rider), UserType.Rider, state.userType == UserType.Rider, onEvent)
    UserTypeOption(stringResource(R.string.onboarding_captain), UserType.Captain, state.userType == UserType.Captain, onEvent)
}

@Composable
private fun UserTypeOption(label: String, type: UserType, selected: Boolean, onEvent: (OnboardingEvent) -> Unit) {
    DarrbiSelectableOption(
        text = label,
        selected = selected,
        onClick = {
            onEvent(OnboardingEvent.SelectUserType(type))
            onEvent(OnboardingEvent.ContinueFromUserType)
        },
    )
}

@Composable
private fun NameStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    CardTitle(
        text = stringResource(R.string.onboarding_enter_name_title),
        showBack = true,
        onBack = { onEvent(OnboardingEvent.Back) },
    )
    Spacer(Modifier.height(16.dp))
    DarrbiTextField(
        value = state.name,
        onValueChange = { onEvent(OnboardingEvent.NameChanged(it)) },
        label = stringResource(R.string.onboarding_name_label),
    )
    Spacer(Modifier.height(12.dp))
    DarrbiTextField(
        value = state.referralCode,
        onValueChange = { onEvent(OnboardingEvent.ReferralChanged(it)) },
        label = stringResource(R.string.onboarding_referral_label),
    )
    Spacer(Modifier.height(16.dp))
    DarrbiPrimaryButton(
        text = stringResource(R.string.common_next),
        enabled = state.canSubmitName,
        onClick = { onEvent(OnboardingEvent.SubmitName) },
    )
}

@Composable
private fun CaptainRideTypeStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    CardTitle(
        text = stringResource(R.string.onboarding_captain_ride_type_title),
        showBack = true,
        onBack = { onEvent(OnboardingEvent.Back) },
    )
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = DarrbiTheme.colors.outline)
    // Single-select: choosing one ride type deselects the other.
    DarrbiCheckbox(
        checked = state.rideType == RideType.Passenger,
        onCheckedChange = { onEvent(OnboardingEvent.SelectRideType(RideType.Passenger)) },
        label = stringResource(R.string.ride_type_passenger),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    )
    HorizontalDivider(color = DarrbiTheme.colors.outline)
    DarrbiCheckbox(
        checked = state.rideType == RideType.Pickup,
        onCheckedChange = { onEvent(OnboardingEvent.SelectRideType(RideType.Pickup)) },
        label = stringResource(R.string.ride_type_pickup),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    )
    HorizontalDivider(color = DarrbiTheme.colors.outline)
    Spacer(Modifier.height(16.dp))
    DarrbiTextField(
        value = state.referralCode,
        onValueChange = { onEvent(OnboardingEvent.ReferralChanged(it)) },
        label = stringResource(R.string.onboarding_referral_label),
    )
    Spacer(Modifier.height(16.dp))
    DarrbiPrimaryButton(
        text = stringResource(R.string.common_next),
        enabled = state.canContinueRideType,
        onClick = { onEvent(OnboardingEvent.ContinueFromRideType) },
    )
}

@Composable
private fun CaptainCarOptionStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    CardTitle(
        text = stringResource(R.string.onboarding_select_option_title),
        showBack = true,
        onBack = { onEvent(OnboardingEvent.Back) },
    )
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = DarrbiTheme.colors.outline)
    Spacer(Modifier.height(8.dp))
    CarOptionRow(stringResource(R.string.car_option_own), CarOption.OwnCar, state.carOption == CarOption.OwnCar, onEvent)
    CarOptionRow(stringResource(R.string.car_option_service), CarOption.DriveADarrbi, state.carOption == CarOption.DriveADarrbi, onEvent)
}

@Composable
private fun CarOptionRow(label: String, option: CarOption, selected: Boolean, onEvent: (OnboardingEvent) -> Unit) {
    DarrbiSelectableOption(
        text = label,
        selected = selected,
        onClick = {
            onEvent(OnboardingEvent.SelectCarOption(option))
            onEvent(OnboardingEvent.ContinueFromCarOption)
        },
    )
}

@Composable
private fun CaptainDetailsStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    CardTitle(
        text = stringResource(R.string.onboarding_captain_details_title),
        showBack = true,
        onBack = { onEvent(OnboardingEvent.Back) },
    )
    Spacer(Modifier.height(16.dp))
    DarrbiTextField(
        value = state.nidNumber,
        onValueChange = { onEvent(OnboardingEvent.NidChanged(it)) },
        label = stringResource(R.string.captain_nid_label),
        keyboardType = KeyboardType.Number,
        isError = state.nidError,
        supportingText = if (state.nidError) stringResource(R.string.error_invalid_nid) else null,
    )
    Spacer(Modifier.height(12.dp))
    // Read-only field; tapping opens the Hijri date picker (a transparent overlay captures the tap).
    Box {
        DarrbiTextField(
            value = state.licenseExpiry,
            onValueChange = {},
            label = stringResource(R.string.captain_license_expiry_label),
            readOnly = true,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showDatePicker = true },
        )
    }
    Spacer(Modifier.height(16.dp))
    DarrbiPrimaryButton(
        text = stringResource(R.string.common_verify),
        enabled = state.canSubmitCaptainDetails,
        onClick = { onEvent(OnboardingEvent.SubmitCaptainDetails) },
    )

    if (showDatePicker) {
        HijriDatePickerSheet(
            onDismiss = { showDatePicker = false },
            onConfirm = { formatted ->
                onEvent(OnboardingEvent.LicenseExpiryChanged(formatted))
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun CarSequenceStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    CardTitle(
        text = stringResource(R.string.onboarding_add_car_sequence_title),
        showBack = true,
        onBack = { onEvent(OnboardingEvent.Back) },
    )
    Spacer(Modifier.height(16.dp))
    DarrbiTextField(
        value = state.carSequenceNo,
        onValueChange = { onEvent(OnboardingEvent.CarSequenceChanged(it)) },
        label = stringResource(R.string.car_sequence_label),
        keyboardType = KeyboardType.Number,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DarrbiTheme.colors.inputBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.car_not_owner_toggle),
            style = DarrbiTheme.typography.bodyMedium,
            color = DarrbiTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DarrbiSwitch(
            checked = state.notOwnerAuthorized,
            onCheckedChange = { onEvent(OnboardingEvent.ToggleNotOwnerAuthorized) },
        )
    }
    if (state.notOwnerAuthorized) {
        Spacer(Modifier.height(12.dp))
        DarrbiTextField(
            value = state.carOwnerId,
            onValueChange = { onEvent(OnboardingEvent.CarOwnerIdChanged(it)) },
            label = stringResource(R.string.car_owner_id_label),
            keyboardType = KeyboardType.Number,
            isError = state.carOwnerIdError,
            supportingText = if (state.carOwnerIdError) stringResource(R.string.error_invalid_nid) else null,
        )
    }
    Spacer(Modifier.height(12.dp))
    CarHelpCard()
    Spacer(Modifier.height(16.dp))
    DarrbiPrimaryButton(
        text = stringResource(R.string.common_done),
        enabled = state.canSubmitCarSequence,
        onClick = { onEvent(OnboardingEvent.SubmitCarSequence) },
    )
}

@Composable
private fun CarHelpCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarrbiTheme.colors.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.image_car),
            contentDescription = null,
            modifier = Modifier.size(width = 72.dp, height = 44.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.car_sequence_help_title),
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
            )
            Text(
                text = stringResource(R.string.common_learn_more),
                style = DarrbiTheme.typography.label,
                color = DarrbiTheme.colors.primary,
            )
        }
    }
}

@Composable
private fun CarDetailsStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    val car = state.foundCar
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (car == null) {
            CarImageWithBadge(badgeColor = DarrbiTheme.colors.error, badge = Icons.Filled.Close)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.car_no_cars_title),
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.car_use_drive_a_derrbi),
                onClick = { onEvent(OnboardingEvent.UseDriveADerrbi) },
            )
        } else {
            Image(
                painter = painterResource(R.drawable.image_car),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.55f),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${car.model} (${car.year})",
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${car.sequenceNo}  •  ${stringResource(R.string.car_seats_format, car.seats)}",
                style = DarrbiTheme.typography.label,
                color = DarrbiTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            LicensePlate(numbers = car.plateNumbers, letters = car.plateLetters)
            Spacer(Modifier.height(20.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.common_confirm),
                onClick = { onEvent(OnboardingEvent.ConfirmCar) },
            )
        }
    }
}

@Composable
private fun ApplicationStatusStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    val rejected = state.applicationState == ApplicationState.Rejected
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CarImageWithBadge(
            badgeColor = if (rejected) DarrbiTheme.colors.error else DarrbiTheme.colors.warning,
            badge = if (rejected) Icons.Filled.Close else Icons.Filled.Refresh,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (rejected) R.string.app_status_rejected_title else R.string.app_status_under_review_title,
            ),
            style = DarrbiTheme.typography.titleLarge,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (rejected) R.string.app_status_rejected_subtitle else R.string.app_status_under_review_subtitle,
            ),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (rejected) {
            DarrbiPrimaryButton(
                text = stringResource(R.string.car_use_drive_a_derrbi),
                onClick = { onEvent(OnboardingEvent.UseDriveADerrbi) },
            )
        } else {
            DarrbiSecondaryButton(
                text = stringResource(R.string.common_see_details),
                onClick = { onEvent(OnboardingEvent.FinishApplication) },
            )
        }
    }
}

@Composable
private fun CarImageWithBadge(badgeColor: Color, badge: ImageVector) {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.image_car),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.5f),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(36.dp)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = badge,
                contentDescription = null,
                tint = DarrbiTheme.colors.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CompletedStep() {
    Text(
        text = stringResource(R.string.onboarding_complete_title),
        style = DarrbiTheme.typography.titleLarge,
        color = DarrbiTheme.colors.onSurface,
    )
    Spacer(Modifier.height(8.dp))
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return buildString {
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }
}
