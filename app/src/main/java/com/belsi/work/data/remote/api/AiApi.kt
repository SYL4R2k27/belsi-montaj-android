package com.belsi.work.data.remote.api

import com.belsi.work.data.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * FIX(2026-05-10) BELSI 1.3.0: API для AI endpoints (через BELSI backend → XeroCode).
 *
 * Все вызовы идут на api.belsi.ru/... — BELSI backend проксирует в XeroCode.
 * Если XeroCode недоступен — backend возвращает 503 или fallback-данные.
 */
interface AiApi {

    /** Ежедневная сводка для куратора (sync, кэш 1 час). */
    @POST("curator/ai-daily-summary")
    suspend fun getDailySummary(
        @Query("date") date: String? = null,
    ): Response<AiDailySummaryResponse>

    /** NLP-поиск фото (sync). */
    @POST("curator/ai-photo-search")
    suspend fun searchPhotos(
        @Body request: AiPhotoSearchRequest,
    ): Response<AiPhotoSearchResponse>

    /** Проверить причину простоя по последнему фото (sync). */
    @POST("shift/ai-verify-idle/{pauseId}")
    suspend fun verifyIdle(
        @Path("pauseId") pauseId: String,
    ): Response<AiIdleVerifyResponse>

    /** Транскрипция аудио (sync, multipart). */
    @Multipart
    @POST("shift/voice/transcribe")
    suspend fun transcribeVoice(
        @Part audio: MultipartBody.Part,
        @Part("language") language: RequestBody,
        @Part("context") context: RequestBody,
    ): Response<VoiceTranscribeResponse>

    /** Подсказки ответа в чате (sync). */
    @POST("messenger/ai-suggest-replies")
    suspend fun suggestReplies(
        @Body request: SmartReplyRequest,
    ): Response<SmartReplyResponse>

    /** Авто-категоризация support-тикета. */
    @POST("support/ai-triage/{ticketId}")
    suspend fun triageTicket(
        @Path("ticketId") ticketId: String,
    ): Response<TriageTicketResponse>

    /** Прогноз исчерпания материалов (sync, кэш 6 часов). */
    @GET("production/materials/ai-forecast")
    suspend fun getStockForecast(
        @Query("facility_id") facilityId: String,
    ): Response<StockForecastResponse>
}
