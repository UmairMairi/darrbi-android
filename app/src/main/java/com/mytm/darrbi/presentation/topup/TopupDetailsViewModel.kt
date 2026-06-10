package com.mytm.darrbi.presentation.topup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.TopupTransaction
import com.mytm.darrbi.domain.usecase.GetTopupHistoryUseCase
import com.mytm.darrbi.domain.usecase.RefundTopupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TopupDetailsUiState(
    val transactions: List<TopupTransaction> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Refund flow
    val refundTarget: TopupTransaction? = null,
    val refundAmount: String = "",
    val showConfirm: Boolean = false,
    val isRefunding: Boolean = false,
) {
    val refundMax: Double get() = refundTarget?.amount ?: 0.0
    val canRefund: Boolean
        get() = (refundAmount.toDoubleOrNull() ?: 0.0).let { it >= REFUND_MIN && it <= refundMax }

    companion object {
        const val REFUND_MIN = 3.0
    }
}

sealed interface TopupDetailsEvent {
    data class OpenRefund(val transaction: TopupTransaction) : TopupDetailsEvent
    data class RefundAmountChanged(val value: String) : TopupDetailsEvent
    data object ProceedToConfirm : TopupDetailsEvent
    data object ConfirmRefund : TopupDetailsEvent
    data object DismissRefund : TopupDetailsEvent
    data object ConsumeError : TopupDetailsEvent
}

@HiltViewModel
class TopupDetailsViewModel @Inject constructor(
    private val getTopupHistory: GetTopupHistoryUseCase,
    private val refundTopup: RefundTopupUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TopupDetailsUiState())
    val state: StateFlow<TopupDetailsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: TopupDetailsEvent) {
        when (event) {
            is TopupDetailsEvent.OpenRefund ->
                _state.update { it.copy(refundTarget = event.transaction, refundAmount = "", showConfirm = false) }
            is TopupDetailsEvent.RefundAmountChanged ->
                _state.update { it.copy(refundAmount = event.value.filter { c -> c.isDigit() || c == '.' }) }
            TopupDetailsEvent.ProceedToConfirm ->
                if (_state.value.canRefund) _state.update { it.copy(showConfirm = true) }
            TopupDetailsEvent.ConfirmRefund -> confirmRefund()
            TopupDetailsEvent.DismissRefund ->
                _state.update { it.copy(refundTarget = null, refundAmount = "", showConfirm = false) }
            TopupDetailsEvent.ConsumeError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getTopupHistory()) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, transactions = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun confirmRefund() {
        val current = _state.value
        val target = current.refundTarget ?: return
        val amount = current.refundAmount.toDoubleOrNull() ?: return
        _state.update { it.copy(isRefunding = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = refundTopup(target.id, amount)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isRefunding = false, refundTarget = null, refundAmount = "", showConfirm = false) }
                    refresh()
                }
                is ApiResult.Error -> _state.update { it.copy(isRefunding = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isRefunding = false, errorMessage = result.error.message) }
            }
        }
    }
}
