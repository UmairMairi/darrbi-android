package com.mytm.darrbi.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.repository.SessionRepository
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.usecase.GetCaptainDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the splash should land based on the persisted session. */
enum class StartDestination { Onboarding, RiderHome, CaptainDashboard }

/** App-level session actions used by [DarrbiRoot]. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val getCaptainDetails: GetCaptainDetailsUseCase,
    private val socketService: SocketService,
) : ViewModel() {

    /**
     * Auto-login routing: a saved token + completed onboarding lands the user straight on their dashboard;
     * otherwise the onboarding (phone) flow is shown. A userType-2 user has a driver profile, but the
     * *current* status comes from `GET /captains` — `driverModeSwitch` true → captain, false → rider.
     */
    suspend fun resolveStartDestination(): StartDestination {
        val hasSession = sessionRepository.loadSession()
        if (!hasSession || !sessionRepository.isNameUpdated) return StartDestination.Onboarding
        if (sessionRepository.userType != USER_TYPE_CAPTAIN) return StartDestination.RiderHome
        val inDriverMode = (getCaptainDetails() as? ApiResult.Success)?.data?.driverModeSwitch == true
        return if (inDriverMode) StartDestination.CaptainDashboard else StartDestination.RiderHome
    }

    /**
     * Logs out: clears the saved session/preferences, then runs [onCleared] — the caller navigates to
     * onboarding and drops all feature ViewModels (and their StateFlows) so the next session starts clean.
     */
    fun logout(onCleared: () -> Unit) {
        viewModelScope.launch {
            socketService.disconnect()
            sessionRepository.clear()
            onCleared()
        }
    }

    private companion object {
        const val USER_TYPE_CAPTAIN = 2
    }
}
