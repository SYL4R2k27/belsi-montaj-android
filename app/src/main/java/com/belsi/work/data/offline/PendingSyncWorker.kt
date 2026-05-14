package com.belsi.work.data.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.belsi.work.data.local.database.dao.PendingActionDao
import com.belsi.work.data.local.database.entities.PendingActionEntity
import com.belsi.work.data.remote.api.BatchApi
import com.belsi.work.data.remote.api.BreakStartRequest
import com.belsi.work.data.remote.api.CoordinatorApi
import com.belsi.work.data.remote.api.CoordinatorRejectPhotoRequest
import com.belsi.work.data.remote.api.CreateTaskRequest
import com.belsi.work.data.remote.dto.coordinator.CreateCoordinatorTaskRequest
import com.belsi.work.data.remote.api.CuratorApi
import com.belsi.work.data.remote.api.FinishShiftRequest
import com.belsi.work.data.remote.api.ForemanIssueToolRequest
import com.belsi.work.data.remote.api.ForemanReturnToolRequest
import com.belsi.work.data.remote.api.PauseApi
import com.belsi.work.data.remote.api.PhotoApi
import com.belsi.work.data.remote.api.RejectPhotoRequest
import com.belsi.work.data.remote.api.ShiftApi
import com.belsi.work.data.remote.api.StartIdleRequest
import com.belsi.work.data.remote.api.StartPauseRequest
import com.belsi.work.data.remote.api.StartShiftRequest
import com.belsi.work.data.remote.api.TasksApi
import com.belsi.work.data.remote.api.TeamApi
import com.belsi.work.data.remote.api.UpdateTaskRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException

/**
 * FIX(2026-05-05): Worker для отправки pending-действий когда сеть появилась.
 *
 * Реализация executeAction для всех 8 типов:
 * - StartShift / FinishShift            → ShiftApi
 * - StartPause / EndPause                → /shift/pause endpoints
 * - StartIdle / EndIdle                  → /shift/idle endpoints
 * - UploadPhoto                          → PhotoApi.uploadPhoto (multipart)
 * - UpdateTaskStatus                     → TasksApi.updateTask
 *
 * При успехе — удаляет запись из БД (и файл из PhotoFileStorage если это photo).
 * При неудаче — увеличивает retries, exponential backoff WorkManager.
 *
 * Для pause/idle конкретные роуты могут отличаться — проверяю через PauseRepository
 * шаблон вызова. Если нужно расширить — добавить методы в существующий ShiftApi.
 */
