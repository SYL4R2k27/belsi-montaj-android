package com.belsi.work.presentation.screens.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.BuildConfig
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsManager: PrefsManager,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSettingsUiState())
    val uiState: StateFlow<DebugSettingsUiState> = _uiState.asStateFlow()

    init {
        loadDebugInfo()
    }

    private fun loadDebugInfo() {
        viewModelScope.launch {
            val user = prefsManager.getUser()
            val token = tokenManager.getToken()

            _uiState.update {
                it.copy(
                    appVersion = BuildConfig.VERSION_NAME,
                    isDebugBuild = BuildConfig.DEBUG,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    accessToken = token,
                    userId = user?.id?.toString(),
                    userRole = user?.role?.name,
                    httpLoggingEnabled = false, // Default value, can be made persistent later
                    verboseLogsEnabled = false  // Default value, can be made persistent later
                )
            }
        }
    }

    fun toggleHttpLogging(enabled: Boolean) {
        _uiState.update { it.copy(httpLoggingEnabled = enabled) }
        showMessage("HTTP логирование ${if (enabled) "включено" else "выключено"}")
    }

    fun toggleVerboseLogs(enabled: Boolean) {
        _uiState.update { it.copy(verboseLogsEnabled = enabled) }
        showMessage("Подробные логи ${if (enabled) "включены" else "выключены"}")
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                // Очистка кэша приложения
                context.cacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()

                showMessage("Кэш очищен")
            } catch (e: Exception) {
                showMessage("Ошибка очистки кэша: ${e.message}")
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                // Очистка всех данных кроме токена
                prefsManager.clearAll()
                tokenManager.clearAuthData()

                // Очистка кэша
                context.cacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()

                loadDebugInfo()
                showMessage("Все данные очищены")
            } catch (e: Exception) {
                showMessage("Ошибка очистки данных: ${e.message}")
            }
        }
    }

    fun forceLogout() {
        viewModelScope.launch {
            try {
                // Полная очистка
                prefsManager.clearAll()
                tokenManager.clearAuthData()
                context.cacheDir.deleteRecursively()

                showMessage("Выполнен выход из системы")
            } catch (e: Exception) {
                showMessage("Ошибка выхода: ${e.message}")
            }
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Debug Info", text)
        clipboard.setPrimaryClip(clip)
        showMessage("Скопировано в буфер обмена")
    }

    fun loadMockShift() {
        viewModelScope.launch {
            // TODO: Создать мок смены для тестирования
            showMessage("Мок смены загружены (не реализовано)")
        }
    }

    fun loadMockPhotos() {
        viewModelScope.launch {
            // TODO: Создать мок фотографии для тестирования
            showMessage("Мок фото загружены (не реализовано)")
        }
    }

    fun loadMockChat() {
        viewModelScope.launch {
            // TODO: Создать мок чат для тестирования
            showMessage("Мок чат загружен (не реализовано)")
        }
    }

    fun incrementApiCall() {
        _uiState.update { it.copy(totalApiCalls = it.totalApiCalls + 1) }
    }

    fun incrementApiError() {
        _uiState.update { it.copy(totalApiErrors = it.totalApiErrors + 1) }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

data class DebugSettingsUiState(
    // Информация
    val appVersion: String = "1.0.0",
    val isDebugBuild: Boolean = true,
    val apiBaseUrl: String = "",

    // Аутентификация
    val accessToken: String? = null,
    val userId: String? = null,
    val userRole: String? = null,

    // Настройки
    val httpLoggingEnabled: Boolean = false,
    val verboseLogsEnabled: Boolean = false,

    // Статистика
    val totalApiCalls: Int = 0,
    val totalApiErrors: Int = 0,

    // UI
    val message: String? = null
)
