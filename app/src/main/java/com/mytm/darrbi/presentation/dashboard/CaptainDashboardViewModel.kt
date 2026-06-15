package com.mytm.darrbi.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.usecase.CurrentLocationUseCase
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.usecase.GetCaptainDetailsUseCase
import com.mytm.darrbi.domain.usecase.ValidateIbanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which card the captain dashboard shows over the map, derived from `GET /captains`. */
enum class CaptainStage { Loading, UnderReview, Approved, EnterIban, NoRiders }

data class CaptainDashboardUiState(
    val stage: CaptainStage = CaptainStage.Loading,
    val captain: CaptainDetails? = null,
    /** Set when the captain taps "Start Now" on the approved card. */
    val started: Boolean = false,
    val ibanInput: String = "",
    val ibanVerifying: Boolean = false,
    val ibanError: Boolean = false,
    val bankName: String? = null,
    /** Device location, once the captain is set up and grants permission, used to centre the map. */
    val myLocation: PlaceLocation? = null,
    val errorMessage: String? = null,
) {
    val canSubmitIban: Boolean
        get() = !ibanVerifying && ibanInput.startsWith("SA", ignoreCase = true) && ibanInput.length == IBAN_LENGTH

    companion object {
        const val IBAN_LENGTH = 24 // Saudi IBAN: "SA" + 22 digits
    }
}

sealed interface CaptainDashboardEvent {
    data object StartNow : CaptainDashboardEvent
    data class IbanChanged(val value: String) : CaptainDashboardEvent
    data object SubmitIban : CaptainDashboardEvent
    data object ConsumeError : CaptainDashboardEvent
}

@HiltViewModel
class CaptainDashboardViewModel @Inject constructor(
    private val getCaptainDetails: GetCaptainDetailsUseCase,
    private val validateIban: ValidateIbanUseCase,
    private val currentLocation: CurrentLocationUseCase,
    private val socketService: SocketService,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptainDashboardUiState())
    val state: StateFlow<CaptainDashboardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Centre the map on the device location (called once permission is granted on the ready dashboard). */
    fun locateMe() {
        viewModelScope.launch {
            when (val result = currentLocation()) {
                is ApiResult.Success -> _state.update { it.copy(myLocation = result.data) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    fun onEvent(event: CaptainDashboardEvent) {
        when (event) {
            CaptainDashboardEvent.StartNow -> {
                val stage = stageFor(_state.value.captain, started = true)
                _state.update { it.copy(started = true, stage = stage) }
                connectSocketIfReady(stage)
            }
            is CaptainDashboardEvent.IbanChanged -> _state.update {
                // Keep the raw IBAN (uppercased, no spaces); the field formats it for display.
                it.copy(
                    ibanInput = event.value.filter { c -> !c.isWhitespace() }.uppercase()
                        .take(CaptainDashboardUiState.IBAN_LENGTH),
                    ibanError = false,
                    bankName = null,
                )
            }
            CaptainDashboardEvent.SubmitIban -> submitIban()
            CaptainDashboardEvent.ConsumeError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    /** Fetches `GET /captains` (cached into the session by the use case) and recomputes the stage. */
    private fun refresh() {
        _state.update { it.copy(stage = CaptainStage.Loading) }
        viewModelScope.launch {
            when (val result = getCaptainDetails()) {
                is ApiResult.Success -> {
                    val stage = stageFor(result.data, _state.value.started)
                    _state.update { it.copy(captain = result.data, stage = stage) }
                    // All checks passed (WASL approved + IBAN set → NoRiders) → open the socket.
                    connectSocketIfReady(stage)
                }
                // No captain yet / fetch failed → treat as still under review.
                is ApiResult.Error ->
                    _state.update { it.copy(captain = null, stage = stageFor(null, it.started)) }
                is ApiResult.Failure ->
                    _state.update { it.copy(stage = stageFor(it.captain, it.started), errorMessage = result.error.message) }
            }
        }
    }

    private fun submitIban() {
        val current = _state.value
        if (!current.canSubmitIban) return
        _state.update { it.copy(ibanVerifying = true, ibanError = false, bankName = null) }
        viewModelScope.launch {
            when (val result = validateIban(current.ibanInput)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(ibanVerifying = false, bankName = result.data.bank, ibanError = false) }
                    // IBAN accepted → re-fetch captain details and re-run all checks (matches ride-android).
                    refresh()
                }
                is ApiResult.Error -> _state.update { it.copy(ibanVerifying = false, ibanError = true) }
                is ApiResult.Failure -> _state.update { it.copy(ibanVerifying = false, ibanError = true) }
            }
        }
    }

    /**
     * Captain dashboard stage rules:
     * - WASL not approved (isWASLApproved != 1) → under review.
     * - WASL approved & IBAN already saved → "no riders around" directly.
     * - WASL approved & no IBAN → "Application Approved" (Start Now) first, then add IBAN.
     */
    private fun stageFor(captain: CaptainDetails?, started: Boolean): CaptainStage = when {
        captain?.isWaslApproved != 1 -> CaptainStage.UnderReview
        !captain.iban.isNullOrBlank() -> CaptainStage.NoRiders
        !started -> CaptainStage.Approved
        else -> CaptainStage.EnterIban
    }

    /** Connect the real-time socket only when the captain is fully verified (WASL approved + IBAN set). */
    private fun connectSocketIfReady(stage: CaptainStage) {
        if (stage == CaptainStage.NoRiders) socketService.connect()
    }
}
