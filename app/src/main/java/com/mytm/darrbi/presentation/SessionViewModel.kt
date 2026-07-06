package com.mytm.darrbi.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.ApplicationScope
import com.mytm.darrbi.core.common.DispatchersProvider
import com.mytm.darrbi.core.common.SessionEvent
import com.mytm.darrbi.core.common.SessionEventBus
import com.mytm.darrbi.domain.repository.SessionRepository
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.usecase.GetCaptainDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Where the splash should land based on the persisted session. */
enum class StartDestination { Onboarding, RiderHome, CaptainDashboard }

/** App-level session actions used by [DarrbiRoot]. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val getCaptainDetails: GetCaptainDetailsUseCase,
    private val socketService: SocketService,
    private val sessionEventBus: SessionEventBus,
    @ApplicationScope private val appScope: CoroutineScope,
    private val dispatchers: DispatchersProvider,
) : ViewModel() {

    private val _sessionExpired = MutableStateFlow(false)

    /**
     * True once any API reports an expired session token (HTTP 401/440 or the `statusCode:440`
     * envelope). Drives the non-dismissable session-expired sheet in [DarrbiRoot]; resets when this
     * ViewModel is recreated after the forced [logout] clears the store.
     */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    init {
        // The bus is a replay-0 SharedFlow, so a fresh ViewModel (post-logout) never re-fires a stale event.
        viewModelScope.launch {
            sessionEventBus.events.collect { event ->
                when (event) {
                    SessionEvent.SessionExpired -> _sessionExpired.value = true
                }
            }
        }
    }

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
     *
     * Runs on [appScope], NOT `viewModelScope`: [onCleared] clears the Activity's ViewModelStore (to drop
     * every feature ViewModel), which also clears *this* ViewModel and cancels its `viewModelScope`. Doing
     * the work there raced the store teardown — the coroutine could be cancelled at the suspending
     * `clear()` (a DataStore write) before the token was wiped, so the next launch auto-logged the user
     * back in ("logout didn't work"). The application scope survives the store clear, so the session is
     * guaranteed to be cleared before we navigate. [onCleared] touches Compose state + the store, so it's
     * marshalled back to the main thread.
     */
    fun logout(onCleared: () -> Unit) {
        // Dismiss the session-expired sheet right away (the actual clear/navigate below is async, so
        // otherwise the sheet would linger until the DataStore write finishes). Harmless when already false.
        _sessionExpired.value = false
        appScope.launch {
            runCatching { socketService.disconnect() }
            sessionRepository.clear()
            withContext(dispatchers.main) { onCleared() }
        }
    }

    private companion object {
        const val USER_TYPE_CAPTAIN = 2
    }
}
