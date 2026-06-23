package com.belsi.work.data.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.belsi.work.data.local.database.dao.PhotoDao
import com.belsi.work.data.local.database.dao.ShiftDao
import com.belsi.work.data.remote.api.FinishShiftRequest
import com.belsi.work.data.remote.api.ShiftApi
import com.belsi.work.data.remote.api.StartShiftRequest
import com.belsi.work.data.repositories.ChatRepositoryImpl
import com.belsi.work.data.repositories.MessengerRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Общий фоновый worker для синхронизации данных.
 *
 * Обязанности:
 * 1. Синхронизация pending-смен (offline start/finish)
 * 2. Триггер PhotoUploadWorker если есть фото в очереди
 * 3. Очистка старых данных
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val shiftDao: ShiftDao,
    private val shiftApi: ShiftApi,
    private val photoDao: PhotoDao,
    private val messengerRepository: MessengerRepositoryImpl,
    private val chatRepository: ChatRepositoryImpl
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val PERIODIC_WORK_NAME = "sync_periodic"
        const val ONE_TIME_WORK_NAME = "sync_now"
        private const val TAG = "SyncWorker"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
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

            Log.d(TAG, "Periodic sync scheduled")
        }

        fun enqueueNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)

            Log.d(TAG, "Sync enqueued now")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync work...")

        try {
            // 1. Синхронизировать pending-смены
            syncPendingShifts()

            // 2. Проверить есть ли фото для загрузки
            val pendingPhotoCount = photoDao.getPendingPhotoCount()
            if (pendingPhotoCount > 0) {
                Log.d(TAG, "$pendingPhotoCount photos pending upload, triggering PhotoUploadWorker")
                PhotoUploadWorker.enqueueUpload(context)
            }

            // 3. Синхронизировать pending-сообщения мессенджера
            try {
                messengerRepository.syncPendingMessages()
            } catch (e: Exception) {
                Log.w(TAG, "Messenger sync failed: ${e.message}")
            }

            // 3b. Синхронизировать pending-сообщения саппорт-чата
            try {
                chatRepository.syncPendingMessages()
            } catch (e: Exception) {
                Log.w(TAG, "Chat sync failed: ${e.message}")
            }

            // 4. Очистка старых завершённых смен (> 7 дней)
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            shiftDao.deleteOldFinishedShifts(sevenDaysAgo)

            // 4. Очистка старых загруженных фото (> 3 дня)
            val threeDaysAgo = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
            photoDao.deleteOldUploadedPhotos(threeDaysAgo)

            Log.d(TAG, "Sync work completed successfully")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync work failed", e)
            return Result.retry()
        }
    }

    private suspend fun syncPendingShifts() {
        val pendingShifts = shiftDao.getPendingShifts()
        Log.d(TAG, "Found ${pendingShifts.size} pending shifts to sync")

        for (shift in pendingShifts) {
            try {
                if (shift.id.startsWith("local-")) {
                    // Офлайн-старт: создать смену на сервере
                    syncOfflineShiftStart(shift.id)
                } else if (shift.status == "finished" && shift.syncStatus == "pending") {
                    // Офлайн-завершение: отправить finish на сервер
                    syncOfflineShiftFinish(shift.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync shift ${shift.id}", e)
                shiftDao.updateSyncStatus(shift.id, "error")
            }
        }
    }

    private suspend fun syncOfflineShiftStart(localShiftId: String) {
        Log.d(TAG, "Syncing offline shift start: $localShiftId")

        val response = shiftApi.startShift(StartShiftRequest())
        if (response.isSuccessful) {
            val body = response.body()!!
            val serverId = body.id

            // Обновить shiftId во всех связанных фото
            photoDao.updateShiftId(localShiftId, serverId)

            // Удалить локальную смену и создать серверную
            val localShift = shiftDao.getShiftById(localShiftId)
            if (localShift != null) {
                shiftDao.deleteShift(localShiftId)
                shiftDao.insertShift(
                    localShift.copy(
                        id = serverId,
                        syncStatus = "synced",
                        startAt = body.startAt
                    )
                )
            }

            Log.d(TAG, "Offline shift $localShiftId → server $serverId")
        } else {
            Log.e(TAG, "Failed to sync shift start: ${response.code()}")
            shiftDao.updateSyncStatus(localShiftId, "error")
        }
    }

    private suspend fun syncOfflineShiftFinish(shiftId: String) {
        Log.d(TAG, "Syncing offline shift finish: $shiftId")

        val response = shiftApi.finishShift(FinishShiftRequest(shiftId = shiftId))
        if (response.isSuccessful || response.code() == 400) {
            // 400 = already finished — считаем успехом
            shiftDao.updateSyncStatus(shiftId, "synced")
            Log.d(TAG, "Shift $shiftId finish synced (code: ${response.code()})")
        } else {
            Log.e(TAG, "Failed to sync shift finish: ${response.code()}")
            shiftDao.updateSyncStatus(shiftId, "error")
        }
    }
}
