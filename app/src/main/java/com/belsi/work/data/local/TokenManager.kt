package com.belsi.work.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер токенов — делегирует хранение в PrefsManager (EncryptedSharedPreferences).
 * Все данные авторизации хранятся в зашифрованном виде.
 */
@Singleton
class TokenManager @Inject constructor(
    private val prefsManager: PrefsManager
) {
    private val _tokenFlow = MutableStateFlow(prefsManager.getToken())

    /**
     * Сохранить токен и данные пользователя после успешной авторизации
     */
    suspend fun saveAuthData(token: String, phone: String, userId: String? = null, role: String? = null) {
        prefsManager.saveToken(token)
        prefsManager.setUserPhone(phone)
        userId?.let { prefsManager.setUserId(it) }
        role?.let { prefsManager.setUserRole(it) }
        _tokenFlow.value = token
    }

    /**
     * Получить токен (suspend для обратной совместимости)
     */
    suspend fun getToken(): String? {
        return prefsManager.getToken()
    }

    /**
     * Получить токен синхронно (для OkHttp interceptor)
     */
    fun getTokenSync(): String? {
        return prefsManager.getToken()
    }

    /**
     * Получить токен как Flow (для реактивной проверки авторизации)
     */
    fun getTokenFlow(): Flow<String?> {
        return _tokenFlow.asStateFlow()
    }

    /**
     * Получить телефон пользователя
     */
    suspend fun getPhone(): String? {
        return prefsManager.getUserPhone()
    }

    /**
     * Очистить все данные авторизации (выход / 401)
     */
    suspend fun clearAuthData() {
        prefsManager.clearToken()
        prefsManager.clearAuthExtras()
        _tokenFlow.value = null
    }

    /**
     * Проверка, авторизован ли пользователь
     */
    suspend fun isAuthorized(): Boolean {
        return getToken() != null
    }

    /**
     * Получить ID пользователя
     */
    suspend fun getUserId(): String? {
        return prefsManager.getUserId()
    }

    /**
     * Получить роль пользователя
     */
    suspend fun getUserRole(): String? {
        return prefsManager.getUserRole()
    }

    /**
     * Сохранить user ID и роль
     */
    suspend fun saveUserInfo(userId: String, role: String) {
        prefsManager.setUserId(userId)
        prefsManager.setUserRole(role)
    }
}
