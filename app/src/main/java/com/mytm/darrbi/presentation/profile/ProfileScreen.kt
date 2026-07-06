package com.mytm.darrbi.presentation.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/** One row in the profile menu list. */
private data class ProfileMenuItem(val iconRes: Int, val labelRes: Int, val onClick: () -> Unit)

/**
 * Profile screen reached from the dashboard avatar. Mirrors ride-android's ProfileActivity: header
 * (back + notifications), profile/balance/IBAN cards, the action menu, and the version footer.
 *
 * Dynamic values are sample placeholders until the profile API (`GET` user) is wired.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    /** When non-null (rider profile), shows a "Drive with Darrbi" entry that switches to captain mode. */
    onSwitchToCaptain: (() -> Unit)? = null,
    /** When non-null (captain profile), shows a "Switch to Rider" entry that switches to rider mode. */
    onSwitchToRider: (() -> Unit)? = null,
    onLogout: () -> Unit = onBack,
    onViewBalanceDetails: () -> Unit = {},
    onMyRides: () -> Unit = {},
    onMyReports: () -> Unit = {},
    onTerms: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onAppSettings: () -> Unit = {},
    onCustomerCare: () -> Unit = {},
    onNotifications: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    var showLogout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Non-null = show the "coming soon" sheet for that feature's label (Car Deal Details / Saved Cards).
    var comingSoonLabel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    var showAddBalance by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showUpdateIban by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var ehsanDonation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val inviteMessage = stringResource(R.string.invite_share_message)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Name / DOB / referral come from the user model captured at verify-OTP.
    val user = state.user
    val userName = user?.name?.takeIf { it.isNotBlank() } ?: "—"
    val dateOfBirth = user?.dateOfBirth?.takeIf { it.isNotBlank() } ?: ""
    val referralCode = user?.referralCode?.takeIf { it.isNotBlank() } ?: "—"
    // Actual wallet balance from get-balance; "—" until it loads.
    val balanceText = state.balance?.let { b ->
        if (b % 1.0 == 0.0) b.toLong().toString() else b.toString()
    } ?: "—"
    // RIDER/CAPTAIN now lives in the segmented toggle below the wallet (per the reference), not the menu.
    val menu = buildList {
        add(ProfileMenuItem(R.drawable.icon_car_details, R.string.menu_car_deal_details) { comingSoonLabel = R.string.menu_car_deal_details })
        add(ProfileMenuItem(R.drawable.icon_saved_cards, R.string.menu_saved_cards) { comingSoonLabel = R.string.menu_saved_cards })
        add(ProfileMenuItem(R.drawable.icon_my_rides, R.string.menu_my_rides, onMyRides))
        add(ProfileMenuItem(R.drawable.icon_app_settings, R.string.menu_app_settings, onAppSettings))
        add(ProfileMenuItem(R.drawable.icon_customer_care, R.string.menu_customer_care, onCustomerCare))
        add(ProfileMenuItem(R.drawable.icon_terms_conditions, R.string.menu_terms_conditions, onTerms))
        add(ProfileMenuItem(R.drawable.icon_privacy_policy, R.string.menu_privacy_policy, onPrivacy))
        add(ProfileMenuItem(R.drawable.icon_share_friends, R.string.menu_invite_friends) { shareInvite(context, inviteMessage) })
        add(ProfileMenuItem(R.drawable.icon_logout, R.string.menu_log_out) { showLogout = true })
    }

    if (showLogout) {
        LogoutConfirmSheet(
            onDismiss = { showLogout = false },
            onConfirm = { showLogout = false; onLogout() },
        )
    }
    comingSoonLabel?.let { labelRes ->
        ComingSoonSheet(featureLabelRes = labelRes, onDismiss = { comingSoonLabel = null })
    }
    if (showAddBalance) {
        com.mytm.darrbi.presentation.topup.AddBalanceSheet(
            onDismiss = { showAddBalance = false },
            onTopUpSuccess = { viewModel.refreshBalance() },
        )
    }
    if (showUpdateIban) {
        UpdateIbanSheet(
            onDismiss = { showUpdateIban = false },
            onUpdated = { showUpdateIban = false; viewModel.refreshIban() },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surfaceVariant) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onBack = onBack, onNotifications = onNotifications)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileHeaderCard(userName, dateOfBirth, user?.rating, referralCode)
                WalletCard(balanceText) { showAddBalance = true }
                EhsanDonationRow(checked = ehsanDonation, onCheckedChange = { ehsanDonation = it })
                // RIDER/CAPTAIN segmented toggle — captain profile exposes onSwitchToRider; rider, onSwitchToCaptain.
                if (onSwitchToRider != null || onSwitchToCaptain != null) {
                    ModeToggle(
                        isCaptain = onSwitchToRider != null,
                        onRider = { onSwitchToRider?.invoke() },
                        onCaptain = { onSwitchToCaptain?.invoke() },
                    )
                }
                // IBAN card appears only when the user has a saved IBAN (from get-iban).
                state.iban?.takeIf { it.isNotBlank() }?.let { iban ->
                    IbanCard(iban, state.bank.orEmpty()) { showUpdateIban = true }
                }
                MenuCard(menu)
                VersionFooter()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProfileTopBar(onBack: () -> Unit, onNotifications: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarrbiTheme.colors.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_back),
                tint = DarrbiTheme.colors.onSurface,
            )
        }
        Spacer(Modifier.weight(1f))
        CircleIconButton(onClick = onNotifications) {
            Box(contentAlignment = Alignment.TopEnd) {
                Image(
                    painter = painterResource(R.drawable.icon_notification),
                    contentDescription = stringResource(R.string.cd_notifications),
                    modifier = Modifier.size(22.dp),
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DarrbiTheme.colors.error),
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(DarrbiTheme.colors.surface)
            .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun ProfileCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ProfileHeaderCard(userName: String, dateOfBirth: String, rating: Double?, referralCode: String) {
    ProfileCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp)) {
                Image(
                    painter = painterResource(R.drawable.user_placeholder),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Image(
                    painter = painterResource(R.drawable.icon_edit),
                    contentDescription = stringResource(R.string.cd_edit_profile),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = userName,
                    style = DarrbiTheme.typography.title,
                    color = DarrbiTheme.colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dateOfBirth.isNotBlank()) {
                        Image(
                            painter = painterResource(R.drawable.icon_dob),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = dateOfBirth,
                            style = DarrbiTheme.typography.label,
                            color = DarrbiTheme.colors.onSurfaceVariant,
                        )
                    }
                    if (rating != null) {
                        if (dateOfBirth.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            Box(modifier = Modifier.width(1.dp).height(14.dp).background(DarrbiTheme.colors.outline))
                            Spacer(Modifier.width(10.dp))
                        }
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = DarrbiTheme.colors.warning,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.profile_rating, formatRating(rating)),
                            style = DarrbiTheme.typography.label,
                            color = DarrbiTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = DarrbiTheme.colors.outline)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.profile_referral_code),
                style = DarrbiTheme.typography.body,
                color = DarrbiTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = referralCode,
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            val clipboard = LocalClipboardManager.current
            Image(
                painter = painterResource(R.drawable.icon_copy),
                contentDescription = stringResource(R.string.cd_copy),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { clipboard.setText(AnnotatedString(referralCode)) },
            )
        }
    }
}

