package com.belsi.work.presentation.screens.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.SavedAccount
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

    // FIX(2026-05-22): мульти-аккаунт. Список сохранённых профилей + флаг авто-открытия
    // переключателя (выставляется из Settings → «Сменить аккаунт»).
    private val _savedAccounts = MutableStateFlow(prefsManager.getSavedAccounts())
    val savedAccounts: StateFlow<List<SavedAccount>> = _savedAccounts.asStateFlow()

    private val _openSwitcher = MutableStateFlow(false)
    val openSwitcher: StateFlow<Boolean> = _openSwitcher.asStateFlow()

    init {
        // Если есть last login — предзаполняем поле логина
        prefsManager.getLastLogin()?.let { lastLogin ->
            if (lastLogin.isNotBlank() && _login.value.isBlank()) {
                _login.value = lastLogin
            }
        }
        // Помечаем что приложение запускалось (next launch будет Returning)
        prefsManager.markLaunched()
        // Settings → «Сменить аккаунт» поставил флаг → откроем переключатель сразу.
        if (prefsManager.getFlag("open_switcher_on_login")) {
            prefsManager.setFlag("open_switcher_on_login", false)
            if (prefsManager.getSavedAccounts().size > 1) _openSwitcher.value = true
        }
    }

    fun consumeOpenSwitcher() { _openSwitcher.value = false }

    /** Тап по сохранённому профилю → предзаполняем логин, фокус на пароль. */
    fun selectAccount(acc: SavedAccount) {
        _login.value = acc.login
        _password.value = ""
        _errorMessage.value = null
        _greetingState.value = GreetingState.Personal(
            login = acc.login,
            fullName = acc.name,
            firstName = Greeting.firstName(acc.name),
            initials = Greeting.initials(acc.name),
            phoneDisplay = acc.phoneDisplay,
        )
    }

    fun removeAccount(login: String) {
        prefsManager.removeSavedAccount(login)
        _savedAccounts.value = prefsManager.getSavedAccounts()
        // Если удалили текущий personal-профиль — сбрасываем поле и приветствие.
        val cur = _greetingState.value as? GreetingState.Personal
        if (cur != null && cur.login.equals(login, ignoreCase = true)) {
            _login.value = ""
            _password.value = ""
            _greetingState.value = if (_savedAccounts.value.isEmpty()) GreetingState.Returning
                                   else GreetingState.Returning
        }
    }

    /** «Добавить аккаунт» → чистый ввод нового логина/пароля. */
    fun addAccount() {
        _login.value = ""
        _password.value = ""
        _errorMessage.value = null
        _greetingState.value = GreetingState.Returning
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
                        val loginUsed = _login.value.trim().ifBlank { user.phone ?: "" }
                        val displayName = user.fullName ?: user.firstName ?: user.phone ?: loginUsed
                        prefsManager.setLastLoginContext(
                            login = loginUsed,
                            name = displayName,
                            phoneDisplay = user.phone,
                        )
                        // FIX(2026-05-22): сохраняем профиль в мульти-аккаунт (до 5).
                        prefsManager.upsertSavedAccount(
                            SavedAccount(
                                login = loginUsed,
                                name = displayName,
                                phoneDisplay = user.phone,
                                role = user.role?.name?.lowercase(),
                            )
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
                        // FIX(2026-05-22): сохраняем профиль даже если getProfile упал
                        // (берём кешированного user) — иначе аккаунт мог не попасть в список.
                        val lg = _login.value.trim().ifBlank { user?.phone ?: "" }
                        if (lg.isNotBlank()) {
                            prefsManager.upsertSavedAccount(
                                SavedAccount(
                                    login = lg,
                                    name = user?.fullName ?: user?.firstName ?: user?.phone ?: lg,
                                    phoneDisplay = user?.phone,
                                    role = user?.role?.name?.lowercase(),
                                )
                            )
                        }
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
