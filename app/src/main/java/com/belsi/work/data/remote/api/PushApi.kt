package com.belsi.work.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST

/**
 * API для управления push-уведомлениями (FCM)
 */
interface PushApi {

    /**
     * Регистрация FCM токена для текущего пользователя
     * POST /push/register
     */
    @POST("push/register")
    suspend fun registerFcmToken(
        @Body request: RegisterFcmTokenRequest
    ): Response<StatusResponse>

    /**
     * Удаление FCM токена (при выходе)
     * DELETE /push/unregister
     */
    @DELETE("push/unregister")
    suspend fun unregisterFcmToken(): Response<StatusResponse>
}

@Serializable
data class RegisterFcmTokenRequest(
    @SerialName("fcm_token") val fcmToken: String
)

@Serializable
data class StatusResponse(
    val status: String
)
