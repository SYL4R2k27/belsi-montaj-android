package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.*
import retrofit2.Response
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
    private val pauseApi: PauseApi
) : PauseRepository {

    override suspend fun startPause(reason: String?): Result<PauseResponse> {
        return safeApiCall("начала паузы") { pauseApi.startPause(StartPauseRequest(reason)) }
    }

    override suspend fun endPause(): Result<PauseResponse> {
        return safeApiCall("завершения паузы") { pauseApi.endPause() }
    }

    override suspend fun startIdle(reason: String): Result<PauseResponse> {
        return safeApiCall("начала простоя") { pauseApi.startIdle(StartIdleRequest(reason)) }
    }

    override suspend fun endIdle(): Result<PauseResponse> {
        return safeApiCall("завершения простоя") { pauseApi.endIdle() }
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
}
