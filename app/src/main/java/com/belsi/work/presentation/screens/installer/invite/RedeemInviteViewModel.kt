package com.belsi.work.presentation.screens.installer.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.InviteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RedeemInviteViewModel @Inject constructor(
    private val inviteRepository: InviteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RedeemInviteUiState())
    val uiState: StateFlow<RedeemInviteUiState> = _uiState.asStateFlow()

    /**
     * Обновить введённый код
     */
    fun updateCode(code: String) {
        // Санитизация: убираем переносы строк, только заглавные буквы и цифры, максимум 6 символов
        val filteredCode = code
            .replace("\n", "")
            .replace("\r", "")
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .take(6)
        _uiState.value = _uiState.value.copy(
            code = filteredCode,
            errorMessage = null
        )
    }

    /**
     * Активировать инвайт-код
     */
    fun redeemInvite() {
        val code = _uiState.value.code.trim()

        if (code.length != 6) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Код должен содержать 6 символов"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            inviteRepository.redeemInvite(code)
                .onSuccess { result ->
                    android.util.Log.d("RedeemInviteVM", "Invite redeemed: success=${result.success}, foreman=${result.foremanName}")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true,
                        foremanPhone = result.foremanName,
                        successMessage = result.message ?: "Вы успешно присоединились к команде!"
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("RedeemInviteVM", "Failed to redeem invite", e)

                    val errorMsg = when {
                        e.message?.contains("404") == true -> "Код не найден или уже использован"
                        e.message?.contains("expired") == true -> "Срок действия кода истёк"
                        e.message?.contains("already") == true -> "Вы уже состоите в команде"
                        else -> "Ошибка: ${e.message ?: "Неизвестная ошибка"}"
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = errorMsg
                    )
                }
        }
    }

    /**
     * Очистить сообщение об успехе
     */
    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(
            isSuccess = false,
            successMessage = null
        )
    }
}

data class RedeemInviteUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val foremanPhone: String? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)
