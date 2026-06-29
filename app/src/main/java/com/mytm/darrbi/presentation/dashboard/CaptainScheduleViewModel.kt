package com.mytm.darrbi.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.C2cCity
import com.mytm.darrbi.domain.model.UpcomingScheduledTrip
import com.mytm.darrbi.domain.usecase.GetC2cCitiesUseCase
import com.mytm.darrbi.domain.usecase.GetMyUpcomingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DRIVER-side City-to-City scheduled rides (see `V2_CITY_TO_CITY_MOBILE_INTEGRATION_GUIDE.md` §9). Lists the
 * driver's matched/open upcoming scheduled rides (`/v2/schedule/my-upcoming`, server-filtered to this driver).
 * Discovering + bidding on OPEN intercity requests lives on the captain dashboard ([CaptainDashboardViewModel]).
 * After activation (~15 min before departure) the trip becomes a normal assigned trip on the dashboard.
 */
data class CaptainScheduleUiState(
    val cities: List<C2cCity> = emptyList(),
    val upcoming: List<UpcomingScheduledTrip> = emptyList(),
    val isLoading: Boolean = false,
) {
    /** Resolve a city id to a [C2cCity] for display. */
    fun city(id: String?): C2cCity? = cities.firstOrNull { it.id == id }
}

sealed interface CaptainScheduleEvent {
    data object Refresh : CaptainScheduleEvent
}

@HiltViewModel
class CaptainScheduleViewModel @Inject constructor(
    private val getCities: GetC2cCitiesUseCase,
    private val getMyUpcoming: GetMyUpcomingUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptainScheduleUiState())
    val state: StateFlow<CaptainScheduleUiState> = _state.asStateFlow()

    init {
        loadCities()
        refresh()
    }

    fun onEvent(event: CaptainScheduleEvent) {
        when (event) {
            CaptainScheduleEvent.Refresh -> refresh()
        }
    }

    private fun loadCities() {
        viewModelScope.launch {
            when (val result = getCities()) {
                is ApiResult.Success -> _state.update { it.copy(cities = result.data) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val upcoming = when (val result = getMyUpcoming()) {
                is ApiResult.Success -> result.data
                is ApiResult.Error, is ApiResult.Failure -> emptyList()
            }
            _state.update { it.copy(isLoading = false, upcoming = upcoming) }
        }
    }
}