@HiltWorker
class PendingSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: PendingActionDao,
    private val json: Json,
    private val shiftApi: ShiftApi,
    private val photoApi: PhotoApi,
    private val tasksApi: TasksApi,
    private val pauseApi: PauseApi,
    private val batchApi: BatchApi,
    private val photoStorage: PhotoFileStorage,
    // FIX(2026-05-12) build18 P2: новые API для расширенных pending-actions.
    private val coordinatorApi: CoordinatorApi,
    private val curatorApi: CuratorApi,
    private val teamApi: TeamApi,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_NAME = "pending_sync"
        private const val TAG = "PendingSyncWorker"
        private const val MAX_RETRIES = 10
    }

    override suspend fun doWork(): Result {
        val ready = dao.getReadyToSend()
        if (ready.isEmpty()) {
            android.util.Log.d(TAG, "Очередь пуста")
            return Result.success()
        }
        android.util.Log.i(TAG, "Найдено ${ready.size} pending действий")

        var anyFailed = false
        for (entity in ready) {
            val ok = runCatching {
                dao.update(entity.copy(status = "sending", lastAttemptAt = System.currentTimeMillis()))
                executeAction(entity)
            }.getOrElse { e ->
                android.util.Log.e(TAG, "Ошибка #${entity.id}", e)
                false
            }

            if (ok) {
                deleteAfterSuccess(entity)
                android.util.Log.i(TAG, "✅ #${entity.id} (${entity.actionType}) отправлено")
            } else {
                val newRetries = entity.retries + 1
                val newStatus = if (newRetries >= MAX_RETRIES) "failed" else "pending"
                dao.update(entity.copy(
                    status = newStatus,
                    retries = newRetries,
                    lastError = "попытка $newRetries не удалась",
                ))
                anyFailed = true
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    /** Если действие — UploadPhoto, удаляем и файл из storage. */
    private suspend fun deleteAfterSuccess(entity: PendingActionEntity) {
        val action = decode(entity) ?: run { dao.deleteById(entity.id); return }
        if (action is PendingAction.UploadPhoto) {
            photoStorage.delete(action.localFilePath)
        }
        dao.deleteById(entity.id)
    }

    private fun decode(entity: PendingActionEntity): PendingAction? = try {
        json.decodeFromString<PendingAction>(entity.payloadJson)
    } catch (e: Exception) {
        android.util.Log.e(TAG, "decode failed for #${entity.id}: ${e.message}")
        null
    }

    /** Реальное выполнение по типу. Возвращает true при HTTP 2xx, иначе false. */
    private suspend fun executeAction(entity: PendingActionEntity): Boolean {
        val action = decode(entity) ?: return false
        return when (action) {
            is PendingAction.StartShift -> ok(shiftApi.startShift(StartShiftRequest(siteObjectId = action.siteObjectId)))
            is PendingAction.FinishShift -> ok(shiftApi.finishShift(FinishShiftRequest(action.shiftId)))
            // FIX(2026-05-11) BELSI 2.0.0 build8: реальная отправка pause/idle через PauseApi.
            // Backend (shift_pauses.py) находит активную смену по JWT — shiftId здесь
            // только для нашей трассировки, серверу не передаётся.
            // Идемпотентность гарантирована backend hotfix 1.2.6 (`/pause/end` возвращает
            // синтетический объект если паузы нет — Курешов/Красавин bug).
            is PendingAction.StartPause -> ok(pauseApi.startPause(StartPauseRequest(reason = null)))
            is PendingAction.EndPause -> ok(pauseApi.endPause())
            is PendingAction.StartIdle -> ok(pauseApi.startIdle(StartIdleRequest(reason = action.reason)))
            is PendingAction.EndIdle -> ok(pauseApi.endIdle())
            is PendingAction.UploadPhoto -> uploadPhoto(action)
            is PendingAction.UpdateTaskStatus -> ok(tasksApi.updateTask(action.taskId, UpdateTaskRequest(action.newStatus)))
            // FIX(2026-05-11) BELSI 2.0.0 build8: производственные перерывы (обед/перекур)
            // через BatchApi. Backend (factory_shifts.py) находит активную смену по JWT.
            is PendingAction.StartBreak -> ok(batchApi.startBreak(BreakStartRequest(action.type)))
            is PendingAction.EndBreak -> ok(batchApi.endBreak())

            // ============================================================
            // FIX(2026-05-12) build18 P2: новые типы pending-action.
            // ============================================================
            is PendingAction.ApprovePhoto -> when (action.role.lowercase()) {
                "curator" -> ok(curatorApi.approvePhoto(action.photoId))
                "coordinator" -> ok(coordinatorApi.approvePhoto(action.photoId))
                "foreman" -> ok(photoApi.approvePhoto(action.photoId))
                else -> ok(photoApi.approvePhoto(action.photoId))
            }
            is PendingAction.RejectPhoto -> when (action.role.lowercase()) {
                "curator" -> ok(curatorApi.rejectPhoto(action.photoId, RejectPhotoRequest(action.reason ?: "")))
                "coordinator" -> ok(coordinatorApi.rejectPhoto(action.photoId, CoordinatorRejectPhotoRequest(action.reason ?: "")))
                "foreman" -> ok(photoApi.rejectPhoto(action.photoId, RejectPhotoRequest(action.reason ?: "")))
                else -> ok(photoApi.rejectPhoto(action.photoId, RejectPhotoRequest(action.reason ?: "")))
            }
            is PendingAction.CreateTask -> when (action.role.lowercase()) {
                "curator" -> ok(curatorApi.createTask(CreateTaskRequest(
                    title = action.title,
                    description = action.description,
                    assignedTo = action.assignedTo,
                    priority = action.priority,
                    dueAt = action.dueAt,
                )))
                "coordinator" -> ok(coordinatorApi.createTask(CreateCoordinatorTaskRequest(
                    title = action.title,
                    description = action.description,
                    assignedTo = action.assignedTo,
                    priority = action.priority,
                    dueAt = action.dueAt,
                )))
                else -> ok(tasksApi.createTask(CreateTaskRequest(
                    title = action.title,
                    description = action.description,
                    assignedTo = action.assignedTo,
                    priority = action.priority,
                    dueAt = action.dueAt,
                )))
            }
            is PendingAction.BatchReceive -> ok(batchApi.receiveBatch(action.batchId))
            is PendingAction.BatchInstall -> ok(batchApi.installBatch(action.batchId))
            is PendingAction.ToolIssue -> ok(teamApi.foremanIssueTool(
                ForemanIssueToolRequest(
                    toolId = action.toolId,
                    installerId = action.installerId,
                    comment = action.comment,
                )
            ))
            is PendingAction.ToolReturn -> ok(teamApi.foremanReturnTool(
                ForemanReturnToolRequest(
                    transactionId = action.transactionId,
                    returnCondition = action.condition,
                    returnComment = action.comment,
                )
            ))
        }
    }

    private fun ok(resp: Response<*>): Boolean = resp.isSuccessful

    private suspend fun uploadPhoto(action: PendingAction.UploadPhoto): Boolean {
        if (!photoStorage.exists(action.localFilePath)) {
            android.util.Log.w(TAG, "photo file gone: ${action.localFilePath}")
            return true  // Файла нет — считаем что удалили, обнуляем запись
        }
        val file = File(action.localFilePath)
        return try {
            val photoPart = MultipartBody.Part.createFormData(
                name = "photo",
                filename = file.name,
                body = file.asRequestBody("image/jpeg".toMediaTypeOrNull()),
            )
            val resp = photoApi.uploadPhoto(
                shiftId = action.shiftId.toRequestBody("text/plain".toMediaTypeOrNull()),
                hourLabel = (action.hourLabel ?: "").toRequestBody("text/plain".toMediaTypeOrNull()),
                comment = (action.comment ?: "").toRequestBody("text/plain".toMediaTypeOrNull()),
                category = action.category.toRequestBody("text/plain".toMediaTypeOrNull()),
                photo = photoPart,
            )
            resp.isSuccessful
        } catch (e: IOException) {
            android.util.Log.w(TAG, "upload IOException: ${e.message}")
            false
        }
    }
}
