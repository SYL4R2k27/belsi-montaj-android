package com.belsi.work.presentation.screens.auth.otp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.repositories.UserRepository
import com.belsi.work.domain.usecases.auth.SendOTPUseCase
import com.belsi.work.domain.usecases.auth.VerifyOTPUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OTPViewModel @Inject constructor(
    private val verifyOTPUseCase: VerifyOTPUseCase,
    private val sendOTPUseCase: SendOTPUseCase,
    private val userRepository: UserRepository,
    private val prefsManager: PrefsManager
) : ViewModel() {

    private val _otpCode = MutableStateFlow("")
    val otpCode: StateFlow<String> = _otpCode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val _remainingTime = MutableStateFlow(60)
    val remainingTime: StateFlow<Int> = _remainingTime.asStateFlow()

    private val _canResend = MutableStateFlow(false)
    val canResend: StateFlow<Boolean> = _canResend.asStateFlow()

    private var timerJob: Job? = null
    private var savedPhone: String = ""

    init {
        startTimer()
    }

    fun setPhone(phone: String) {
        savedPhone = phone
    }

    fun onOTPChanged(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _otpCode.value = value
            _errorMessage.value = null

            // Auto-verify when 6 digits entered
            if (value.length == 6 && savedPhone.isNotEmpty()) {
                // Small delay for UX
                viewModelScope.launch {
                    delay(300)
                    verifyOTP(savedPhone)
                }
            }
        }
    }

    fun verifyOTP(phone: String) {
        if (_otpCode.value.length != 6) {
            _errorMessage.value = "Введите 6 цифр кода"
            return
        }

        val phoneToUse = phone.ifEmpty { savedPhone }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = verifyOTPUseCase(phoneToUse, _otpCode.value)

            _isLoading.value = false

            result.onSuccess { authResult ->
                // Авторизация успешна, токен уже сохранен в DataStore
                if (authResult.isNew) {
                    // Новый пользователь — онбординг: Terms → RoleSelect → Instructions → Main
                    _navigationEvent.emit(NavigationEvent.NavigateToTerms(authResult.phone))
                } else {
                    // Существующий пользователь — загружаем профиль и сразу на главный экран
                    loadProfileAndNavigate()
                }
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Неверный код"
                _otpCode.value = "" // Clear code on error
            }
        }
    }
    
    fun resendOTP(phone: String) {
        if (!_canResend.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = sendOTPUseCase(phone)
            
            _isLoading.value = false
            
            result.onSuccess {
                _remainingTime.value = 60
                _canResend.value = false
                _otpCode.value = ""
                startTimer()
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Ошибка отправки кода"
            }
        }
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_remainingTime.value > 0) {
                delay(1000)
                _remainingTime.value -= 1
            }
            _canResend.value = true
        }
    }
    
    /**
     * Загрузить профиль с сервера и перейти на главный экран по роли.
     * Для существующих пользователей — пропускаем весь онбординг.
     */
    private fun loadProfileAndNavigate() {
        viewModelScope.launch {
            try {
                // Загружаем профиль с сервера, чтобы получить актуальную роль
                userRepository.getProfile()
                    .onSuccess { user ->
                        // Помечаем онбординг как пройденный
                        prefsManager.setTermsAccepted(true)
                        prefsManager.setOnboardingCompleted(true)

                        val event = when (user.role) {
                            UserRole.FOREMAN -> NavigationEvent.NavigateToForemanMain
                            UserRole.COORDINATOR -> NavigationEvent.NavigateToCoordinatorMain
                            UserRole.CURATOR -> NavigationEvent.NavigateToCuratorMain
                            else -> NavigationEvent.NavigateToMain
                        }
                        _navigationEvent.emit(event)
                    }
                    .onFailure {
                        // Если не удалось загрузить профиль — переходим по роли из PrefsManager
                        prefsManager.setTermsAccepted(true)
                        prefsManager.setOnboardingCompleted(true)

                        val user = prefsManager.getUser()
                        val event = when (user?.role) {
                            UserRole.FOREMAN -> NavigationEvent.NavigateToForemanMain
                            UserRole.COORDINATOR -> NavigationEvent.NavigateToCoordinatorMain
                            UserRole.CURATOR -> NavigationEvent.NavigateToCuratorMain
                            else -> NavigationEvent.NavigateToMain
                        }
                        _navigationEvent.emit(event)
                    }
            } catch (e: Exception) {
                // Fallback — на экран монтажника
                prefsManager.setTermsAccepted(true)
                prefsManager.setOnboardingCompleted(true)
                _navigationEvent.emit(NavigationEvent.NavigateToMain)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

sealed class NavigationEvent {
    data class NavigateToTerms(val phone: String) : NavigationEvent()
    object NavigateToMain : NavigationEvent()
    object NavigateToForemanMain : NavigationEvent()
    object NavigateToCoordinatorMain : NavigationEvent()
    object NavigateToCuratorMain : NavigationEvent()
}
