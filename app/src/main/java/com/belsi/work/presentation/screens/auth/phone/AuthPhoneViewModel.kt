package com.belsi.work.presentation.screens.auth.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.domain.usecases.auth.AuthWithYandexUseCase
import com.belsi.work.domain.usecases.auth.SendOTPUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthPhoneViewModel @Inject constructor(
    private val sendOTPUseCase: SendOTPUseCase,
    private val authWithYandexUseCase: AuthWithYandexUseCase
) : ViewModel() {

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    fun onPhoneChanged(value: String) {
        // Сохраняем только цифры (без форматирования)
        // Форматирование будет применяться через VisualTransformation
        val digits = value.filter { it.isDigit() }.take(11)
        _phone.value = digits
        _errorMessage.value = null
    }

    fun sendOTP() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // Валидация номера телефона
            if (!isValidPhone()) {
                _errorMessage.value = "Введите корректный номер телефона (11 цифр)"
                _isLoading.value = false
                return@launch
            }

            // Нормализуем телефон в формат +7XXXXXXXXXX
            val normalizedPhone = normalizedPhone()

            println("AuthPhoneViewModel: Отправка OTP на номер: $normalizedPhone")
            val result = sendOTPUseCase(normalizedPhone)

            _isLoading.value = false

            result.onSuccess {
                println("AuthPhoneViewModel: Навигация на OTP с номером: $normalizedPhone")
                _navigationEvent.emit(NavigationEvent.NavigateToOTP(normalizedPhone))
            }.onFailure { error ->
                println("AuthPhoneViewModel: Ошибка OTP: ${error.message}")
                _errorMessage.value = error.message ?: "Ошибка отправки кода"
            }
        }
    }

    /**
     * Обработка результата Yandex OAuth
     */
    fun onYandexAuthResult(yandexToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = authWithYandexUseCase(yandexToken)

            _isLoading.value = false

            result.onSuccess { authResult ->
                _navigationEvent.emit(NavigationEvent.NavigateToTerms(authResult.phone))
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Ошибка авторизации через Яндекс"
            }
        }
    }

    fun onYandexAuthError(message: String) {
        _errorMessage.value = message
    }

    /**
     * Проверка валидности телефонного номера
     * - Должен содержать ровно 11 цифр
     * - Должен начинаться с 7 или 8
     */
    private fun isValidPhone(): Boolean {
        val digits = _phone.value.trim()
        if (digits.length != 11) return false

        val firstDigit = digits.firstOrNull() ?: return false
        return firstDigit == '7' || firstDigit == '8'
    }

    /**
     * Нормализованный телефон в формате +7XXXXXXXXXX
     * - Если начинается с 8, заменяет на +7
     * - Если начинается с 7, добавляет +
     */
    private fun normalizedPhone(): String {
        val digits = _phone.value.trim()
        if (digits.length != 11) return _phone.value

        return when (digits.first()) {
            '8' -> "+7${digits.substring(1)}"
            '7' -> "+$digits"
            else -> _phone.value
        }
    }
}

sealed class NavigationEvent {
    data class NavigateToOTP(val phone: String) : NavigationEvent()
    data class NavigateToTerms(val phone: String) : NavigationEvent()
}
