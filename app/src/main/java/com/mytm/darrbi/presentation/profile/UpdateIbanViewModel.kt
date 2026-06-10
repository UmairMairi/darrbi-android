package com.mytm.darrbi.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.usecase.ValidateIbanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateIbanUiState(
    val ibanInput: String = "",
    val verifying: Boolean = false,
    val error: Boolean = false,
    val bankName: String? = null,
    val done: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !verifying && ibanInput.startsWith("SA", ignoreCase = true) && ibanInput.length == IBAN_LENGTH

    companion object {
        const val IBAN_LENGTH = 24 // Saudi IBAN: "SA" + 22 digits
    }
}

/** Validates and updates the user's IBAN — same `validate-iban` flow used on the dashboard. */
@HiltViewModel
class UpdateIbanViewModel @Inject constructor(
    private val validateIban: ValidateIbanUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateIbanUiState())
    val state: StateFlow<UpdateIbanUiState> = _state.asStateFlow()

    fun onIbanChanged(value: String) {
        _state.update {
            it.copy(
                ibanInput = value.filter { c -> !c.isWhitespace() }.uppercase()
                    .take(UpdateIbanUiState.IBAN_LENGTH),
                error = false,
                bankName = null,
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(verifying = true, error = false, bankName = null) }
        viewModelScope.launch {
            when (val result = validateIban(current.ibanInput)) {
                is ApiResult.Success -> _state.update { it.copy(verifying = false, bankName = result.data.bank, done = true) }
                is ApiResult.Error, is ApiResult.Failure -> _state.update { it.copy(verifying = false, error = true) }
            }
        }
    }
}
