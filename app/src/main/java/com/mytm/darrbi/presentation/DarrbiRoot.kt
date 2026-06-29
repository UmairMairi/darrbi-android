package com.mytm.darrbi.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mytm.darrbi.R
import com.mytm.darrbi.domain.model.Ride
import com.mytm.darrbi.presentation.chat.ChatScreen
import com.mytm.darrbi.presentation.dashboard.CaptainDashboardScreen
import com.mytm.darrbi.presentation.dashboard.CaptainScheduleScreen
import com.mytm.darrbi.presentation.dashboard.CaptainStatusDetailScreen
import com.mytm.darrbi.presentation.legal.LegalPage
import com.mytm.darrbi.presentation.legal.LegalScreen
import com.mytm.darrbi.presentation.notifications.NotificationsScreen
import com.mytm.darrbi.presentation.onboarding.OnboardingScreen
import com.mytm.darrbi.presentation.profile.ProfileScreen
import com.mytm.darrbi.presentation.reports.MyReportsScreen
import com.mytm.darrbi.presentation.rental.RentalFlowScreen
import com.mytm.darrbi.presentation.rider.RiderFlowScreen
import com.mytm.darrbi.presentation.schedule.ScheduleFlowScreen
import com.mytm.darrbi.presentation.rides.MyRidesScreen
import com.mytm.darrbi.presentation.rides.ReportProblemScreen
import com.mytm.darrbi.presentation.rides.RideDetailsScreen
import com.mytm.darrbi.presentation.settings.AppSettingsScreen
import com.mytm.darrbi.presentation.splash.SplashScreen
import com.mytm.darrbi.presentation.support.CustomerCareScreen
import com.mytm.darrbi.presentation.topup.TopupDetailsScreen
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 3000L

/** Dummy customer-care number until the support-number API is wired. */
private const val CUSTOMER_CARE_NUMBER = "+966500000000"

/** Top-level destinations after the splash. */
private enum class Route {
    Onboarding, Dashboard, CaptainSchedule, RiderHome, ScheduleHome, RentalHome, StatusDetail, Profile, TopupDetails,
    MyRides, RideDetails, ReportProblem, MyReports, Terms, Privacy,
    AppSettings, CustomerCare, Notifications, Chat,
}

/** The other party in an in-trip chat (driver for the rider; rider for the captain). */
private data class ChatPeer(val id: String, val name: String, val imageUrl: String?, val localUserIsRider: Boolean)

/**
 * App root: branded splash for 3s, then the onboarding flow. A successful captain application routes to
 * the dashboard, whose profile opens the account screens (rides, reports, settings, support, legal, …).
 */
