package com.mytm.darrbi.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.usecase.ChangeDriverModeUseCase
import com.mytm.darrbi.domain.usecase.GetCaptainDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a mode switch should land the user. */
enum class ModeTarget { Rider, Captain, RegisterCaptain }

/**
 * Switches the active RIDER/CAPTAIN mode, mirroring ride-android: read the current mode via
 * `GET /captains` ([CaptainDetails.driverModeSwitch]) and only call `POST /captains/change-driver-mode`
 * when the requested mode differs. A `driver_not_found` error means the user isn't a registered driver yet.
 */
@HiltViewModel
class ModeSwitchViewModel @Inject constructor(
    private val getCaptainDetails: GetCaptainDetailsUseCase,
    private val changeDriverMode: ChangeDriverModeUseCase,
) : ViewModel() {

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun consumeError() { _error.value = null }

    /** Resolves and applies the switch to [toCaptain], then reports where to navigate via [onResult]. */
    fun switchMode(toCaptain: Boolean, onResult: (ModeTarget) -> Unit) {
        if (_switching.value) return
        _switching.value = true
        viewModelScope.launch {
            val target = resolveTarget(toCaptain)
            _switching.value = false
            target?.let(onResult)
        }
    }

    private suspend fun resolveTarget(toCaptain: Boolean): ModeTarget? = when (val details = getCaptainDetails()) {
        is ApiResult.Success -> {
            val currentlyCaptain = details.data.driverModeSwitch == true
            if (toCaptain == currentlyCaptain) {
                // Already in the requested mode server-side — just navigate.
                if (toCaptain) ModeTarget.Captain else ModeTarget.Rider
            } else {
                // Flip the server-side mode, then navigate (only proceed if the flip succeeds).
                when (val flip = changeDriverMode()) {
                    is ApiResult.Success -> if (toCaptain) ModeTarget.Captain else ModeTarget.Rider
                    is ApiResult.Error -> { _error.value = flip.message; null }
                    is ApiResult.Failure -> { _error.value = flip.error.message; null }
                }
            }
        }
        is ApiResult.Error -> {
            // GET /captains returns 404 "Driver Not Found" when the user isn't a registered driver yet.
            val notDriver = details.code == 404 || details.message?.contains("not found", ignoreCase = true) == true
            when {
                !toCaptain -> ModeTarget.Rider // rider mode is always available
                notDriver -> ModeTarget.RegisterCaptain
                else -> { _error.value = details.message; null }
            }
        }
        is ApiResult.Failure -> if (!toCaptain) ModeTarget.Rider else { _error.value = details.error.message; null }
    }
}
