package com.belsi.work.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

/**
 * BELSI API - Инвайты бригадира
 * Базовый URL: https://api.belsi.ru
 */
interface InviteApi {

    /**
     * Создать новый инвайт-код (только для role=foreman)
     * POST /foreman/invites
     *
     * Request: {} (пустой объект)
     * Response: { "id": "...", "code": "ABC123", "foreman_phone": "+7999...", ... }
     */
    @POST("foreman/invites")
    suspend fun createInvite(
        @Body request: CreateInviteRequest = CreateInviteRequest()
    ): Response<ForemanInviteResponse>

    /**
     * Получить список инвайтов бригадира
     * GET /foreman/invites
     *
     * Response: { "items": [ {...}, ... ] }
     */
    @GET("foreman/invites")
    suspend fun getForemanInvites(): Response<ForemanInvitesListResponse>

    /**
     * Монтажник активирует инвайт-код
     * POST /foreman/invites/redeem
     *
     * Request: { "code": "ABC123" }
     * Response: { "success": true, "foreman_name": "...", "message": "..." }
     */
    @POST("foreman/invites/redeem")
    suspend fun redeemInvite(
        @Body request: RedeemInviteRequest
    ): Response<RedeemInviteResponse>

    /**
     * Бригадир отменяет инвайт
     * POST /foreman/invites/cancel
     *
     * Request: { "code": "ABC123" }
     * Response: invite DTO
     */
    @POST("foreman/invites/cancel")
    suspend fun cancelInvite(
        @Body request: CancelInviteRequest
    ): Response<ForemanInviteResponse>
}

// Request DTOs
@Serializable
data class CreateInviteRequest(
    val dummy: String? = null  // Пустой объект для FastAPI
)

@Serializable
data class RedeemInviteRequest(
    val code: String
)

@Serializable
data class CancelInviteRequest(
    val code: String
)

// Response DTOs
@Serializable
data class ForemanInviteResponse(
    val id: String,
    val code: String,
    @SerialName("foreman_phone")
    val foremanPhone: String,
    @SerialName("installer_phone")
    val installerPhone: String? = null,
    val status: String,  // "active", "used", "cancelled"
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("used_at")
    val usedAt: String? = null
)

@Serializable
data class RedeemInviteResponse(
    val success: Boolean,
    @SerialName("foreman_name")
    val foremanName: String? = null,
    val message: String? = null
)

@Serializable
data class ForemanInvitesListResponse(
    val items: List<ForemanInviteResponse>
)
