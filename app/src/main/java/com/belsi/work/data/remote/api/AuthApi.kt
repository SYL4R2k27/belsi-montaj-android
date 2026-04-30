package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.auth.SendOtpRequest
import com.belsi.work.data.remote.dto.auth.SendOtpResponse
import com.belsi.work.data.remote.dto.auth.VerifyOtpRequest
import com.belsi.work.data.remote.dto.auth.VerifyOtpResponse
import com.belsi.work.data.remote.dto.auth.YandexAuthRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * BELSI API - Авторизация
 * Базовый URL: https://api.belsi.ru
 *
 * Бэкенд: auth.py, auth_login.py, yandex_auth.py
 */
interface AuthApi {

    /**
     * Отправить OTP код на телефон
     * POST /auth/phone
     */
    @POST("auth/phone")
    suspend fun sendOtp(
        @Body request: SendOtpRequest
    ): Response<SendOtpResponse>

    /**
     * Верифицировать OTP код и получить токен
     * POST /auth/verify
     */
    @POST("auth/verify")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest
    ): Response<VerifyOtpResponse>

    /**
     * Авторизация через Yandex OAuth
     * POST /auth/yandex/callback
     */
    @POST("auth/yandex/callback")
    suspend fun authWithYandex(
        @Body request: YandexAuthRequest
    ): Response<VerifyOtpResponse>

    /**
     * Авторизация по логину/паролю
     * POST /auth/login
     * Бэкенд: auth_login.py
     */
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    /**
     * Смена пароля
     * POST /auth/change-password
     * Бэкенд: auth_login.py
     */
    @POST("auth/change-password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): Response<ChangePasswordResponse>

    /**
     * Установка пароля куратором для пользователя
     * POST /curator/set-password
     * Бэкенд: auth_login.py
     */
    @POST("curator/set-password")
    suspend fun curatorSetPassword(
        @Body request: CuratorSetPasswordRequest
    ): Response<ChangePasswordResponse>
}

// DTO для авторизации по логину/паролю
@Serializable
data class LoginRequest(
    val login: String,      // phone, email, or username
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: LoginUser
)

@Serializable
data class LoginUser(
    val id: String,
    val phone: String,
    val role: String = "installer",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("short_id") val shortId: String? = null,
    @SerialName("foreman_id") val foremanId: String? = null,
    val email: String? = null,
    val username: String? = null
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class CuratorSetPasswordRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class ChangePasswordResponse(
    val message: String
)
