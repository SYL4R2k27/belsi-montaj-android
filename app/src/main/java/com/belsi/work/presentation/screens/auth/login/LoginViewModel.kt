package com.belsi.work.presentation.screens.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.repositories.AuthRepository
import com.belsi.work.data.repositories.UserRepository
import com.belsi.work.domain.usecases.auth.AuthWithYandexUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val prefsManager: PrefsManager,
    // FIX(2026-05-12) build19+: Yandex как secondary auth-метод прямо из LoginScreen
    private val authWithYandexUseCase: AuthWithYandexUseCase,
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

    // FIX(2026-05-12) build19+: state-driven приветствие на экране логина.
    // FirstTime — никогда не запускали приложение / нет last login.
    // Returning — был запуск, но не помним кого (logout + clear).
    // Personal — помним последнего юзера (login + name + phone).
    private val _greetingState = MutableStateFlow<GreetingState>(initialGreetingState())
    val greetingState: StateFlow<GreetingState> = _greetingState.asStateFlow()

    init {
        // Если есть last login — предзаполняем поле логина
        prefsManager.getLastLogin()?.let { lastLogin ->
            if (lastLogin.isNotBlank() && _login.value.isBlank()) {
                _login.value = lastLogin
            }
        }
        // Помечаем что приложение запускалось (next launch будет Returning)
        prefsManager.markLaunched()
    }

    private fun initialGreetingState(): GreetingState {
        val lastLogin = prefsManager.getLastLogin()
        val lastName = prefsManager.getLastUserName()
        val lastPhone = prefsManager.getLastUserPhoneDisplay()
        return when {
            !prefsManager.hasLaunchedBefore() -> GreetingState.FirstTime
            !lastLogin.isNullOrBlank() && !lastName.isNullOrBlank() ->
                GreetingState.Personal(
                    login = lastLogin,
                    fullName = lastName,
                    firstName = Greeting.firstName(lastName),
                    initials = Greeting.initials(lastName),
                    phoneDisplay = lastPhone,
                )
            else -> GreetingState.Returning
        }
    }

    /** «Сменить» на personal-баннере → переходим в anonymous Returning + чистим поле. */
    fun forgetLastLogin() {
        prefsManager.clearLastLoginContext()
        _login.value = ""
        _password.value = ""
        _greetingState.value = GreetingState.Returning
    }

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

    // FIX(2026-05-12) build19+: Yandex OAuth обработка (паттерн из AuthPhoneViewModel).
    fun onYandexAuthResult(yandexToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authWithYandexUseCase(yandexToken)
            _isLoading.value = false
            result.onSuccess { authResult ->
                if (authResult.isNew) {
                    _navigationEvent.emit(LoginNavigationEvent.NavigateToTerms)
                } else {
                    loadProfileAndNavigate()
                }
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Ошибка авторизации через Яндекс"
            }
        }
    }

    fun onYandexAuthError(message: String) {
        _errorMessage.value = message
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
                        // FIX(2026-05-12) build19+: save для personalized greeting на след. логине
                        prefsManager.setLastLoginContext(
                            login = _login.value.trim().ifBlank { user.phone ?: "" },
                            name = user.fullName ?: user.firstName ?: user.phone,
                            phoneDisplay = user.phone,
                        )

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

/** FIX(2026-05-12) build19+: state для приветствия на экране логина. */
sealed class GreetingState {
    object FirstTime : GreetingState()
    object Returning : GreetingState()
    data class Personal(
        val login: String,
        val fullName: String,
        val firstName: String,
        val initials: String,
        val phoneDisplay: String?,
    ) : GreetingState()
}
