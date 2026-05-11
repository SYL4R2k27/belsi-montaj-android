package com.belsi.work.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    private val _logoutSuccess = MutableStateFlow(false)
    val logoutSuccess: StateFlow<Boolean> = _logoutSuccess.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    /**
     * Выход из аккаунта
     */
    fun logout() {
        viewModelScope.launch {
            _isLoggingOut.value = true
            try {
                // Очищаем все данные авторизации
                tokenManager.clearAuthData()
                _logoutSuccess.value = true
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error during logout", e)
            } finally {
                _isLoggingOut.value = false
            }
        }
    }

    fun resetLogoutSuccess() {
        _logoutSuccess.value = false
    }
}
