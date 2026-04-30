package com.belsi.work.presentation.screens.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.repositories.AuthRepository
import com.belsi.work.data.repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val prefsManager: PrefsManager
) : ViewModel() {

    private val _login = MutableStateFlow("")
    val login: StateFlow<String> = _login.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<LoginNavigationEvent>()
    val navigationEvent: SharedFlow<LoginNavigationEvent> = _navigationEvent.asSharedFlow()

    fun onLoginChanged(value: String) {
        _login.value = value
        _errorMessage.value = null
    }

    fun onPasswordChanged(value: String) {
        _password.value = value
        _errorMessage.value = null
    }

    fun login() {
        val loginValue = _login.value.trim()
        val passwordValue = _password.value

        if (loginValue.isBlank()) {
            _errorMessage.value = "Введите логин, телефон или email"
            return
        }
        if (passwordValue.isBlank()) {
            _errorMessage.value = "Введите пароль"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = authRepository.login(loginValue, passwordValue)

            _isLoading.value = false

            result.onSuccess { authResult ->
                if (authResult.isNew) {
                    // Новый пользователь → онбординг
                    _navigationEvent.emit(LoginNavigationEvent.NavigateToTerms)
                } else {
                    // Существующий → загружаем профиль и переходим по роли
                    loadProfileAndNavigate()
                }
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Неверный логин или пароль"
            }
        }
    }

    /**
     * Загрузить профиль с сервера и перейти на главный экран по роли.
     * Паттерн из OTPViewModel.
     */
    private fun loadProfileAndNavigate() {
        viewModelScope.launch {
            try {
                userRepository.getProfile()
                    .onSuccess { user ->
                        prefsManager.setTermsAccepted(true)
                        prefsManager.setOnboardingCompleted(true)

                        val event = when (user.role) {
                            UserRole.FOREMAN -> LoginNavigationEvent.NavigateToForemanMain
                            UserRole.COORDINATOR -> LoginNavigationEvent.NavigateToCoordinatorMain
                            UserRole.CURATOR -> LoginNavigationEvent.NavigateToCuratorMain
                            else -> LoginNavigationEvent.NavigateToMain
                        }
                        _navigationEvent.emit(event)
                    }
                    .onFailure {
                        prefsManager.setTermsAccepted(true)
                        prefsManager.setOnboardingCompleted(true)

                        val user = prefsManager.getUser()
                        val event = when (user?.role) {
                            UserRole.FOREMAN -> LoginNavigationEvent.NavigateToForemanMain
                            UserRole.COORDINATOR -> LoginNavigationEvent.NavigateToCoordinatorMain
                            UserRole.CURATOR -> LoginNavigationEvent.NavigateToCuratorMain
                            else -> LoginNavigationEvent.NavigateToMain
                        }
                        _navigationEvent.emit(event)
                    }
            } catch (e: Exception) {
                prefsManager.setTermsAccepted(true)
                prefsManager.setOnboardingCompleted(true)
                _navigationEvent.emit(LoginNavigationEvent.NavigateToMain)
            }
        }
    }
}

sealed class LoginNavigationEvent {
    object NavigateToTerms : LoginNavigationEvent()
    object NavigateToMain : LoginNavigationEvent()
    object NavigateToForemanMain : LoginNavigationEvent()
    object NavigateToCoordinatorMain : LoginNavigationEvent()
    object NavigateToCuratorMain : LoginNavigationEvent()
}
