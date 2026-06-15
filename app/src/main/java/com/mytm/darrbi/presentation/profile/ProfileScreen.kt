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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
    var showAddBalance by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showUpdateIban by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
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
    val menu = listOf(
        ProfileMenuItem(R.drawable.icon_subscription, R.string.menu_subscription) {},
        ProfileMenuItem(R.drawable.icon_car_details, R.string.menu_car_deal_details) {},
        ProfileMenuItem(R.drawable.icon_saved_cards, R.string.menu_saved_cards) {},
        ProfileMenuItem(R.drawable.icon_my_rides, R.string.menu_my_rides, onMyRides),
        ProfileMenuItem(R.drawable.iv_report, R.string.menu_my_reports, onMyReports),
        ProfileMenuItem(R.drawable.icon_app_settings, R.string.menu_app_settings, onAppSettings),
        ProfileMenuItem(R.drawable.icon_customer_care, R.string.menu_customer_care, onCustomerCare),
        ProfileMenuItem(R.drawable.icon_terms_conditions, R.string.menu_terms_conditions, onTerms),
        ProfileMenuItem(R.drawable.icon_privacy_policy, R.string.menu_privacy_policy, onPrivacy),
        ProfileMenuItem(R.drawable.icon_share_friends, R.string.menu_invite_friends) { shareInvite(context, inviteMessage) },
        ProfileMenuItem(R.drawable.icon_logout, R.string.menu_log_out) { showLogout = true },
    )

    if (showLogout) {
        LogoutConfirmSheet(
            onDismiss = { showLogout = false },
            onConfirm = { showLogout = false; onLogout() },
        )
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
                ProfileHeaderCard(userName, dateOfBirth, referralCode)
                BalanceCard(balanceText, onViewBalanceDetails) { showAddBalance = true }
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
private fun ProfileHeaderCard(userName: String, dateOfBirth: String, referralCode: String) {
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
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.profile_status_good),
                        style = DarrbiTheme.typography.label,
                        color = DarrbiTheme.colors.onSurfaceVariant,
                    )
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

@Composable
private fun BalanceCard(balance: String, onViewDetails: () -> Unit, onTopup: () -> Unit = {}) {
    ProfileCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
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
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.profile_view_details),
                style = DarrbiTheme.typography.button,
                color = DarrbiTheme.colors.primary,
                modifier = Modifier.clickable(onClick = onViewDetails),
            )
        }
        Spacer(Modifier.height(14.dp))
        TonalGreenButton(text = stringResource(R.string.profile_topup), onClick = onTopup)
    }
}

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
