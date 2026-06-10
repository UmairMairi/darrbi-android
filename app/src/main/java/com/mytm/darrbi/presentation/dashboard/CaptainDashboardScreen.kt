package com.mytm.darrbi.presentation.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiCard
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField

/**
 * Captain dashboard: a full-screen map with a bottom card whose content is driven by `GET /captains`
 * (mirrors ride-android's MyDashboard.verifyWSLSubStatus):
 * under review → approved (Start Now) → enter IBAN → no riders around.
 */
@Composable
fun CaptainDashboardScreen(
    onSeeDetails: () -> Unit,
    onProfile: () -> Unit = {},
    viewModel: CaptainDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Default camera over Riyadh until live location wiring is added.
    val riyadh = LatLng(24.7136, 46.6753)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(riyadh, 14f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false,
            ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp)
                .clip(CircleShape)
                .background(DarrbiTheme.colors.primary)
                .border(3.dp, DarrbiTheme.colors.onPrimary, CircleShape),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(DarrbiTheme.colors.surface)
                .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                .clickable(onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = stringResource(R.string.cd_profile),
                tint = DarrbiTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }

        when (state.stage) {
            CaptainStage.Loading -> Unit
            CaptainStage.UnderReview -> BottomCard { UnderReviewContent(onSeeDetails) }
            CaptainStage.Approved -> BottomCard { ApprovedContent { viewModel.onEvent(CaptainDashboardEvent.StartNow) } }
            CaptainStage.EnterIban -> BottomCard { EnterIbanContent(state, viewModel::onEvent) }
            CaptainStage.NoRiders -> BottomCard { NoRidersContent() }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BottomCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    DarrbiCard(modifier = Modifier.align(Alignment.BottomCenter), content = content)
}

@Composable
private fun UnderReviewContent(onSeeDetails: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CarBadge()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.dashboard_app_under_process),
            style = DarrbiTheme.typography.title,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.dashboard_absher_subtitle),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        DarrbiSecondaryButton(text = stringResource(R.string.common_see_details), onClick = onSeeDetails)
    }
}

@Composable
private fun ApprovedContent(onStartNow: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.image_car),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.45f),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.dashboard_approved_title),
            style = DarrbiTheme.typography.title,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        DarrbiPrimaryButton(text = stringResource(R.string.common_start_now), onClick = onStartNow)
    }
}

@Composable
private fun EnterIbanContent(state: CaptainDashboardUiState, onEvent: (CaptainDashboardEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.iban_enter_title),
            style = DarrbiTheme.typography.title,
            color = DarrbiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.iban_enter_subtitle),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        DarrbiTextField(
            value = state.ibanInput,
            onValueChange = { onEvent(CaptainDashboardEvent.IbanChanged(it)) },
            label = stringResource(R.string.iban_label),
            keyboardType = KeyboardType.Text,
            isError = state.ibanError,
            visualTransformation = IbanVisualTransformation,
            trailingContent = {
                when {
                    state.ibanVerifying -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = DarrbiTheme.colors.primary,
                    )
                    state.ibanError -> Icon(Icons.Filled.Close, null, tint = DarrbiTheme.colors.error)
                    state.bankName != null -> Icon(Icons.Filled.Check, null, tint = DarrbiTheme.colors.primary)
                }
            },
        )
        if (state.ibanError) {
            Spacer(Modifier.height(8.dp))
            InfoPill(text = stringResource(R.string.iban_incorrect), error = true)
        } else if (state.bankName != null) {
            Spacer(Modifier.height(8.dp))
            InfoPill(text = stringResource(R.string.iban_bank_format, state.bankName), error = false)
        }
        Spacer(Modifier.height(20.dp))
        DarrbiPrimaryButton(
            text = stringResource(R.string.common_done),
            onClick = { onEvent(CaptainDashboardEvent.SubmitIban) },
            enabled = state.canSubmitIban,
        )
    }
}

@Composable
private fun NoRidersContent() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.no_riders_title),
            style = DarrbiTheme.typography.titleLarge,
            color = DarrbiTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_riders_subtitle),
            style = DarrbiTheme.typography.body,
            color = DarrbiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        DarrbiPrimaryButton(text = stringResource(R.string.no_riders_navigate), onClick = {})
    }
}

/** Light tonal pill used for the IBAN bank (green) / error (red) message under the field. */
@Composable
private fun InfoPill(text: String, error: Boolean) {
    val accent = if (error) DarrbiTheme.colors.error else DarrbiTheme.colors.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            style = DarrbiTheme.typography.label,
            color = accent,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Car illustration with the small orange "under review" clock badge. */
@Composable
private fun CarBadge() {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.image_car),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.45f),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(34.dp)
                .clip(CircleShape)
                .background(DarrbiTheme.colors.warning),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = DarrbiTheme.colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Groups the IBAN into blocks of 4 for display ("SA44 2000 0002 …") while keeping the raw value. */
private val IbanVisualTransformation = VisualTransformation { text ->
    val grouped = text.text.chunked(4).joinToString(" ")
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            offset + (offset - 1).coerceAtLeast(0) / 4
        override fun transformedToOriginal(offset: Int): Int =
            offset - offset / 5
    }
    TransformedText(AnnotatedString(grouped), mapping)
}
