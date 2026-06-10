package com.mytm.darrbi.presentation.topup

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField
import com.mytm.darrbi.domain.model.PaymentSource
import com.mytm.darrbi.domain.model.TopupStatus
import com.mytm.darrbi.domain.model.TopupTransaction
import java.text.SimpleDateFormat
import java.util.Locale

/** Topup Details: list of top-up transactions with a refund flow (mirrors ride-android's TopupDetails). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopupDetailsScreen(
    onBack: () -> Unit,
    viewModel: TopupDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surfaceVariant) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.topup_details_title),
                    style = DarrbiTheme.typography.titleLarge,
                    color = DarrbiTheme.colors.onSurface,
                )
                state.transactions.forEach { txn ->
                    TransactionCard(txn) { viewModel.onEvent(TopupDetailsEvent.OpenRefund(txn)) }
                }
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = DarrbiTheme.colors.primary,
                        modifier = Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (state.refundTarget != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(TopupDetailsEvent.DismissRefund) },
            sheetState = sheetState,
            containerColor = DarrbiTheme.colors.surface,
        ) {
            if (state.showConfirm) {
                RefundConfirmSheet(state, viewModel::onEvent)
            } else {
                RefundAmountSheet(state, viewModel::onEvent)
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarrbiTheme.colors.surfaceVariant)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DarrbiTheme.colors.surface)
                .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_back),
                tint = DarrbiTheme.colors.onSurface,
            )
        }
    }
}

@Composable
private fun TransactionCard(txn: TopupTransaction, onRefund: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarrbiTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarrbiTheme.colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.icon_saved_cards),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = paymentLabel(txn),
                        style = DarrbiTheme.typography.bodyMedium,
                        color = DarrbiTheme.colors.onSurface,
                    )
                    txn.cardLast4?.let {
                        Text(
                            text = stringResource(R.string.topup_ending_format, it),
                            style = DarrbiTheme.typography.caption,
                            color = DarrbiTheme.colors.onSurfaceVariant,
                        )
                    }
                }
                if (txn.isRefundable) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarrbiTheme.colors.primary.copy(alpha = 0.12f),
                        modifier = Modifier.clickable(onClick = onRefund),
                    ) {
                        Text(
                            text = stringResource(R.string.topup_refund),
                            style = DarrbiTheme.typography.label,
                            color = DarrbiTheme.colors.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarrbiTheme.colors.outline)
            DetailRow(stringResource(R.string.topup_amount), amountText(txn.amount))
            DetailRow(stringResource(R.string.topup_time), formatTime(txn.timestampIso))
            DetailRow(stringResource(R.string.topup_date), formatDate(txn.timestampIso))
            StatusRow(txn.status)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}

@Composable
private fun StatusRow(status: TopupStatus) {
    val (labelRes, color) = when (status) {
        TopupStatus.Successful -> R.string.topup_status_successful to DarrbiTheme.colors.primary
        TopupStatus.Refunded -> R.string.topup_status_refunded to DarrbiTheme.colors.onSurfaceVariant
        TopupStatus.Failed -> R.string.topup_status_failed to DarrbiTheme.colors.error
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.topup_status), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(stringResource(labelRes), style = DarrbiTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun RefundAmountSheet(state: TopupDetailsUiState, onEvent: (TopupDetailsEvent) -> Unit) {
    val sar = stringResource(R.string.profile_currency_sar)
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
        Text(
            text = stringResource(R.string.refund_title),
            style = DarrbiTheme.typography.title,
            color = DarrbiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            LabeledAmount(stringResource(R.string.refund_min), "$sar ${amountText(TopupDetailsUiState.REFUND_MIN)}", Modifier.weight(1f))
            LabeledAmount(stringResource(R.string.refund_max), "$sar ${amountText(state.refundMax)}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        DarrbiTextField(
            value = state.refundAmount,
            onValueChange = { onEvent(TopupDetailsEvent.RefundAmountChanged(it)) },
            label = stringResource(R.string.refund_amount_label),
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(Modifier.height(20.dp))
        DarrbiPrimaryButton(
            text = stringResource(R.string.topup_refund),
            onClick = { onEvent(TopupDetailsEvent.ProceedToConfirm) },
            enabled = state.canRefund,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RefundConfirmSheet(state: TopupDetailsUiState, onEvent: (TopupDetailsEvent) -> Unit) {
    val sar = stringResource(R.string.profile_currency_sar)
    val amount = "$sar ${amountText(state.refundAmount.toDoubleOrNull() ?: 0.0)}"
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.refund_confirm_title),
            style = DarrbiTheme.typography.title,
            color = DarrbiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.refund_confirm_info, amount),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        DarrbiPrimaryButton(
            text = stringResource(R.string.common_confirm),
            onClick = { onEvent(TopupDetailsEvent.ConfirmRefund) },
            enabled = !state.isRefunding,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LabeledAmount(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
        Text(value, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}

@Composable
private fun paymentLabel(txn: TopupTransaction): String = when (txn.source) {
    PaymentSource.Wallet -> stringResource(R.string.payment_wallet)
    PaymentSource.Card -> txn.cardBrand ?: stringResource(R.string.payment_card)
    PaymentSource.Tabby -> stringResource(R.string.payment_tabby)
    PaymentSource.Tamara -> stringResource(R.string.payment_tamara)
    PaymentSource.Promotion -> stringResource(R.string.payment_promotion)
    PaymentSource.Unknown -> stringResource(R.string.payment_card)
}

/** "100.0" → "100"; keeps decimals otherwise. */
private fun amountText(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()

private fun formatDate(iso: String?): String = formatTimestamp(iso, "dd MMMM yyyy")
private fun formatTime(iso: String?): String = formatTimestamp(iso, "hh:mm a")

private fun formatTimestamp(iso: String?, pattern: String): String {
    if (iso.isNullOrBlank()) return ""
    val inputs = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm")
    for (p in inputs) {
        runCatching {
            val date = SimpleDateFormat(p, Locale.ENGLISH).parse(iso)
            if (date != null) return SimpleDateFormat(pattern, Locale.ENGLISH).format(date)
        }
    }
    return ""
}
