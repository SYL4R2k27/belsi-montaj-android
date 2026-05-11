package com.belsi.work.data.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.belsi.work.data.local.database.dao.PendingActionDao
import com.belsi.work.data.local.database.entities.PendingActionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-05): Фасад для offline-first очереди.
 *
 * Использование (в существующих Repository):
 *
 *   suspend fun startShift(...): Result<Shift> = try {
 *       val resp = api.startShift(...)
 *       if (resp.isSuccessful) Result.success(...)
 *       else throw IOException("HTTP ${'$'}{resp.code()}")
 *   } catch (e: Exception) {
 *       // Сеть упала или сервер недоступен → в очередь
 *       offlineQueue.enqueue(PendingAction.StartShift(siteObjectId))
 *       Result.success(localShiftPlaceholder())  // UI продолжает работать
 *   }
 *
 * Главное — НЕ показывать ошибку юзеру если действие добавлено в очередь.
 * Юзер видит «📤 будет отправлено» вместо «Ошибка сети».
 */
@Singleton
class OfflineQueueRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PendingActionDao,
    private val json: Json,
) {

    /** Подписка на счётчик pending — для бейджа в шапке. */
    val pendingCount: Flow<Int> = dao.pendingCountFlow()

    /** Все действия в очереди — для DEBUG-экрана / settings «Ожидают отправки». */
    val allActions: Flow<List<PendingActionEntity>> = dao.observeAll()

    /**
     * Добавить действие в очередь. Возвращает id записи.
     * Сразу же планирует запуск Worker'а с constraint NetworkType.CONNECTED —
     * как только сеть появится, попробуется отправить.
     */
    suspend fun enqueue(action: PendingAction): Long {
        val entity = PendingActionEntity(
            actionType = action::class.simpleName ?: "Unknown",
            payloadJson = json.encodeToString(action),
        )
        val id = dao.insert(entity)
        scheduleWorker()
        return id
    }

    /**
     * Запланировать запуск Worker. WorkManager сам:
     * - Запустит когда появится сеть (NetworkType.CONNECTED)
     * - Сделает retry с exponential backoff если упало
     * - Переживёт reboot устройства
     */
    fun scheduleWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PendingSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PendingSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,  // Не запускать второго если первый ещё бежит
            request,
        )
    }

    /** Ручной retry для конкретного failed-действия (если юзер нажимает «попробовать ещё раз»). */
    suspend fun retry(id: Long) {
        dao.retry(id)
        scheduleWorker()
    }

    /** Удалить действие (юзер отменяет). */
    suspend fun cancel(id: Long) {
        dao.deleteById(id)
    }

    /**
     * Очистить failed-старше 7 дней — гигиена БД.
     * Вызывать редко (раз в день из application onCreate).
     */
    suspend fun cleanupOldFailed() {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        dao.cleanOldFailed(sevenDaysAgo)
    }
}
