package com.mytm.darrbi.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mytm.darrbi.R
import com.mytm.darrbi.domain.model.Ride
import com.mytm.darrbi.presentation.dashboard.CaptainDashboardScreen
import com.mytm.darrbi.presentation.dashboard.CaptainStatusDetailScreen
import com.mytm.darrbi.presentation.legal.LegalPage
import com.mytm.darrbi.presentation.legal.LegalScreen
import com.mytm.darrbi.presentation.notifications.NotificationsScreen
import com.mytm.darrbi.presentation.onboarding.OnboardingScreen
import com.mytm.darrbi.presentation.profile.ProfileScreen
import com.mytm.darrbi.presentation.reports.MyReportsScreen
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
    Onboarding, Dashboard, StatusDetail, Profile, TopupDetails,
    MyRides, RideDetails, ReportProblem, MyReports, Terms, Privacy,
    AppSettings, CustomerCare, Notifications,
}

/**
 * App root: branded splash for 3s, then the onboarding flow. A successful captain application routes to
 * the dashboard, whose profile opens the account screens (rides, reports, settings, support, legal, …).
 */
@Composable
fun DarrbiRoot() {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }
    var route by remember { mutableStateOf(Route.Onboarding) }
    // Ride selected from the My Rides list, passed to the details screen.
    var selectedRide by remember { mutableStateOf<Ride?>(null) }
    var selectedRideGiven by remember { mutableStateOf(false) }
    // Where "Report a Problem" should return to (it's reachable from Ride Details and Customer Care).
    var reportReturn by remember { mutableStateOf(Route.RideDetails) }

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        showSplash = false
    }

    Crossfade(targetState = showSplash, animationSpec = tween(durationMillis = 400), label = "root") { splash ->
        if (splash) {
            SplashScreen()
        } else {
            when (route) {
                Route.Onboarding -> OnboardingScreen(onNavigateToDashboard = { route = Route.Dashboard })
                Route.Dashboard -> CaptainDashboardScreen(
                    onSeeDetails = { route = Route.StatusDetail },
                    onProfile = { route = Route.Profile },
                )
                Route.StatusDetail -> {
                    BackHandler { route = Route.Dashboard }
                    CaptainStatusDetailScreen(onDone = { route = Route.Dashboard })
                }
                Route.Profile -> {
                    BackHandler { route = Route.Dashboard }
                    ProfileScreen(
                        onBack = { route = Route.Dashboard },
                        onLogout = { route = Route.Onboarding },
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
