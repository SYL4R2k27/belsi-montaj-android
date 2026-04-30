package com.belsi.work.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * API для управления паузами и простоями смены
 * Бэкенд: shift_pauses.py
 */
interface PauseApi {

    /**
     * Начать паузу
     * POST /shift/pause/start
     */
    @POST("shift/pause/start")
    suspend fun startPause(
        @Body request: StartPauseRequest
    ): Response<PauseResponse>

    /**
     * Завершить паузу
     * POST /shift/pause/end
     */
    @POST("shift/pause/end")
    suspend fun endPause(): Response<PauseResponse>

    /**
     * Начать простой (idle)
     * POST /shift/idle/start
     */
    @POST("shift/idle/start")
    suspend fun startIdle(
        @Body request: StartIdleRequest
    ): Response<PauseResponse>

    /**
     * Завершить простой (idle)
     * POST /shift/idle/end
     */
    @POST("shift/idle/end")
    suspend fun endIdle(): Response<PauseResponse>

    /**
     * Получить текущую паузу/простой
     * GET /shift/pause/current
     */
    @GET("shift/pause/current")
    suspend fun getCurrentPause(): Response<CurrentPauseResponse>

    /**
     * Получить все паузы смены
     * GET /shift/pauses?shift_id=...
     */
    @GET("shift/pauses")
    suspend fun getShiftPauses(
        @Query("shift_id") shiftId: String
    ): Response<List<PauseResponse>>
}

@Serializable
data class StartPauseRequest(
    val reason: String? = null
)

@Serializable
data class StartIdleRequest(
    val reason: String
)

@Serializable
data class PauseResponse(
    val id: String,
    @SerialName("shift_id") val shiftId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    val reason: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null
)

@Serializable
data class CurrentPauseResponse(
    val pause: PauseResponse? = null,
    @SerialName("on_pause") val onPause: Boolean = false
)
