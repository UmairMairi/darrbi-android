package com.mytm.darrbi.presentation.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.datastore.LanguageStore
import com.mytm.darrbi.domain.usecase.GetLegalContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which legal page to load. */
enum class LegalPage { Terms, Privacy }

data class LegalUiState(
    val html: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LegalViewModel @Inject constructor(
    private val getLegalContent: GetLegalContentUseCase,
    private val languageStore: LanguageStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LegalUiState())
    val state: StateFlow<LegalUiState> = _state.asStateFlow()

    fun load(page: LegalPage) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = getLegalContent(privacy = page == LegalPage.Privacy, language = languageStore.language)
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, html = result.data) }
                is ApiResult.Error -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }
}
