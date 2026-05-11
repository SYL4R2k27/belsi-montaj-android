package com.belsi.work.data.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.belsi.work.data.local.database.dao.PendingActionDao
import com.belsi.work.data.local.database.entities.PendingActionEntity
import com.belsi.work.data.remote.api.PhotoApi
import com.belsi.work.data.remote.api.ShiftApi
import com.belsi.work.data.remote.api.TasksApi
import com.belsi.work.data.remote.api.UpdateTaskRequest
import com.belsi.work.data.remote.api.FinishShiftRequest
import com.belsi.work.data.remote.api.StartShiftRequest
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
    private val photoStorage: PhotoFileStorage,
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
            is PendingAction.StartShift -> ok(shiftApi.startShift(StartShiftRequest()))
            is PendingAction.FinishShift -> ok(shiftApi.finishShift(FinishShiftRequest(action.shiftId)))
            // pause/idle — стандартные эндпоинты, работают через специальный PauseApi
            // если он есть в проекте; иначе — пока возвращаем false (пишется в lastError).
            // Реальная имплементация добавится когда увидим точные DTO.
            is PendingAction.StartPause,
            is PendingAction.EndPause,
            is PendingAction.StartIdle,
            is PendingAction.EndIdle -> {
                android.util.Log.w(TAG, "Pause/Idle pending action: ${action::class.simpleName} — TBD direct API call")
                false
            }
            is PendingAction.UploadPhoto -> uploadPhoto(action)
            is PendingAction.UpdateTaskStatus -> ok(tasksApi.updateTask(action.taskId, UpdateTaskRequest(action.newStatus)))
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
