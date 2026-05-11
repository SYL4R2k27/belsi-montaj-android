package com.belsi.work.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана смены пароля.
 *
 * Логика:
 *  - Поля: oldPassword, newPassword, confirmPassword
 *  - Валидация: новый ≥ 6 символов, совпадает с подтверждением
 *  - Вызов POST /auth/change-password через AuthRepository
 *  - Успех → Snackbar + popBackStack
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _oldPassword = MutableStateFlow("")
    val oldPassword: StateFlow<String> = _oldPassword.asStateFlow()

    private val _newPassword = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    sealed class Event {
        data class Success(val message: String) : Event()
    }

    fun onOldPasswordChanged(value: String) {
        _oldPassword.value = value
        _error.value = null
    }

    fun onNewPasswordChanged(value: String) {
        _newPassword.value = value
        _error.value = null
    }

    fun onConfirmPasswordChanged(value: String) {
        _confirmPassword.value = value
        _error.value = null
    }

    fun submit() {
        val oldPwd = _oldPassword.value
        val newPwd = _newPassword.value
        val confirm = _confirmPassword.value

        when {
            oldPwd.isBlank() -> {
                _error.value = "Введите текущий пароль"
                return
            }
            newPwd.length < 6 -> {
                _error.value = "Новый пароль должен быть не менее 6 символов"
                return
            }
            newPwd != confirm -> {
                _error.value = "Пароли не совпадают"
                return
            }
            newPwd == oldPwd -> {
                _error.value = "Новый пароль совпадает с текущим"
                return
            }
        }

        viewModelScope.launch {
            _isLoading.value = true
            authRepository.changePassword(oldPwd, newPwd)
                .onSuccess { msg ->
                    _events.emit(Event.Success(msg.ifBlank { "Пароль обновлён" }))
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Не удалось сменить пароль"
                }
            _isLoading.value = false
        }
    }
}
