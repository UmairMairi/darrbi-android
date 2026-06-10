package com.mytm.darrbi.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.UserReport
import com.mytm.darrbi.domain.usecase.GetUserReportsUseCase
import com.mytm.darrbi.domain.usecase.RateReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyReportsUiState(
    val reports: List<UserReport> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Rate Us flow
    val ratingTarget: UserReport? = null,
    val selectedRating: Int = RATING_EXCELLENT,
    val isSubmitting: Boolean = false,
) {
    companion object {
        const val RATING_EXCELLENT = 5
    }
}

sealed interface MyReportsEvent {
    data class OpenRate(val report: UserReport) : MyReportsEvent
    data class SelectRating(val rating: Int) : MyReportsEvent
    data object SubmitRating : MyReportsEvent
    data object DismissRate : MyReportsEvent
}

@HiltViewModel
class MyReportsViewModel @Inject constructor(
    private val getUserReports: GetUserReportsUseCase,
    private val rateReport: RateReportUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MyReportsUiState())
    val state: StateFlow<MyReportsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: MyReportsEvent) {
        when (event) {
            is MyReportsEvent.OpenRate ->
                _state.update { it.copy(ratingTarget = event.report, selectedRating = MyReportsUiState.RATING_EXCELLENT) }
            is MyReportsEvent.SelectRating -> _state.update { it.copy(selectedRating = event.rating) }
            MyReportsEvent.SubmitRating -> submitRating()
            MyReportsEvent.DismissRate -> _state.update { it.copy(ratingTarget = null) }
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getUserReports()) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, reports = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun submitRating() {
        val target = _state.value.ratingTarget ?: return
        val rating = _state.value.selectedRating
        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (rateReport(target.id, rating)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isSubmitting = false, ratingTarget = null) }
                    refresh()
                }
                else -> _state.update { it.copy(isSubmitting = false, ratingTarget = null) }
            }
        }
    }
}
