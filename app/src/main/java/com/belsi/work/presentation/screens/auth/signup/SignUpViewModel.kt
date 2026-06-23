package com.belsi.work.presentation.screens.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-04): SignUp wizard — V1 без email/SMS подтверждения.
 *
 * Шаг 1: Телефон + пароль + подтверждение пароля
 * Шаг 2: Имя + фамилия (+ опц. email)
 * Шаг 3: Выбор роли → submit → переход на главный экран
 */
enum class SignUpStep { Credentials, Name, Role }

data class SignUpUiState(
    val step: SignUpStep = SignUpStep.Credentials,
    val phone: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    // FIX(2026-05-23): мультироль при регистрации (до 3). Первая = основная.
    val selectedRoles: List<String> = listOf("installer"),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class SignUpNavigationEvent {
    object NavigateToTerms : SignUpNavigationEvent()
    object NavigateToMain : SignUpNavigationEvent()
    object NavigateToForemanMain : SignUpNavigationEvent()
    object NavigateToCoordinatorMain : SignUpNavigationEvent()
    object NavigateToCuratorMain : SignUpNavigationEvent()
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private val _navigationEvent = Channel<SignUpNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun setPhone(v: String) = _state.update { it.copy(phone = v, error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun setPasswordConfirm(v: String) = _state.update { it.copy(passwordConfirm = v, error = null) }
    fun setFirstName(v: String) = _state.update { it.copy(firstName = v, error = null) }
    fun setLastName(v: String) = _state.update { it.copy(lastName = v, error = null) }
    fun setEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    /** FIX(2026-05-23): мультивыбор ролей (до 3, как лимит enforce_max_3_roles на бэке). */
    fun toggleRole(v: String) = _state.update { st ->
        val cur = st.selectedRoles
        val next = when {
            v in cur -> cur - v
            cur.size >= 3 -> cur  // лимит 3 — игнорируем добавление
            else -> cur + v
        }
        st.copy(selectedRoles = next, error = null)
    }

    /** Шаг 1 → 2 — валидация телефона и пароля */
    fun nextFromCredentials() {
        val s = _state.value
        val phone = s.phone.trim()
        if (phone.length < 10) {
            _state.update { it.copy(error = "Введите корректный телефон") }
            return
        }
        if (s.password.length < 8) {
            _state.update { it.copy(error = "Пароль не короче 8 символов") }
            return
        }
        if (!s.password.any { it.isDigit() } || !s.password.any { it.isLetter() }) {
            _state.update { it.copy(error = "Пароль должен содержать буквы и цифры") }
            return
        }
        if (s.password != s.passwordConfirm) {
            _state.update { it.copy(error = "Пароли не совпадают") }
            return
        }
        _state.update { it.copy(step = SignUpStep.Name, error = null) }
    }

    /** Шаг 2 → 3 — валидация имени */
    fun nextFromName() {
        val s = _state.value
        if (s.firstName.isBlank()) {
            _state.update { it.copy(error = "Введите имя") }
            return
        }
        if (s.email.isNotBlank() && !s.email.contains("@")) {
            _state.update { it.copy(error = "Email указан некорректно") }
            return
        }
        _state.update { it.copy(step = SignUpStep.Role, error = null) }
    }

    /** Шаг 3 → submit → backend */
    fun submit() {
        val s = _state.value
        if (s.isLoading) return
        if (s.selectedRoles.isEmpty()) {
            _state.update { it.copy(error = "Выберите хотя бы одну роль") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            // Нормализация телефона: добавляем + если отсутствует
            val normalizedPhone = s.phone.trim().let { p ->
                when {
                    p.startsWith("+") -> p
                    p.startsWith("8") -> "+7" + p.drop(1)
                    p.startsWith("7") -> "+$p"
                    else -> "+$p"
                }
            }

            authRepository.signup(
                phone = normalizedPhone,
                password = s.password,
                firstName = s.firstName.trim(),
                lastName = s.lastName.trim(),
                roles = s.selectedRoles,
                email = s.email.trim().takeIf { it.isNotBlank() },
            ).fold(
                onSuccess = {
                    // Навигация по ОСНОВНОЙ (первой) роли. Остальные доступны через переключатель ролей.
                    val primary = s.selectedRoles.firstOrNull()?.lowercase() ?: "installer"
                    val event = when (primary) {
                        "foreman" -> SignUpNavigationEvent.NavigateToForemanMain
                        "coordinator" -> SignUpNavigationEvent.NavigateToCoordinatorMain
                        "curator" -> SignUpNavigationEvent.NavigateToCuratorMain
                        else -> SignUpNavigationEvent.NavigateToMain
                    }
                    _navigationEvent.send(event)
                    _state.update { it.copy(isLoading = false) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: "Не удалось создать аккаунт")
                    }
                }
            )
        }
    }

    fun back() {
        _state.update {
            val prev = when (it.step) {
                SignUpStep.Credentials -> it.step
                SignUpStep.Name -> SignUpStep.Credentials
                SignUpStep.Role -> SignUpStep.Name
            }
            it.copy(step = prev, error = null)
        }
    }
}

private fun MutableStateFlow<SignUpUiState>.update(transform: (SignUpUiState) -> SignUpUiState) {
    value = transform(value)
}
