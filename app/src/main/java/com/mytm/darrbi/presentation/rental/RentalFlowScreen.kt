package com.mytm.darrbi.presentation.rental

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/**
 * Self-drive car-rental (RAC) renter flow — a self-contained REST step machine ([RentalStep]) over the
 * `/v2/rac/renter/...` endpoints (see `RAC_MOBILE_INTEGRATION_GUIDE.md`). No socket, no map dependency.
 *
 * @param onBack exit the flow back to the rider home.
 */
@Composable
fun RentalFlowScreen(
    onBack: () -> Unit,
    viewModel: RentalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val errorText = rentalMessageText(state.errorMessage)
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            android.widget.Toast.makeText(context, errorText, android.widget.Toast.LENGTH_LONG).show()
            viewModel.onEvent(RentalEvent.ConsumeError)
        }
    }
    val infoText = rentalMessageText(state.infoMessage)
    LaunchedEffect(state.infoMessage) {
        if (state.infoMessage != null) {
            android.widget.Toast.makeText(context, infoText, android.widget.Toast.LENGTH_LONG).show()
            viewModel.onEvent(RentalEvent.ConsumeInfo)
        }
    }
    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) {
            viewModel.onEvent(RentalEvent.ConsumeExit)
            onBack()
        }
    }

    BackHandler { viewModel.onEvent(RentalEvent.Back) }

    Box(modifier = Modifier.fillMaxSize().background(DarrbiTheme.colors.surface)) {
        when (state.step) {
            RentalStep.Search -> RentalSearchHome(state = state, onEvent = viewModel::onEvent)

            RentalStep.Results -> Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                RentalHeader(title = stringResource(R.string.rental_title), onBack = { viewModel.onEvent(RentalEvent.Back) })
                RentalResultsContent(state = state, onEvent = viewModel::onEvent, modifier = Modifier.weight(1f))
            }

            RentalStep.Detail -> Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                RentalHeader(title = stringResource(R.string.rental_title), onBack = { viewModel.onEvent(RentalEvent.Back) })
                val vehicle = state.vehicle
                if (vehicle != null) {
                    RentalDetailContent(vehicle = vehicle, onContinue = { viewModel.onEvent(RentalEvent.Continue) }, modifier = Modifier.weight(1f))
                }
            }

            RentalStep.Reserve -> Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                RentalHeader(title = stringResource(R.string.rental_reservation_title), onBack = { viewModel.onEvent(RentalEvent.Back) })
                RentalReserveContent(state = state, onEvent = viewModel::onEvent, modifier = Modifier.weight(1f))
            }

            RentalStep.Confirmation -> {
                // The bookings hub renders behind the success dialog.
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    RentalHeader(title = stringResource(R.string.rental_bookings_title), onBack = { viewModel.onEvent(RentalEvent.Back) })
                }
                ReservationSuccessDialog(
                    bookingRef = state.createdBooking?.bookingRef,
                    onDone = { viewModel.onEvent(RentalEvent.Done) },
                )
            }

            RentalStep.Bookings -> Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                RentalHeader(title = stringResource(R.string.rental_bookings_title), onBack = { viewModel.onEvent(RentalEvent.Back) })
                RentalBookingsContent(
                    state = state,
                    onOpen = { viewModel.onEvent(RentalEvent.OpenBooking(it)) },
                    onCreateNew = { viewModel.onEvent(RentalEvent.CreateNew) },
                    modifier = Modifier.weight(1f),
                )
            }

            RentalStep.BookingDetail -> Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                RentalHeader(title = stringResource(R.string.rental_detail_title), onBack = { viewModel.onEvent(RentalEvent.Back) })
                val detail = state.bookingDetail
                if (detail != null) {
                    RentalBookingDetailContent(
                        detail = detail,
                        isActionInFlight = state.isActionInFlight,
                        onCancel = { viewModel.onEvent(RentalEvent.CancelBooking) },
                        onExtend = { viewModel.onEvent(RentalEvent.OpenExtend) },
                        onRate = { viewModel.onEvent(RentalEvent.OpenRate) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---- Overlays ----
        if (state.showDateSheet) {
            Scrim(onDismiss = { viewModel.onEvent(RentalEvent.DismissDateSheet) })
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                RentalDateRangeSheet(
                    initialPickup = state.pickupAtMillis,
                    initialReturn = state.returnAtMillis,
                    onConfirm = { p, r -> viewModel.onEvent(RentalEvent.SetDates(p, r)) },
                )
            }
        }
        if (state.showDobSheet) {
            Scrim(onDismiss = { viewModel.onEvent(RentalEvent.DismissDobSheet) })
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                RentalDobSheet(
                    initialMillis = state.renterDobMillis,
                    onConfirm = { viewModel.onEvent(RentalEvent.SetDob(it)) },
                )
            }
        }
        if (state.showFilterSheet) {
            Scrim(onDismiss = { viewModel.onEvent(RentalEvent.DismissSheets) })
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                RentalFilterSheet(state = state, onApply = { c, s, t -> viewModel.onEvent(RentalEvent.ApplyFilter(c, s, t)) })
            }
        }
        if (state.showSortSheet) {
            Scrim(onDismiss = { viewModel.onEvent(RentalEvent.DismissSheets) })
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                RentalSortSheet(current = state.sortBy, onSelect = { viewModel.onEvent(RentalEvent.SetSort(it)) })
            }
        }
        if (state.showExtendSheet) {
            Scrim(onDismiss = { viewModel.onEvent(RentalEvent.DismissBookingSheets) })
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                RentalExtendSheet(onConfirm = { viewModel.onEvent(RentalEvent.RequestExtend(it)) })
            }
        }
        if (state.showRateSheet) {
            Scrim(onDismiss = { viewModel.onEvent(RentalEvent.DismissBookingSheets) })
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                RentalRateSheet(onSubmit = { rating, text -> viewModel.onEvent(RentalEvent.SubmitRate(rating, text)) })
            }
        }

        // Blocking spinners. (Vehicle search loading is shown as a shimmer skeleton in the results list,
        // not a blocking spinner — see RentalResultsContent.)
        val blocking = state.isLoadingDetail || state.isBooking || state.isVerifyingNafath ||
            state.isLoadingBookingDetail && state.bookingDetail == null ||
            state.isLoadingBookings && state.bookings.isEmpty() ||
            state.isActionInFlight
        if (blocking) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DarrbiTheme.colors.primary)
            }
        }
    }
}

/** Dimmed, tap-to-dismiss backdrop behind a bottom sheet. */
@Composable
private fun Scrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
}