/** Green "Derrbi Wallet" card: brand mark + name on the left, Balance/SAR on the right; tap to top up. */
@Composable
private fun WalletCard(balance: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.primary.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.icon_darrbi),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.profile_wallet_name),
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.profile_balance),
                    style = DarrbiTheme.typography.label,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                )
                Text(
                    text = "${stringResource(R.string.profile_currency_sar)} $balance",
                    style = DarrbiTheme.typography.title,
                    color = DarrbiTheme.colors.onSurface,
                )
            }
        }
    }
}

/** "Ehsan Donation" round-up toggle row (standalone over the grey background, per the reference). */
@Composable
private fun EhsanDonationRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.profile_ehsan_donation),
            style = DarrbiTheme.typography.bodyMedium,
            color = DarrbiTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarrbiTheme.colors.onPrimary,
                checkedTrackColor = DarrbiTheme.colors.primary,
                checkedBorderColor = DarrbiTheme.colors.primary,
                uncheckedThumbColor = DarrbiTheme.colors.surface,
                uncheckedTrackColor = DarrbiTheme.colors.outline,
                uncheckedBorderColor = DarrbiTheme.colors.outline,
            ),
        )
    }
}

/**
 * RIDER / CAPTAIN segmented toggle (per the reference): a grey rounded track with the active mode shown as
 * a raised bright-green pill (shadow), dark label on green, light label on the grey side.
 */
