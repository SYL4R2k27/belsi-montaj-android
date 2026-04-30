package com.belsi.work.data.remote.api

import com.belsi.work.data.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * BELSI API - Управление инструментами
 * Базовый URL: https://api.belsi.ru
 *
 * ТРЕБОВАНИЯ:
 * - Выдает инструмент: только бригадир (role=foreman)
 * - После выдачи: бригадир подтверждает в кабинете
 * - Куратор видит все движение инструментов
 * - Фото при возврате: обязательно
 * - Фото при выдаче: опционально
 * - Справочник: готовый список + возможность добавления
 * - Инструменты не привязаны к сменам
 * - Offline: первичная выдача offline, синхронизация при online
 */
interface ToolsApi {

    /**
     * Получить список всех инструментов (справочник)
     * GET /tools
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: foreman, curator
     */
    @GET("tools")
    suspend fun getTools(
        @Query("status") status: String? = null,
        @Query("category") category: String? = null
    ): Response<ToolsListResponse>

    /**
     * Получить мои активные инструменты (для монтажника)
     * GET /tools/my
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: installer
     * Backend фильтрует по installer_id из JWT token
     */
    @GET("tools/my")
    suspend fun getMyTools(): Response<ToolTransactionsResponse>

    /**
     * Получить инструменты команды (для бригадира)
     * GET /foreman/tools
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: foreman
     * Возвращает активные выдачи всех монтажников команды
     */
    @GET("foreman/tools")
    suspend fun getTeamTools(): Response<ToolTransactionsResponse>

    /**
     * Получить все транзакции инструментов (для куратора)
     * GET /curator/tools/transactions
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: curator
     * Куратор видит все движение инструментов
     */
    @GET("curator/tools/transactions")
    suspend fun getAllTransactions(
        @Query("status") status: String? = null,
        @Query("installer_id") installerId: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<ToolTransactionsResponse>

    /**
     * Создать новый инструмент (добавить в справочник)
     * POST /tools
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: foreman, curator
     */
    @POST("tools")
    suspend fun createTool(
        @Body request: CreateToolRequest
    ): Response<Tool>

    /**
     * Выдать инструмент монтажнику
     * POST /tools/issue
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: foreman ИЛИ installer (для самовыдачи)
     * JWT token используется для issued_by
     *
     * Тело запроса:
     * {
     *   "tool_id": "UUID",
     *   "installer_id": "UUID",
     *   "comment": "Опциональный комментарий",
     *   "photo_url": "Опциональный URL фото (если загружено заранее)"
     * }
     *
     * ВАЖНО: Статус сразу "issued" (без подтверждения)
     * Installer может выдавать ТОЛЬКО свои инструменты ТОЛЬКО себе
     */
    @POST("tools/issue")
    suspend fun issueTool(
        @Body request: IssueToolRequest
    ): Response<ToolTransaction>

    /**
     * Загрузить фото инструмента (универсальный endpoint)
     * POST /tools/photos
     *
     * Требует: Authorization: Bearer <token>
     * Content-Type: multipart/form-data
     *
     * Поля:
     * - photo: binary JPEG
     *
     * Возвращает: { "photoUrl": "https://..." }
     * Этот URL используется в issue_photo_url или return_photo_url
     */
    @Multipart
    @POST("tools/photos")
    suspend fun uploadToolPhoto(
        @Part photo: MultipartBody.Part
    ): Response<ToolPhotoUploadResponse>

    /**
     * Вернуть инструмент
     * POST /tools/transactions/{id}/return
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: foreman (принимает возврат)
     * JWT token используется для returned_to
     *
     * Тело запроса:
     * {
     *   "return_condition": "good" | "damaged" | "broken",
     *   "return_comment": "Опциональный комментарий",
     *   "return_photo_url": "URL фото (загруженного через POST /tools/photos)"
     * }
     *
     * ВАЖНО: Фото при возврате ОБЯЗАТЕЛЬНО
     * Сначала загрузить фото через uploadToolPhoto(), получить URL,
     * затем передать этот URL в return_photo_url
     */
    @POST("tools/transactions/{id}/return")
    suspend fun returnTool(
        @Path("id") transactionId: String,
        @Body request: ReturnToolRequest
    ): Response<ToolTransaction>

    /**
     * Получить историю транзакций конкретного инструмента
     * GET /tools/{id}/transactions
     *
     * Требует: Authorization: Bearer <token>
     */
    @GET("tools/{id}/transactions")
    suspend fun getToolTransactions(
        @Path("id") toolId: String
    ): Response<List<ToolTransaction>>

    /**
     * Получить историю транзакций монтажника
     * GET /tools/transactions/installer/{id}
     *
     * Требует: Authorization: Bearer <token>
     * Доступ: foreman, curator
     */
    @GET("tools/transactions/installer/{id}")
    suspend fun getInstallerTransactions(
        @Path("id") installerId: String
    ): Response<List<ToolTransaction>>
}

/**
 * Ответ со списком инструментов
 */
@kotlinx.serialization.Serializable
data class ToolsListResponse(
    val items: List<Tool>
)

/**
 * Ответ со списком транзакций
 *
 * Поля page, totalPages, totalItems опциональны, так как backend
 * может возвращать только {"items":[]} без пагинации
 */
@kotlinx.serialization.Serializable
data class ToolTransactionsResponse(
    val items: List<ToolTransaction> = emptyList(),
    val page: Int? = null,
    @kotlinx.serialization.SerialName("total_pages")
    val totalPages: Int? = null,
    @kotlinx.serialization.SerialName("total_items")
    val totalItems: Int? = null
)

/**
 * Ответ после загрузки фото инструмента
 */
@kotlinx.serialization.Serializable
data class ToolPhotoUploadResponse(
    @kotlinx.serialization.SerialName("photoUrl")
    val photoUrl: String
)
