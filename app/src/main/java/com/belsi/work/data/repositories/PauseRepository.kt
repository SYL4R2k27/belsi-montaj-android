package com.belsi.work.data.repositories

import com.belsi.work.data.offline.OfflineQueueRepository
import com.belsi.work.data.offline.OfflineQueuedException
import com.belsi.work.data.offline.PendingAction
import com.belsi.work.data.remote.api.*
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface PauseRepository {
    suspend fun startPause(reason: String? = null): Result<PauseResponse>
    suspend fun endPause(): Result<PauseResponse>
    suspend fun startIdle(reason: String): Result<PauseResponse>
    suspend fun endIdle(): Result<PauseResponse>
    suspend fun getCurrentPause(): Result<CurrentPauseResponse>
    suspend fun getShiftPauses(shiftId: String): Result<List<PauseResponse>>
}

@Singleton
class PauseRepositoryImpl @Inject constructor(
    private val pauseApi: PauseApi,
    // FIX(2026-05-05): offline-очередь для шатдаунов мобильного интернета
    private val offlineQueue: OfflineQueueRepository,
) : PauseRepository {

    override suspend fun startPause(reason: String?): Result<PauseResponse> {
        return safeApiCallWithFallback("начала паузы",
            call = { pauseApi.startPause(StartPauseRequest(reason)) },
            offlineAction = { PendingAction.StartPause(shiftId = "current") },
        )
    }

    override suspend fun endPause(): Result<PauseResponse> {
        return safeApiCallWithFallback("завершения паузы",
            call = { pauseApi.endPause() },
            offlineAction = { PendingAction.EndPause(shiftId = "current") },
        )
    }

    override suspend fun startIdle(reason: String): Result<PauseResponse> {
        return safeApiCallWithFallback("начала простоя",
            call = { pauseApi.startIdle(StartIdleRequest(reason)) },
            offlineAction = { PendingAction.StartIdle(shiftId = "current", reason = reason) },
        )
    }

    override suspend fun endIdle(): Result<PauseResponse> {
        return safeApiCallWithFallback("завершения простоя",
            call = { pauseApi.endIdle() },
            offlineAction = { PendingAction.EndIdle(shiftId = "current") },
        )
    }

    override suspend fun getCurrentPause(): Result<CurrentPauseResponse> {
        return safeApiCall("текущей паузы") { pauseApi.getCurrentPause() }
    }

    override suspend fun getShiftPauses(shiftId: String): Result<List<PauseResponse>> {
        return safeApiCall("списка пауз") { pauseApi.getShiftPauses(shiftId) }
    }

    private suspend fun <T> safeApiCall(context: String, call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Result.failure(Exception("Ошибка $context: ${response.code()} $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка $context: ${e.message}", e))
        }
    }

    /**
     * FIX(2026-05-05): variant safeApiCall с offline-очередью.
     * При IOException (сеть упала / шатдаун) → действие в очередь, return особый Result.failure
     * с OfflineQueuedException — UI может показать «📤 будет отправлено» вместо «Ошибка сети».
     */
    private suspend fun <T> safeApiCallWithFallback(
        context: String,
        call: suspend () -> Response<T>,
        offlineAction: () -> PendingAction,
    ): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Result.failure(Exception("Ошибка $context: ${response.code()} $errorBody"))
            }
        } catch (e: IOException) {
            // Сеть упала — в очередь
            offlineQueue.enqueue(offlineAction())
            Result.failure(OfflineQueuedException("$context: будет отправлено когда появится связь", e))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка $context: ${e.message}", e))
        }
    }
}