@Composable
private fun ModeToggle(isCaptain: Boolean, onRider: () -> Unit, onCaptain: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp)
            // Background carries the rounded shape WITHOUT clipping, so the active pill's shadow shows.
            .background(DarrbiTheme.colors.modeTrack, RoundedCornerShape(10.dp))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeSegment(stringResource(R.string.mode_rider), active = !isCaptain, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onRider)
        ModeSegment(stringResource(R.string.mode_captain), active = isCaptain, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onCaptain)
    }
}

@Composable
private fun ModeSegment(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (active) {
        Surface(
            modifier = modifier.clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = DarrbiTheme.colors.modeAccent,
            shadowElevation = 4.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = label, style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onModeAccent)
            }
        }
    } else {
        Box(
            modifier = modifier.clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.onModeTrack)
        }
    }
}

/** Formats a rating to one decimal place (e.g. 4.2). */
private fun formatRating(rating: Double): String = String.format(java.util.Locale.US, "%.1f", rating)

/** Opens the Android share sheet to invite friends (ride-android's invite action). */
private fun shareInvite(context: android.content.Context, message: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, message)
    }
    context.startActivity(android.content.Intent.createChooser(intent, null))
}

@Composable
private fun IbanCard(iban: String, bankName: String, onUpdateIban: () -> Unit = {}) {
    ProfileCard {
        Row {
            Text(
                text = "${stringResource(R.string.profile_iban)} : ",
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurfaceVariant,
            )
            Text(
                text = iban,
                style = DarrbiTheme.typography.bodyMedium,
                color = DarrbiTheme.colors.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = DarrbiTheme.colors.surfaceVariant,
        ) {
            Text(
                text = "${stringResource(R.string.profile_bank)} : $bankName",
                style = DarrbiTheme.typography.label,
                color = DarrbiTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        TonalGreenButton(text = stringResource(R.string.profile_update_iban), onClick = onUpdateIban)
    }
}

@Composable
private fun MenuCard(items: List<ProfileMenuItem>) {
    ProfileCard {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(item.labelRes),
                    style = DarrbiTheme.typography.bodyMedium,
                    color = DarrbiTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = DarrbiTheme.colors.onSurfaceVariant,
                )
            }
            if (index < items.lastIndex) HorizontalDivider(color = DarrbiTheme.colors.outline)
        }
    }
}

@Composable
private fun VersionFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.splashBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.darrbi_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.28f),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.profile_version),
                style = DarrbiTheme.typography.caption,
                color = DarrbiTheme.colors.onPrimary,
            )
        }
    }
}

/** Light-green tonal button (Topup / Update IBAN) — derived from the primary token, no raw color. */
@Composable
private fun TonalGreenButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = DarrbiTheme.colors.primary.copy(alpha = 0.12f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = DarrbiTheme.typography.button, color = DarrbiTheme.colors.primary)
        }
    }
}
