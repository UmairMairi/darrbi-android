package com.mytm.darrbi.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.UserProfile
import com.mytm.darrbi.domain.repository.SessionRepository
import com.mytm.darrbi.domain.usecase.GetBalanceUseCase
import com.mytm.darrbi.domain.usecase.GetIbanUseCase
import com.mytm.darrbi.domain.usecase.GetUserDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: UserProfile? = null,
    val iban: String? = null,
    val bank: String? = null,
    val isLoadingIban: Boolean = false,
    /** Wallet balance from `GET /user/get-balance`; null until loaded. */
    val balance: Double? = null,
)

/**
 * Profile state: the user model (seeded from the cached verify-OTP session for instant display, then
 * refreshed from `GET /getuserdetails` as ride-android's ProfileActivity does), plus the saved IBAN/bank
 * from `GET /user/get-iban`.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val getUserDetails: GetUserDetailsUseCase,
    private val getIban: GetIbanUseCase,
    private val getBalance: GetBalanceUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(user = sessionRepository.user))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        loadUser()
        loadIban()
        loadBalance()
    }

    /** Refresh the name + profile details from `GET /getuserdetails`, keeping the cached copy on failure. */
    private fun loadUser() {
        viewModelScope.launch {
            when (val result = getUserDetails()) {
                is ApiResult.Success -> _state.update { it.copy(user = result.data) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    private fun loadBalance() {
        viewModelScope.launch {
            when (val result = getBalance()) {
                is ApiResult.Success -> _state.update { it.copy(balance = result.data) }
                is ApiResult.Error, is ApiResult.Failure -> Unit
            }
        }
    }

    /** Re-fetch the IBAN (e.g. after the user updates it). */
    fun refreshIban() = loadIban()

    /** Re-fetch the wallet balance (e.g. after a successful top-up). */
    fun refreshBalance() = loadBalance()

    private fun loadIban() {
        _state.update { it.copy(isLoadingIban = true) }
        viewModelScope.launch {
            when (val result = getIban()) {
                is ApiResult.Success ->
                    _state.update { it.copy(isLoadingIban = false, iban = result.data.iban, bank = result.data.bank) }
                // No IBAN on file / not a driver → leave the IBAN card hidden.
                is ApiResult.Error, is ApiResult.Failure ->
                    _state.update { it.copy(isLoadingIban = false) }
            }
        }
    }
}
