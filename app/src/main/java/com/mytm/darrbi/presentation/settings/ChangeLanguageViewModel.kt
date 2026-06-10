package com.mytm.darrbi.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.datastore.LanguageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangeLanguageViewModel @Inject constructor(
    private val languageStore: LanguageStore,
) : ViewModel() {

    val language: StateFlow<String> = languageStore.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = languageStore.language,
    )

    fun select(tag: String) {
        viewModelScope.launch { languageStore.setLanguage(tag) }
    }
}
