package com.belsi.work.data.repositories

import com.belsi.work.data.local.database.dao.ShiftDao
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
    // FIX(2026-05-11) build8: shiftId из активной смены вместо "current" — для аудита очереди
    private val shiftDao: ShiftDao,
) : PauseRepository {

    /**
     * FIX(2026-05-11) build8: реальный shiftId активной смены (или "current" если смены нет).
     * Используется только для трассировки в pending_actions — серверу не передаётся
     * (backend /shift/pause/start|end находит смену по JWT).
     */
    private suspend fun activeShiftId(): String =
        shiftDao.getActiveShift()?.id ?: "current"

    override suspend fun startPause(reason: String?): Result<PauseResponse> {
        val sid = activeShiftId()
        return safeApiCallWithFallback("начала паузы",
            call = { pauseApi.startPause(StartPauseRequest(reason)) },
            // FIX(2026-05-22) P0-2: кладём reason в очередь, чтобы офлайн обед/перекур
            // (break:lunch / break:smoke) не потеряли причину при синке.
            offlineAction = { PendingAction.StartPause(shiftId = sid, reason = reason) },
        )
    }

    override suspend fun endPause(): Result<PauseResponse> {
        val sid = activeShiftId()
        return safeApiCallWithFallback("завершения паузы",
            call = { pauseApi.endPause() },
            offlineAction = { PendingAction.EndPause(shiftId = sid) },
        )
    }

    override suspend fun startIdle(reason: String): Result<PauseResponse> {
        val sid = activeShiftId()
        return safeApiCallWithFallback("начала простоя",
            call = { pauseApi.startIdle(StartIdleRequest(reason)) },
            offlineAction = { PendingAction.StartIdle(shiftId = sid, reason = reason) },
        )
    }

    override suspend fun endIdle(): Result<PauseResponse> {
        val sid = activeShiftId()
        return safeApiCallWithFallback("завершения простоя",
            call = { pauseApi.endIdle() },
            offlineAction = { PendingAction.EndIdle(shiftId = sid) },
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
