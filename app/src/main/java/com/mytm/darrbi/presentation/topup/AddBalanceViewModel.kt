package com.mytm.darrbi.presentation.topup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.usecase.GetHostedTopUpUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the "Add Balance" hosted-page top-up: fetches the ClickPay URL to open in the WebView. */
@HiltViewModel
class AddBalanceViewModel @Inject constructor(
    private val getHostedTopUpUrl: GetHostedTopUpUrlUseCase,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Requests the hosted-page URL; calls [onResult] with the URL on success, or null on failure. */
    fun requestHostedUrl(amount: Int, onResult: (String?) -> Unit) {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            val result = getHostedTopUpUrl(amount)
            _loading.value = false
            onResult((result as? ApiResult.Success)?.data)
        }
    }
}
