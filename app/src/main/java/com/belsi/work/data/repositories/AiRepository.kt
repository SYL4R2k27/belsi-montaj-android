package com.belsi.work.data.repositories

import com.belsi.work.data.models.*
import com.belsi.work.data.remote.api.AiApi
import com.belsi.work.data.remote.error.parseApiError
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-10) BELSI 1.3.0: Repository для AI endpoints.
 * Все методы возвращают Result<T> в стиле проекта.
 */
interface AiRepository {
    suspend fun getDailySummary(date: String? = null): Result<AiDailySummaryResponse>
    suspend fun searchPhotos(query: String): Result<AiPhotoSearchResponse>
    suspend fun verifyIdle(pauseId: String): Result<AiIdleVerifyResponse>
    suspend fun transcribeVoice(
        audioBytes: ByteArray,
        filename: String = "audio.mp3",
        mime: String = "audio/mpeg",
        language: String = "ru",
        context: String = "general",
    ): Result<VoiceTranscribeResponse>
    suspend fun suggestReplies(request: SmartReplyRequest): Result<SmartReplyResponse>
    suspend fun triageTicket(ticketId: String): Result<TriageTicketResponse>
    suspend fun getStockForecast(facilityId: String): Result<StockForecastResponse>
}

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val api: AiApi,
    private val json: Json,
) : AiRepository {

    private suspend fun <T> safeCall(op: String, call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val msg = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception("$op: $msg"))
            }
        } catch (e: Exception) {
            android.util.Log.e("AiRepository", op, e)
            Result.failure(Exception("$op: ${e.message}", e))
        }
    }

    override suspend fun getDailySummary(date: String?): Result<AiDailySummaryResponse> =
        safeCall("getDailySummary") { api.getDailySummary(date) }

    override suspend fun searchPhotos(query: String): Result<AiPhotoSearchResponse> =
        safeCall("searchPhotos") { api.searchPhotos(AiPhotoSearchRequest(query)) }

    override suspend fun verifyIdle(pauseId: String): Result<AiIdleVerifyResponse> =
        safeCall("verifyIdle") { api.verifyIdle(pauseId) }

    override suspend fun transcribeVoice(
        audioBytes: ByteArray,
        filename: String,
        mime: String,
        language: String,
        context: String,
    ): Result<VoiceTranscribeResponse> {
        return try {
            val audioBody = audioBytes.toRequestBody(mime.toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData("audio", filename, audioBody)
            val langPart = language.toRequestBody("text/plain".toMediaTypeOrNull())
            val ctxPart = context.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.transcribeVoice(audioPart, langPart, ctxPart)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val msg = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception("transcribeVoice: $msg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("transcribeVoice: ${e.message}", e))
        }
    }

    override suspend fun suggestReplies(request: SmartReplyRequest): Result<SmartReplyResponse> =
        safeCall("suggestReplies") { api.suggestReplies(request) }

    override suspend fun triageTicket(ticketId: String): Result<TriageTicketResponse> =
        safeCall("triageTicket") { api.triageTicket(ticketId) }

    override suspend fun getStockForecast(facilityId: String): Result<StockForecastResponse> =
        safeCall("getStockForecast") { api.getStockForecast(facilityId) }
}
