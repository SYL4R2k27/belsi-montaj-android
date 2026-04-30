package com.belsi.work.data.repositories

import android.util.Log
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.local.TokenManager
import com.belsi.work.data.models.User
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.remote.api.AuthApi
import com.belsi.work.data.remote.api.ChangePasswordRequest
import com.belsi.work.data.remote.api.LoginRequest
import com.belsi.work.data.remote.api.UserApi
import com.belsi.work.data.remote.dto.auth.SendOtpRequest
import com.belsi.work.data.remote.dto.auth.VerifyOtpRequest
import com.belsi.work.data.remote.dto.auth.YandexAuthRequest
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий авторизации для BELSI API
 *
 * API эндпоинты:
 * - POST /auth/phone - отправка OTP
 * - POST /auth/verify - верификация OTP и получение токена
 */
interface AuthRepository {
    suspend fun sendOtp(phone: String): Result<Unit>
    suspend fun verifyOtp(phone: String, code: String): Result<AuthResult>
    suspend fun authWithYandex(yandexToken: String): Result<AuthResult>
    suspend fun login(login: String, password: String): Result<AuthResult>
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<String>
    suspend fun logout(): Result<Unit>
    suspend fun isAuthorized(): Boolean
}

/**
 * Результат успешной авторизации
 */