@Composable
fun DarrbiRoot() {
    val context = LocalContext.current
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val modeSwitchViewModel: ModeSwitchViewModel = hiltViewModel()
    val modeSwitching by modeSwitchViewModel.switching.collectAsStateWithLifecycle()
    val modeSwitchError by modeSwitchViewModel.error.collectAsStateWithLifecycle()
    // Surface a failed RIDER/CAPTAIN switch (it was silent before — looked like the toggle did nothing).
    LaunchedEffect(modeSwitchError) {
        modeSwitchError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            modeSwitchViewModel.consumeError()
        }
    }
    // No NavHost here, so every screen's hiltViewModel() is scoped to this Activity's store; clearing it
    // on logout drops all feature ViewModels (and their StateFlows) so the next session starts clean.
    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    var showSplash by remember { mutableStateOf(true) }
    var route by remember { mutableStateOf(Route.Onboarding) }

    // Switches the active RIDER/CAPTAIN mode (GET /captains + conditional change-driver-mode) then routes.
    fun switchMode(toCaptain: Boolean) {
        modeSwitchViewModel.switchMode(toCaptain) { target ->
            route = when (target) {
                ModeTarget.Captain -> Route.Dashboard
                ModeTarget.Rider -> Route.RiderHome
                // Not a registered driver yet → into the become-a-captain onboarding.
                ModeTarget.RegisterCaptain -> Route.Onboarding
            }
        }
    }
    // The other party (driver for the rider, rider for the captain) being chatted with.
    var chatPeer by remember { mutableStateOf<ChatPeer?>(null) }
    var chatReturn by remember { mutableStateOf(Route.RiderHome) }
    // Ride selected from the My Rides list, passed to the details screen.
    var selectedRide by remember { mutableStateOf<Ride?>(null) }
    var selectedRideGiven by remember { mutableStateOf(false) }
    // Where "Report a Problem" should return to (it's reachable from Ride Details and Customer Care).
    var reportReturn by remember { mutableStateOf(Route.RideDetails) }
    // Where Profile should return to (captain dashboard vs rider home).
    var profileReturn by remember { mutableStateOf(Route.Dashboard) }

    LaunchedEffect(Unit) {
        // Auto-login: route from the persisted session (saved token → straight to the dashboard).
        route = when (sessionViewModel.resolveStartDestination()) {
            StartDestination.CaptainDashboard -> Route.Dashboard
            StartDestination.RiderHome -> Route.RiderHome
            StartDestination.Onboarding -> Route.Onboarding
        }
        delay(SPLASH_DURATION_MS)
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Crossfade(targetState = showSplash, animationSpec = tween(durationMillis = 400), label = "root") { splash ->
        if (splash) {
            SplashScreen()
        } else {
            when (route) {
                Route.Onboarding -> OnboardingScreen(
                    onNavigateToDashboard = { route = Route.Dashboard },
                    onNavigateToRiderHome = { route = Route.RiderHome },
                )
                Route.Dashboard -> CaptainDashboardScreen(
                    onSeeDetails = { route = Route.StatusDetail },
                    onProfile = { profileReturn = Route.Dashboard; route = Route.Profile },
                    onOpenScheduled = { route = Route.CaptainSchedule },
                    onChat = { request ->
                        // Captain chatting the rider → local user is the captain.
                        chatPeer = ChatPeer(request.riderId, request.riderName, request.riderImageUrl, localUserIsRider = false)
                        chatReturn = Route.Dashboard
                        route = Route.Chat
                    },
                )
                Route.RiderHome -> RiderFlowScreen(
                    onProfile = { profileReturn = Route.RiderHome; route = Route.Profile },
                    onChat = { trip ->
                        // Rider chatting the captain → local user is the rider.
                        chatPeer = ChatPeer(trip.driverId, trip.driverName, trip.driverImageUrl, localUserIsRider = true)
                        chatReturn = Route.RiderHome
                        route = Route.Chat
                    },
                    // SCHEDULE category tile → the scheduled City-to-City flow.
                    onOpenSchedule = { route = Route.ScheduleHome },
                    // RENT-A-CAR category tile → the self-drive car-rental flow.
                    onOpenRental = { route = Route.RentalHome },
                )
                Route.ScheduleHome -> {
                    BackHandler { route = Route.RiderHome }
                    ScheduleFlowScreen(
                        onBack = { route = Route.RiderHome },
                        onChat = { driverId, name, imageUrl ->
                            chatPeer = ChatPeer(driverId, name, imageUrl, localUserIsRider = true)
                            chatReturn = Route.ScheduleHome
                            route = Route.Chat
                        },
                    )
                }
                Route.RentalHome -> {
                    BackHandler { route = Route.RiderHome }
                    RentalFlowScreen(onBack = { route = Route.RiderHome })
                }
                Route.CaptainSchedule -> {
                    BackHandler { route = Route.Dashboard }
                    CaptainScheduleScreen(onBack = { route = Route.Dashboard })
                }
                Route.StatusDetail -> {
                    BackHandler { route = Route.Dashboard }
                    CaptainStatusDetailScreen(onDone = { route = Route.Dashboard })
                }
                Route.Profile -> {
                    BackHandler { route = profileReturn }
                    ProfileScreen(
                        onBack = { route = profileReturn },
                        // RIDER/CAPTAIN switch now lives in the profile menu (rider side only; the captain
                        // dashboard keeps its own toggle for switching back to rider).
                        onSwitchToCaptain = if (profileReturn == Route.RiderHome) {
                            { switchMode(toCaptain = true) }
                        } else {
                            null
                        },
                        // Captain profile → switch back to rider mode.
                        onSwitchToRider = if (profileReturn == Route.Dashboard) {
                            { switchMode(toCaptain = false) }
                        } else {
                            null
                        },
                        onLogout = {
                            // Clear prefs, then navigate home and reset every ViewModel/StateFlow.
                            sessionViewModel.logout {
                                route = Route.Onboarding
                                viewModelStoreOwner?.viewModelStore?.clear()
                            }
                        },
                        onViewBalanceDetails = { route = Route.TopupDetails },
                        onMyRides = { route = Route.MyRides },
                        onMyReports = { route = Route.MyReports },
                        onTerms = { route = Route.Terms },
                        onPrivacy = { route = Route.Privacy },
                        onAppSettings = { route = Route.AppSettings },
                        onCustomerCare = { route = Route.CustomerCare },
                        onNotifications = { route = Route.Notifications },
                    )
                }
                Route.TopupDetails -> {
                    BackHandler { route = Route.Profile }
                    TopupDetailsScreen(onBack = { route = Route.Profile })
                }
                Route.MyRides -> {
                    BackHandler { route = Route.Profile }
                    MyRidesScreen(
                        onBack = { route = Route.Profile },
                        onRideClick = { ride ->
                            selectedRide = ride
                            route = Route.RideDetails
                        },
                    )
                }
                Route.RideDetails -> {
                    BackHandler { route = Route.MyRides }
                    val ride = selectedRide
                    if (ride == null) {
                        route = Route.MyRides
                    } else {
                        RideDetailsScreen(
                            ride = ride,
                            given = selectedRideGiven,
                            onBack = { route = Route.MyRides },
                            onReportProblem = {
                                reportReturn = Route.RideDetails
                                route = Route.ReportProblem
                            },
                        )
                    }
                }
                Route.ReportProblem -> {
                    BackHandler { route = reportReturn }
                    ReportProblemScreen(
                        onBack = { route = reportReturn },
                        onReported = { route = reportReturn },
                    )
                }
                Route.MyReports -> {
                    BackHandler { route = Route.Profile }
                    MyReportsScreen(onBack = { route = Route.Profile })
                }
                Route.AppSettings -> {
                    BackHandler { route = Route.Profile }
                    AppSettingsScreen(onBack = { route = Route.Profile })
                }
                Route.CustomerCare -> {
                    BackHandler { route = Route.Profile }
                    CustomerCareScreen(
                        onBack = { route = Route.Profile },
                        onCallCustomerCare = { dialNumber(context, CUSTOMER_CARE_NUMBER) },
                        onReportProblem = {
                            reportReturn = Route.CustomerCare
                            route = Route.ReportProblem
                        },
                    )
                }
                Route.Notifications -> {
                    BackHandler { route = Route.Profile }
                    NotificationsScreen(onBack = { route = Route.Profile })
                }
                Route.Terms -> {
                    BackHandler { route = Route.Profile }
                    LegalScreen(titleRes = R.string.menu_terms_conditions, page = LegalPage.Terms, onBack = { route = Route.Profile })
                }
                Route.Privacy -> {
                    BackHandler { route = Route.Profile }
                    LegalScreen(titleRes = R.string.menu_privacy_policy, page = LegalPage.Privacy, onBack = { route = Route.Profile })
                }
                Route.Chat -> {
                    BackHandler { route = chatReturn }
                    val peer = chatPeer
                    if (peer == null || peer.id.isBlank()) {
                        route = chatReturn
                    } else {
                        ChatScreen(
                            peerId = peer.id,
                            peerName = peer.name,
                            peerImageUrl = peer.imageUrl,
                            onBack = { route = chatReturn },
                            localUserIsRider = peer.localUserIsRider,
                        )
                    }
                }
            }
        }
    }
        // Mode switch is in flight (GET /captains + change-driver-mode) → block input and show a spinner so
        // the RIDER/CAPTAIN toggle gives feedback instead of appearing to do nothing.
        if (modeSwitching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarrbiTheme.colors.splashBackground.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = DarrbiTheme.colors.primary)
            }
        }
    }
}

/** Opens the phone dialer pre-filled with [number]. */
private fun dialNumber(context: android.content.Context, number: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_DIAL,
        android.net.Uri.parse("tel:$number"),
    )
    context.startActivity(intent)
}
