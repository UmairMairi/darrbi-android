package com.mytm.darrbi.presentation.rides

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.Ride
import com.mytm.darrbi.domain.usecase.GetRidesGivenUseCase
import com.mytm.darrbi.domain.usecase.GetRidesTakenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RidesTab { Taken, Given }

data class MyRidesUiState(
    val tab: RidesTab = RidesTab.Taken,
    val taken: List<Ride> = emptyList(),
    val given: List<Ride> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val rides: List<Ride> get() = if (tab == RidesTab.Taken) taken else given
}

@HiltViewModel
class MyRidesViewModel @Inject constructor(
    private val getRidesTaken: GetRidesTakenUseCase,
    private val getRidesGiven: GetRidesGivenUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MyRidesUiState())
    val state: StateFlow<MyRidesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun selectTab(tab: RidesTab) = _state.update { it.copy(tab = tab) }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    private fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getRidesTaken()) {
                is ApiResult.Success -> _state.update { it.copy(taken = result.data) }
                is ApiResult.Error -> _state.update { it.copy(errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(errorMessage = result.error.message) }
            }
            when (val result = getRidesGiven()) {
                is ApiResult.Success -> _state.update { it.copy(given = result.data) }
                else -> Unit
            }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