data class AuthResult(
    val token: String,
    val phone: String,
    val isNew: Boolean = false,
    val role: String = "installer"
)

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val tokenManager: TokenManager,
    private val prefsManager: PrefsManager,
    private val pushRepositoryLazy: Lazy<PushRepository>
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
    }

    private val pushRepository: PushRepository
        get() = pushRepositoryLazy.get()

    /**
     * После успешной авторизации подтягиваем userId из GET /user/me
     * и сохраняем в encrypted storage. Ошибка не ломает auth flow.
     */
    private suspend fun fetchAndSaveUserId() {
        try {
            val response = userApi.getProfile()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                val userId = user.id.toString()
                prefsManager.setUserId(userId)
                Log.d(TAG, "userId saved: $userId")
            } else {
                Log.w(TAG, "Failed to fetch userId: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch userId: ${e.message}")
        }
    }

    /**
     * Отправить OTP код на телефон
     * POST /auth/phone
     */
    override suspend fun sendOtp(phone: String): Result<Unit> {
        return try {
            val response = authApi.sendOtp(SendOtpRequest(phone))

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMsg = parseErrorMessage(response.code(), response.errorBody()?.string())
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети: ${e.message}", e))
        }
    }

    /**
     * Верифицировать OTP код и получить токен
     * POST /auth/verify
     *
     * Response: { "token": "...", "phone": "+79991234567" }
     */
    override suspend fun verifyOtp(phone: String, code: String): Result<AuthResult> {
        return try {
            val response = authApi.verifyOtp(VerifyOtpRequest(phone, code))

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                // Сохраняем токен, телефон и роль (всё в encrypted storage)
                tokenManager.saveAuthData(
                    token = body.token,
                    phone = body.phone,
                    role = body.role
                )

                // Подтягиваем и сохраняем userId из /user/me
                fetchAndSaveUserId()

                // Определяем роль из ответа сервера
                val userRole = when (body.role.lowercase()) {
                    "foreman" -> UserRole.FOREMAN
                    "coordinator" -> UserRole.COORDINATOR
                    "curator" -> UserRole.CURATOR
                    else -> UserRole.INSTALLER
                }

                val tempUser = User(
                    phone = body.phone,
                    name = body.phone,
                    role = userRole
                )
                prefsManager.saveUser(tempUser)

                // Регистрируем FCM токен на сервере для push-уведомлений
                pushRepository.registerFcmToken()

                Result.success(AuthResult(
                    token = body.token,
                    phone = body.phone,
                    isNew = body.isNew,
                    role = body.role
                ))
            } else {
                val errorMsg = parseErrorMessage(response.code(), response.errorBody()?.string())
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети: ${e.message}", e))
        }
    }

    /**
     * Авторизация через Yandex OAuth
     * POST /auth/yandex/callback
     */
    override suspend fun authWithYandex(yandexToken: String): Result<AuthResult> {
        return try {
            val response = authApi.authWithYandex(YandexAuthRequest(yandexToken))

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                tokenManager.saveAuthData(
                    token = body.token,
                    phone = body.phone,
                    role = body.role
                )

                // Подтягиваем и сохраняем userId из /user/me
                fetchAndSaveUserId()

                val userRole = when (body.role.lowercase()) {
                    "foreman" -> UserRole.FOREMAN
                    "coordinator" -> UserRole.COORDINATOR
                    "curator" -> UserRole.CURATOR
                    else -> UserRole.INSTALLER
                }

                val tempUser = User(
                    phone = body.phone,
                    name = body.phone,
                    role = userRole
                )
                prefsManager.saveUser(tempUser)

                pushRepository.registerFcmToken()

                Result.success(AuthResult(
                    token = body.token,
                    phone = body.phone,
                    isNew = body.isNew,
                    role = body.role
                ))
            } else {
                val errorMsg = parseErrorMessage(response.code(), response.errorBody()?.string())
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети: ${e.message}", e))
        }
    }

    /**
     * Авторизация по логину/паролю
     * POST /auth/login
     */
    override suspend fun login(login: String, password: String): Result<AuthResult> {
        return try {
            val response = authApi.login(LoginRequest(login, password))

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val user = body.user

                // Сохраняем токен, телефон и роль (всё в encrypted storage)
                tokenManager.saveAuthData(
                    token = body.token,
                    phone = user.phone,
                    role = user.role
                )

                // Сохраняем userId сразу из ответа (не нужен отдельный /user/me)
                prefsManager.setUserId(user.id)

                // Определяем роль из ответа сервера
                val userRole = when (user.role.lowercase()) {
                    "foreman" -> UserRole.FOREMAN
                    "coordinator" -> UserRole.COORDINATOR
                    "curator" -> UserRole.CURATOR
                    else -> UserRole.INSTALLER
                }

                val tempUser = User(
                    phone = user.phone,
                    name = user.fullName ?: user.firstName ?: user.phone,
                    role = userRole
                )
                prefsManager.saveUser(tempUser)

                // Регистрируем FCM токен на сервере для push-уведомлений
                pushRepository.registerFcmToken()

                Result.success(AuthResult(
                    token = body.token,
                    phone = user.phone,
                    isNew = false,
                    role = user.role
                ))
            } else {
                val errorMsg = parseErrorMessage(response.code(), response.errorBody()?.string())
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети: ${e.message}", e))
        }
    }

    /**
     * Смена пароля
     * POST /auth/change-password
     */
    override suspend fun changePassword(oldPassword: String, newPassword: String): Result<String> {
        return try {
            val response = authApi.changePassword(ChangePasswordRequest(oldPassword, newPassword))

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message)
            } else {
                val errorMsg = parseErrorMessage(response.code(), response.errorBody()?.string())
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка смены пароля: ${e.message}", e))
        }
    }

    /**
     * Выход (очистка токена)
     */
    override suspend fun logout(): Result<Unit> {
        return try {
            // Удаляем FCM токен с сервера
            pushRepository.unregisterFcmToken()
            // Очищаем локальные данные (token + auth extras через encrypted storage)
            tokenManager.clearAuthData()
            prefsManager.clearUser()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Проверка, авторизован ли пользователь (есть ли токен)
     */
    override suspend fun isAuthorized(): Boolean {
        return tokenManager.isAuthorized()
    }

    /**
     * Парсинг ошибок от сервера
     */
    private fun parseErrorMessage(code: Int, errorBody: String?): String {
        return when (code) {
            400 -> "Неверный формат данных"
            401 -> "Неверный логин или пароль"
            404 -> "Пользователь не найден"
            429 -> "Слишком много попыток, попробуйте позже"
            500, 502, 503 -> "Ошибка сервера, попробуйте позже"
            else -> errorBody ?: "Неизвестная ошибка"
        }
    }
}
