package com.belsi.work.data.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.belsi.work.data.local.database.dao.PhotoDao
import com.belsi.work.data.local.database.entities.PhotoEntity
import com.belsi.work.data.remote.api.ShiftApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Фоновый worker для загрузки фото из офлайн-очереди.
 *
 * Работает так:
 * 1. Берёт фото со статусом LOCAL и retryCount < 5 из Room
 * 2. Загружает каждое на сервер через multipart upload
 * 3. При успехе: status = UPLOADED + сохраняет remoteUrl
 * 4. При ошибке: retryCount++ для повторной попытки
 *
 * Запускается:
 * - После сохранения фото локально (OneTimeWork)
 * - При появлении сети (через NetworkMonitor)
 * - Каждые 15 минут (PeriodicWork как safety net)
 */
@HiltWorker
class PhotoUploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val photoDao: PhotoDao,
    private val shiftApi: ShiftApi
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "photo_upload_work"
        const val PERIODIC_WORK_NAME = "photo_upload_periodic"
        private const val TAG = "PhotoUploadWorker"

        /**
         * Запустить одноразовую загрузку (при сохранении фото или появлении сети)
         */
        fun enqueueUpload(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)

            Log.d(TAG, "Photo upload enqueued")
        }

        /**
         * Запланировать периодическую проверку (safety net)
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PhotoUploadWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            Log.d(TAG, "Periodic photo upload scheduled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting photo upload work...")

        // FIX(2026-04-30): сбрасываем фото, у которых retryCount >= 5 — иначе они
        // навсегда замораживались до перезапуска приложения. resetFailedPhotos
        // вызывался только в BelsiWorkApp.onCreate; теперь — каждый запуск воркера.
        try {
            val reset = photoDao.resetFailedPhotos()
            if (reset > 0) Log.i(TAG, "Reset $reset stuck photos (retryCount>=5) → LOCAL")
        } catch (e: Exception) {
            Log.w(TAG, "resetFailedPhotos failed", e)
        }

        val pendingPhotos = photoDao.getPendingPhotosForUpload()
        if (pendingPhotos.isEmpty()) {
            Log.d(TAG, "No pending photos to upload")
            return Result.success()
        }

        Log.d(TAG, "Found ${pendingPhotos.size} photos to upload")

        var allSuccess = true
        for (photo in pendingPhotos) {
            val success = uploadPhoto(photo)
            if (!success) allSuccess = false
        }

        // Проверяем остались ли ещё фото
        val remaining = photoDao.getPendingPhotoCount()
        Log.d(TAG, "Upload complete. Remaining: $remaining, allSuccess: $allSuccess")

        return if (allSuccess) Result.success() else Result.retry()
    }

    private suspend fun uploadPhoto(photo: PhotoEntity): Boolean {
        val file = photo.localPath?.let { File(it) }
        if (file == null || !file.exists()) {
            Log.e(TAG, "Photo file not found: ${photo.localPath}, removing from queue")
            photoDao.deletePhotoById(photo.id)
            return true // Считаем успехом — файла нет, нечего загружать
        }

        // Пропускаем фото с локальным shiftId (смена ещё не синхронизирована)
        if (photo.shiftId.startsWith("local-")) {
            Log.d(TAG, "Skipping photo ${photo.id}: shift ${photo.shiftId} not synced yet")
            return true
        }

        try {
            photoDao.updatePhotoStatus(photo.id, "UPLOADING")

            val photoRequestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", file.name, photoRequestBody)

            val shiftIdBody = photo.shiftId.toRequestBody("text/plain".toMediaTypeOrNull())
            val hourLabelBody = photo.hourLabel.toRequestBody("text/plain".toMediaTypeOrNull())
            val commentBody = photo.comment?.toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryBody = photo.category.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = shiftApi.uploadHourPhoto(
                shiftId = shiftIdBody,
                hourLabel = hourLabelBody,
                photo = photoPart,
                comment = commentBody,
                category = categoryBody
            )

            if (response.isSuccessful) {
                val body = response.body()
                val remoteUrl = body?.photoUrl ?: ""
                photoDao.markPhotoAsUploaded(photo.id, "UPLOADED", remoteUrl)
                Log.d(TAG, "Photo ${photo.id} uploaded successfully")

                // Удаляем локальный файл после успешной загрузки
                try { file.delete() } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete local file: ${file.path}")
                }
                return true
            } else {
                Log.e(TAG, "Upload failed: ${response.code()} ${response.message()}")
                photoDao.incrementRetryCount(photo.id)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception for photo ${photo.id}", e)
            photoDao.incrementRetryCount(photo.id)
            return false
        }
    }
}
