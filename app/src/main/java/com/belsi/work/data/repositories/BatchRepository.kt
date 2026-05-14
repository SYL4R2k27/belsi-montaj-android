package com.belsi.work.data.repositories

import com.belsi.work.data.local.database.dao.ShiftDao
import com.belsi.work.data.models.Batch
import com.belsi.work.data.models.BatchCreateRequest
import com.belsi.work.data.models.BatchHistoryItem
import com.belsi.work.data.models.BatchStatus
import com.belsi.work.data.models.BatchStatusChangeRequest
import com.belsi.work.data.models.IdleReason
import com.belsi.work.data.offline.OfflineQueueRepository
import com.belsi.work.data.offline.OfflineQueuedException
import com.belsi.work.data.offline.PendingAction
import com.belsi.work.data.remote.api.BatchApi
import com.belsi.work.data.remote.api.BreakStartRequest
import com.belsi.work.data.remote.error.parseApiError
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-05): Репозиторий партий + перерывы + idle reasons.
 *
 * Все методы возвращают Result<T> в стиле остальных репозиториев проекта.
 * При ошибке сервера — возвращается Result.failure с понятным сообщением.
 */
interface BatchRepository {
    suspend fun listBatches(
        status: BatchStatus? = null,
        facilityId: String? = null,
        targetObjectId: String? = null,
    ): Result<List<Batch>>

    suspend fun createBatch(request: BatchCreateRequest): Result<Batch>
    suspend fun getBatch(batchId: String): Result<Batch>
    suspend fun changeStatus(
        batchId: String,
        toStatus: BatchStatus,
        comment: String? = null,
    ): Result<Batch>
    suspend fun getHistory(batchId: String): Result<List<BatchHistoryItem>>

    suspend fun startSmokeBreak(): Result<Unit>
    suspend fun startLunchBreak(): Result<Unit>
    suspend fun endBreak(): Result<Unit>

    suspend fun getIdleReasons(domain: String? = null): Result<List<IdleReason>>

    // FIX(2026-05-12) BELSI 2.0.0 build15: Foreman/Coordinator endpoints
    suspend fun getIncomingBatches(): Result<List<com.belsi.work.data.models.IncomingBatchDto>>
    suspend fun receiveBatch(batchId: String): Result<Unit>
    suspend fun installBatch(batchId: String): Result<Unit>
}

@Singleton
class BatchRepositoryImpl @Inject constructor(
    private val api: BatchApi,
    private val json: Json,
    // FIX(2026-05-11) BELSI 2.0.0 build8: offline-очередь для обеда/перекура
    private val offlineQueue: OfflineQueueRepository,
    private val shiftDao: ShiftDao,
) : BatchRepository {

    /** Реальный shiftId активной смены — для трассировки в pending_actions. */
    private suspend fun activeShiftId(): String =
        shiftDao.getActiveShift()?.id ?: "current"

    override suspend fun listBatches(
        status: BatchStatus?,
        facilityId: String?,
        targetObjectId: String?,
    ): Result<List<Batch>> = try {
        val resp = api.listBatches(
            status = status?.name?.lowercase(),
            facilityId = facilityId,
            targetObjectId = targetObjectId,
        )
        if (resp.isSuccessful) Result.success(resp.body() ?: emptyList())
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка загрузки партий: ${e.message}", e))
    }

    override suspend fun createBatch(request: BatchCreateRequest): Result<Batch> = try {
        val resp = api.createBatch(request)
        if (resp.isSuccessful && resp.body() != null) Result.success(resp.body()!!)
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка создания партии: ${e.message}", e))
    }

    override suspend fun getBatch(batchId: String): Result<Batch> = try {
        val resp = api.getBatch(batchId)
        if (resp.isSuccessful && resp.body() != null) Result.success(resp.body()!!)
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка загрузки партии: ${e.message}", e))
    }

    override suspend fun changeStatus(
        batchId: String,
        toStatus: BatchStatus,
        comment: String?,
    ): Result<Batch> = try {
        val resp = api.changeStatus(batchId, BatchStatusChangeRequest(toStatus, comment))
        if (resp.isSuccessful && resp.body() != null) Result.success(resp.body()!!)
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка смены статуса: ${e.message}", e))
    }

    override suspend fun getHistory(batchId: String): Result<List<BatchHistoryItem>> = try {
        val resp = api.getHistory(batchId)
        if (resp.isSuccessful) Result.success(resp.body() ?: emptyList())
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка истории: ${e.message}", e))
    }

    override suspend fun startSmokeBreak(): Result<Unit> = doBreakStart("smoke")
    override suspend fun startLunchBreak(): Result<Unit> = doBreakStart("lunch")

    private suspend fun doBreakStart(type: String): Result<Unit> = try {
        val resp = api.startBreak(BreakStartRequest(type))
        if (resp.isSuccessful) Result.success(Unit)
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: IOException) {
        // FIX(2026-05-11) BELSI 2.0.0 build8: offline-fallback для производственных перерывов.
        // Сеть упала — действие в очередь, PendingSyncWorker отправит когда сеть появится.
        offlineQueue.enqueue(PendingAction.StartBreak(shiftId = activeShiftId(), type = type))
        Result.failure(OfflineQueuedException("перерыв «$type»: будет отправлен когда появится связь", e))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка старта перерыва: ${e.message}", e))
    }

    override suspend fun endBreak(): Result<Unit> = try {
        val resp = api.endBreak()
        if (resp.isSuccessful) Result.success(Unit)
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: IOException) {
        // FIX(2026-05-11) BELSI 2.0.0 build8: offline-fallback для завершения перерыва.
        offlineQueue.enqueue(PendingAction.EndBreak(shiftId = activeShiftId()))
        Result.failure(OfflineQueuedException("завершение перерыва: будет отправлено когда появится связь", e))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка завершения перерыва: ${e.message}", e))
    }

    override suspend fun getIdleReasons(domain: String?): Result<List<IdleReason>> = try {
        val resp = api.getIdleReasons(domain)
        if (resp.isSuccessful) Result.success(resp.body() ?: emptyList())
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка загрузки причин: ${e.message}", e))
    }

    // FIX(2026-05-12) BELSI 2.0.0 build15: Foreman/Coordinator endpoints
    override suspend fun getIncomingBatches(): Result<List<com.belsi.work.data.models.IncomingBatchDto>> = try {
        val resp = api.getIncomingBatches()
        if (resp.isSuccessful) Result.success(resp.body() ?: emptyList())
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка входящих партий: ${e.message}", e))
    }

    override suspend fun receiveBatch(batchId: String): Result<Unit> = try {
        val resp = api.receiveBatch(batchId)
        if (resp.isSuccessful) Result.success(Unit)
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка приёмки партии: ${e.message}", e))
    }

    override suspend fun installBatch(batchId: String): Result<Unit> = try {
        val resp = api.installBatch(batchId)
        if (resp.isSuccessful) Result.success(Unit)
        else Result.failure(Exception(parseApiError(json, resp.errorBody()?.string(), resp.code())))
    } catch (e: Exception) {
        Result.failure(Exception("Ошибка закрытия монтажа: ${e.message}", e))
    }
}
