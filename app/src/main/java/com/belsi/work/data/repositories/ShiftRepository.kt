package com.belsi.work.data.repositories

import com.belsi.work.data.local.database.dao.ShiftDao
import com.belsi.work.data.local.database.entities.ShiftEntity
import com.belsi.work.data.remote.api.ShiftApi
import com.belsi.work.data.remote.dto.shift.StartShiftResponse
import com.belsi.work.data.remote.dto.shift.UploadHourPhotoResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы со сменами в BELSI API
 *
 * API эндпоинты:
 * - POST /shifts/start - начать смену
 * - POST /shift/hour/photo - загрузить почасовое фото
 */
interface ShiftRepository {
    suspend fun startShift(siteObjectId: String? = null): Result<ShiftData>
    suspend fun endShift(shiftId: String): Result<ShiftData>
    suspend fun uploadHourPhoto(
        shiftId: String,
        hourLabel: String,
        photoFile: File,
        comment: String? = null,
        category: String = "hourly"
    ): Result<PhotoUploadData>
    suspend fun getShiftHistory(page: Int, limit: Int): Result<List<ShiftHistoryData>>
    suspend fun getActiveShift(): Result<ShiftData?>
    suspend fun getShiftDetails(shiftId: String): Result<ShiftDetailData>
    suspend fun getShiftPhotos(shiftId: String): Result<List<ShiftPhotoData>>
    suspend fun syncPhotosFromServer(shiftId: String): Result<Unit>

    // Offline-first methods with Flow
    fun observeActiveShift(): Flow<ShiftData?>
    suspend fun syncPendingShifts(): Result<Unit>
}

/**
 * Данные активной смены
 */
data class ShiftData(
    val id: String,
    val startAt: String,
    val status: String
)

/**
 * Данные загруженного фото
 */
data class PhotoUploadData(
    val id: String,
    val shiftId: String,
    val hourLabel: String,
    val status: String,
    val photoUrl: String,
    val comment: String?,
    val createdAt: String
)

/**
 * Данные истории смены
 */
data class ShiftHistoryData(
    val id: String,
    val startAt: String,
    val endAt: String?,
    val status: String,
    val durationMinutes: Int?,
    val earnings: Double?,
    val photosCount: Int?
)

/**
 * Детальные данные смены
 */
data class ShiftDetailData(
    val id: String,
    val userId: String,
    val startAt: String,
    val endAt: String?,
    val status: String,
    val location: String?,
    val notes: String?
)

/**
 * Данные фотографии смены
 */
data class ShiftPhotoData(
    val id: String,
    val shiftId: String,
    val hourLabel: String?,
    val status: String,
    val comment: String?,
    val photoUrl: String,
    val createdAt: String
)

@Singleton
class ShiftRepositoryImpl @Inject constructor(
    private val shiftApi: ShiftApi,
    private val shiftDao: ShiftDao,
    private val photoDao: com.belsi.work.data.local.database.dao.PhotoDao
) : ShiftRepository {

    companion object {
        private const val CACHE_VALIDITY_MS = 120_000L // 2 minutes
    }

    /**
     * Начать смену
     * POST /shifts/start
     * Сохраняет в локальную БД для offline доступа
     */
    override suspend fun startShift(siteObjectId: String?): Result<ShiftData> {
        return try {
            val request = com.belsi.work.data.remote.api.StartShiftRequest(siteObjectId = siteObjectId)
            val response = shiftApi.startShift(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val shiftData = ShiftData(
                    id = body.id,
                    startAt = body.startAt,
                    status = body.status
                )

                // Сохраняем в локальную БД
                val entity = ShiftEntity(
                    userId = "current_user", // User ID from auth token
                    id = body.id,
                    startAt = body.startAt,
                    finishAt = null,
                    status = body.status,
                    syncStatus = "synced",
                    lastSyncAt = System.currentTimeMillis()
                )
                shiftDao.insertShift(entity)
                android.util.Log.d("ShiftRepository", "✅ Shift cached locally: ${body.id}")

                Result.success(shiftData)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети: ${e.message}", e))
        }
    }

    /**
     * Завершить смену
     * POST /shifts/finish
     * Offline-first: при ошибке сети сохраняем финиш локально, SyncWorker досинхронизирует
     */
    override suspend fun endShift(shiftId: String): Result<ShiftData> {
        return try {
            if (shiftId.isBlank()) {
                android.util.Log.e("ShiftRepository", "endShift called with blank shiftId")
                return Result.failure(Exception("ID смены не может быть пустым"))
            }

            // Если shiftId локальный — завершаем только локально
            if (shiftId.startsWith("local-")) {
                android.util.Log.d("ShiftRepository", "Finishing local shift offline: $shiftId")
                val now = java.time.OffsetDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                shiftDao.updateShiftStatus(shiftId, "finished")
                shiftDao.updateShiftEndTime(shiftId, now)
                shiftDao.updateSyncStatus(shiftId, "pending")
                return Result.success(ShiftData(id = shiftId, startAt = "", status = "finished"))
            }

            android.util.Log.d("ShiftRepository", "=== ENDING SHIFT ===")
            android.util.Log.d("ShiftRepository", "ShiftId: $shiftId")

            val request = com.belsi.work.data.remote.api.FinishShiftRequest(shiftId = shiftId)
            val response = shiftApi.finishShift(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                android.util.Log.d("ShiftRepository", "✅ Shift ended successfully!")

                val result = ShiftData(
                    id = body.id,
                    startAt = body.startAt,
                    status = body.status
                )

                // Обновляем локальную БД
                shiftDao.updateShiftStatus(shiftId, body.status)
                shiftDao.updateShiftEndTime(shiftId, body.finishAt ?: "")
                shiftDao.updateSyncStatus(shiftId, "synced")

                Result.success(result)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("ShiftRepository", "❌ Finish shift FAILED: ${response.code()}")

                // Если 400 и смена уже завершена — считаем успехом (идемпотентность)
                if (response.code() == 400 && errorBody?.contains("finished") == true) {
                    android.util.Log.w("ShiftRepository", "⚠️ Shift already finished, treating as success")
                    shiftDao.updateShiftStatus(shiftId, "finished")
                    return Result.success(ShiftData(id = shiftId, startAt = "", status = "finished"))
                }

                val errorMsg = parseErrorMessage(response.code(), errorBody)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("ShiftRepository", "❌ Timeout — saving finish offline", e)
            saveFinishOffline(shiftId)
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("ShiftRepository", "❌ No network — saving finish offline", e)
            saveFinishOffline(shiftId)
        } catch (e: java.io.IOException) {
            android.util.Log.e("ShiftRepository", "❌ IO error — saving finish offline", e)
            saveFinishOffline(shiftId)
        } catch (e: Exception) {
            android.util.Log.e("ShiftRepository", "❌ Unexpected error while ending shift", e)
            Result.failure(Exception("Ошибка завершения смены: ${e.message}", e))
        }
    }

    /**
     * Сохранить завершение смены локально при отсутствии сети.
     * SyncWorker подхватит и отправит на сервер при появлении связи.
     */
    private suspend fun saveFinishOffline(shiftId: String): Result<ShiftData> {
        val now = java.time.OffsetDateTime.now()
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        shiftDao.updateShiftStatus(shiftId, "finished")
        shiftDao.updateShiftEndTime(shiftId, now)
        shiftDao.updateSyncStatus(shiftId, "pending")
        android.util.Log.d("ShiftRepository", "📱 Finish saved offline, will sync later")
        return Result.success(ShiftData(id = shiftId, startAt = "", status = "finished"))
    }

    /**
     * Загрузить почасовое фото
     * POST /shift/hour/photo
     *
     * Multipart с полями:
     * - shift_id
     * - hour_label
     * - file (JPEG/PNG)
     * - comment (опционально)
     */
    override suspend fun uploadHourPhoto(
        shiftId: String,
        hourLabel: String,
        photoFile: File,
        comment: String?,
        category: String
    ): Result<PhotoUploadData> {
        return try {
            // Валидация параметров
            if (shiftId.isBlank()) {
                return Result.failure(Exception("ID смены не может быть пустым"))
            }

            if (hourLabel.isBlank()) {
                return Result.failure(Exception("Метка времени не может быть пустой"))
            }

            // Проверка файла
            if (!photoFile.exists()) {
                return Result.failure(Exception("Файл не найден"))
            }

            // Проверка размера файла (макс 10MB)
            val maxSize = 10 * 1024 * 1024 // 10MB
            if (photoFile.length() > maxSize) {
                return Result.failure(Exception("Файл слишком большой (макс 10MB)"))
            }

            android.util.Log.d("ShiftRepository", "Uploading photo: shift=$shiftId, hour=$hourLabel, comment=$comment, size=${photoFile.length()} bytes")

            // Создаем multipart части
            val shiftIdPart = shiftId.toRequestBody("text/plain".toMediaTypeOrNull())
            val hourLabelPart = hourLabel.toRequestBody("text/plain".toMediaTypeOrNull())
            val commentPart = comment?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryPart = category.toRequestBody("text/plain".toMediaTypeOrNull())

            val requestFile = photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photoFile.name, requestFile)

            // Отправка запроса
            val response = shiftApi.uploadHourPhoto(
                shiftId = shiftIdPart,
                hourLabel = hourLabelPart,
                photo = photoPart,
                comment = commentPart,
                category = categoryPart
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                android.util.Log.d("ShiftRepository", "Photo uploaded successfully: id=${body.id}, url=${body.photoUrl}")
                Result.success(PhotoUploadData(
                    id = body.id,
                    shiftId = body.shiftId,
                    hourLabel = body.hourLabel,
                    status = body.status,
                    photoUrl = body.photoUrl,
                    comment = body.comment,
                    createdAt = body.createdAt
                ))
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("ShiftRepository", "Upload failed: code=${response.code()}, error=$errorBody")
                val errorMsg = parseErrorMessage(response.code(), errorBody)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("ShiftRepository", "Upload timeout", e)
            Result.failure(Exception("Превышено время ожидания. Проверьте интернет-соединение"))
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("ShiftRepository", "Network error - unknown host", e)
            Result.failure(Exception("Нет подключения к серверу. Проверьте интернет"))
        } catch (e: java.io.IOException) {
            android.util.Log.e("ShiftRepository", "IO error during upload", e)
            Result.failure(Exception("Ошибка сети: ${e.message}"))
        } catch (e: Exception) {
            android.util.Log.e("ShiftRepository", "Unexpected error during upload", e)
            Result.failure(Exception("Ошибка загрузки фото: ${e.message}", e))
        }
    }

    override suspend fun getShiftHistory(page: Int, limit: Int): Result<List<ShiftHistoryData>> {
        return try {
            val response = shiftApi.getShiftHistory(page, limit)

            if (response.isSuccessful && response.body() != null) {
                // Сервер возвращает { "items": [...] }
                val shifts = response.body()!!.items.map { item ->
                    ShiftHistoryData(
                        id = item.id,
                        startAt = item.start_at,
                        endAt = item.end_at,
                        status = item.status,
                        durationMinutes = item.duration_minutes,
                        earnings = item.earnings,
                        photosCount = item.photos_count
                    )
                }
                Result.success(shifts)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки истории: ${e.message}", e))
        }
    }

    /**
     * Получить активную смену из GET /shifts
     * Ищет смену со статусом "active"
     * Использует offline-first подход: сначала проверяет кеш, затем API
     */
    override suspend fun getActiveShift(): Result<ShiftData?> {
        return try {
            android.util.Log.d("ShiftRepository", "=== GETTING ACTIVE SHIFT ===")

            // Проверяем локальный кеш
            val cachedShift = shiftDao.getActiveShift()
            val now = System.currentTimeMillis()

            if (cachedShift != null && (now - cachedShift.lastSyncAt) < CACHE_VALIDITY_MS) {
                android.util.Log.d("ShiftRepository", "✅ Using cached active shift: ${cachedShift.id}")
                return Result.success(ShiftData(
                    id = cachedShift.id,
                    startAt = cachedShift.startAt,
                    status = cachedShift.status
                ))
            }

            // Загружаем с API
            val response = shiftApi.getShiftHistory(page = 1, limit = 50)

            if (response.isSuccessful && response.body() != null) {
                val allShifts = response.body()!!.items
                android.util.Log.d("ShiftRepository", "Total shifts retrieved: ${allShifts.size}")

                // Логируем все смены для отладки
                allShifts.take(5).forEach { shift ->
                    android.util.Log.d("ShiftRepository", "Shift: id=${shift.id.take(8)}, status=${shift.status}, start=${shift.start_at}")
                }

                // Ищем активную смену
                val activeShift = allShifts.firstOrNull { it.status == "active" }

                if (activeShift != null) {
                    android.util.Log.d("ShiftRepository", "✅ Found active shift: id=${activeShift.id}")

                    // Сохраняем в кеш
                    val entity = ShiftEntity(
                        id = activeShift.id,
                        startAt = activeShift.start_at,
                        userId = "current_user", // User ID from auth token
                        finishAt = activeShift.end_at,
                        status = activeShift.status,
                        syncStatus = "synced",
                        lastSyncAt = now
                    )
                    shiftDao.insertShift(entity)

                    Result.success(ShiftData(
                        id = activeShift.id,
                        startAt = activeShift.start_at,
                        status = activeShift.status
                    ))
                } else {
                    android.util.Log.d("ShiftRepository", "ℹ️ No active shift found (this is normal)")
                    // Очищаем старые неактивные смены из кеша
                    shiftDao.clearInactiveShifts()
                    Result.success(null)
                }
            } else {
                android.util.Log.e("ShiftRepository", "Failed to get shifts: code=${response.code()}")

                // 401/403 — авторизация протухла, не возвращаем stale кэш
                if (response.code() == 401 || response.code() == 403) {
                    shiftDao.clearInactiveShifts()
                    return Result.failure(Exception(parseErrorMessage(response.code())))
                }

                // Для других ошибок — stale кэш только если свежий (< 10 мин)
                if (cachedShift != null && (now - cachedShift.lastSyncAt) < 600_000L) {
                    android.util.Log.d("ShiftRepository", "⚠️ Using recent cache due to API error")
                    return Result.success(ShiftData(
                        id = cachedShift.id,
                        startAt = cachedShift.startAt,
                        status = cachedShift.status
                    ))
                }

                // Старый кэш — не доверяем, очищаем
                shiftDao.clearInactiveShifts()
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("ShiftRepository", "Exception getting active shift", e)

            // Офлайн: возвращаем любую активную смену из Room
            val cachedShift = shiftDao.getActiveShift()
            if (cachedShift != null) {
                // Локальные смены (local-*) всегда возвращаем — они были созданы офлайн
                if (cachedShift.id.startsWith("local-") || cachedShift.syncStatus == "pending") {
                    android.util.Log.d("ShiftRepository", "📱 Offline mode: returning local shift ${cachedShift.id}")
                    return Result.success(ShiftData(
                        id = cachedShift.id,
                        startAt = cachedShift.startAt,
                        status = cachedShift.status
                    ))
                }
                // Серверные смены — возвращаем если не слишком старый кэш
                val cacheAge = System.currentTimeMillis() - cachedShift.lastSyncAt
                if (cacheAge < 600_000L) {
                    android.util.Log.d("ShiftRepository", "📱 Offline mode: using cached shift (${cacheAge / 1000}s old)")
                    return Result.success(ShiftData(
                        id = cachedShift.id,
                        startAt = cachedShift.startAt,
                        status = cachedShift.status
                    ))
                } else {
                    android.util.Log.d("ShiftRepository", "⚠️ Cache too old (${cacheAge / 1000}s), discarding")
                    shiftDao.clearInactiveShifts()
                }
            }

            Result.failure(Exception("Ошибка получения активной смены: ${e.message}", e))
        }
    }

    override suspend fun getShiftDetails(shiftId: String): Result<ShiftDetailData> {
        return try {
            val response = shiftApi.getShift(shiftId)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(ShiftDetailData(
                    id = body.id,
                    userId = body.user_id,
                    startAt = body.start_at,
                    endAt = body.end_at,
                    status = body.status,
                    location = body.location,
                    notes = body.notes
                ))
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки деталей смены: ${e.message}", e))
        }
    }

    override suspend fun getShiftPhotos(shiftId: String): Result<List<ShiftPhotoData>> {
        return try {
            val response = shiftApi.getShiftPhotos(shiftId)

            if (response.isSuccessful && response.body() != null) {
                val photos = response.body()!!.map { photo ->
                    ShiftPhotoData(
                        id = photo.id,
                        shiftId = photo.shift_id,
                        hourLabel = photo.hour_label,
                        status = photo.status,
                        comment = photo.comment,
                        photoUrl = photo.photo_url,
                        createdAt = photo.created_at
                    )
                }
                Result.success(photos)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки фотографий: ${e.message}", e))
        }
    }

    override suspend fun syncPhotosFromServer(shiftId: String): Result<Unit> {
        return try {
            android.util.Log.d("ShiftRepository", "syncPhotosFromServer called for shift: $shiftId")
            // Просто загружаем фотографии с сервера, чтобы обновить кеш
            getShiftPhotos(shiftId)
            android.util.Log.d("ShiftRepository", "syncPhotosFromServer completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ShiftRepository", "syncPhotosFromServer failed", e)
            Result.failure(e)
        }
    }

    /**
     * Наблюдать за активной сменой в реальном времени через Flow
     * Возвращает данные из локальной БД с автоматическим обновлением
     */
    override fun observeActiveShift(): Flow<ShiftData?> {
        return shiftDao.getActiveShiftFlow().map { entity ->
            entity?.let {
                ShiftData(
                    id = it.id,
                    startAt = it.startAt,
                    status = it.status
                )
            }
        }
    }

    /**
     * Синхронизировать смены, ожидающие отправки на сервер.
     *
     * Обрабатывает два типа pending-смен:
     * 1. Локальные старты (id начинается с "local-") — создаём смену на сервере,
     *    обновляем shiftId в Room и во всех связанных PhotoEntity
     * 2. Локальные финиши (status="finished", syncStatus="pending") — отправляем finish на сервер
     */
    override suspend fun syncPendingShifts(): Result<Unit> {
        return try {
            android.util.Log.d("ShiftRepository", "=== SYNCING PENDING SHIFTS ===")
            val pendingShifts = shiftDao.getPendingShifts()

            if (pendingShifts.isEmpty()) {
                android.util.Log.d("ShiftRepository", "No pending shifts to sync")
                return Result.success(Unit)
            }

            android.util.Log.d("ShiftRepository", "Found ${pendingShifts.size} pending shifts")

            var successCount = 0
            var errorCount = 0

            pendingShifts.forEach { shift ->
                try {
                    if (shift.id.startsWith("local-")) {
                        // Тип 1: Локальный старт — нужно создать смену на сервере
                        syncLocalStart(shift)?.let { successCount++ } ?: errorCount++
                    } else if (shift.status == "finished") {
                        // Тип 2: Локальный финиш — отправляем finish
                        syncLocalFinish(shift)?.let { successCount++ } ?: errorCount++
                    } else {
                        // Неизвестный тип pending — пробуем как finish
                        syncLocalFinish(shift)?.let { successCount++ } ?: errorCount++
                    }
                } catch (e: Exception) {
                    shiftDao.updateSyncStatus(shift.id, "error")
                    errorCount++
                    android.util.Log.e("ShiftRepository", "❌ Exception syncing shift: ${shift.id}", e)
                }
            }

            android.util.Log.d("ShiftRepository", "Sync complete: success=$successCount, errors=$errorCount")

            if (errorCount > 0) {
                Result.failure(Exception("Синхронизировано $successCount из ${pendingShifts.size} смен"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("ShiftRepository", "syncPendingShifts failed", e)
            Result.failure(e)
        }
    }

    /**
     * Синхронизация локально созданной смены: отправляем POST /shifts/start,
     * получаем серверный ID, обновляем все связанные записи.
     */
    private suspend fun syncLocalStart(shift: ShiftEntity): Unit? {
        val oldId = shift.id
        android.util.Log.d("ShiftRepository", "Syncing local start: $oldId")

        val request = com.belsi.work.data.remote.api.StartShiftRequest(siteObjectId = null)
        val response = shiftApi.startShift(request)

        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            val newId = body.id

            // Обновляем shiftId во всех связанных фото
            photoDao.updateShiftId(oldId, newId)
            android.util.Log.d("ShiftRepository", "Reconciled photos: $oldId → $newId")

            // Удаляем старую запись, создаём новую с серверным ID
            shiftDao.deleteShift(oldId)
            shiftDao.insertShift(
                ShiftEntity(
                    id = newId,
                    userId = "current_user",
                    startAt = body.startAt,
                    status = if (shift.status == "finished") "active" else body.status,
                    syncStatus = if (shift.status == "finished") "pending" else "synced",
                    finishAt = shift.finishAt,
                    lastSyncAt = System.currentTimeMillis(),
                    startTimeMillis = shift.startTimeMillis,
                    elapsedSeconds = shift.elapsedSeconds,
                    totalPauseSeconds = shift.totalPauseSeconds,
                    totalIdleSeconds = shift.totalIdleSeconds
                )
            )
            android.util.Log.d("ShiftRepository", "✅ Local start synced: $oldId → $newId")

            // Если смена уже была завершена локально — сразу завершаем на сервере
            if (shift.status == "finished") {
                syncLocalFinish(shiftDao.getShiftById(newId) ?: return Unit)
            }

            return Unit
        } else {
            android.util.Log.e("ShiftRepository", "❌ Failed to sync local start: ${response.code()}")
            shiftDao.updateSyncStatus(oldId, "error")
            return null
        }
    }

    /**
     * Синхронизация локально завершённой смены: отправляем POST /shifts/finish.
     */
    private suspend fun syncLocalFinish(shift: ShiftEntity): Unit? {
        android.util.Log.d("ShiftRepository", "Syncing local finish: ${shift.id}")

        val request = com.belsi.work.data.remote.api.FinishShiftRequest(shiftId = shift.id)
        val response = shiftApi.finishShift(request)

        if (response.isSuccessful) {
            shiftDao.updateSyncStatus(shift.id, "synced")
            android.util.Log.d("ShiftRepository", "✅ Local finish synced: ${shift.id}")
            return Unit
        } else {
            // Если 400 и уже завершена — считаем успехом
            val errorBody = response.errorBody()?.string()
            if (response.code() == 400 && errorBody?.contains("finished") == true) {
                shiftDao.updateSyncStatus(shift.id, "synced")
                android.util.Log.d("ShiftRepository", "✅ Shift already finished on server: ${shift.id}")
                return Unit
            }
            shiftDao.updateSyncStatus(shift.id, "error")
            android.util.Log.e("ShiftRepository", "❌ Failed to sync finish: ${shift.id}, code=${response.code()}")
            return null
        }
    }

    private fun parseErrorMessage(code: Int, errorBody: String? = null): String {
        val baseMessage = when (code) {
            400 -> "Неверный формат данных"
            401 -> "Требуется авторизация. Войдите заново"
            403 -> "Доступ запрещен. Проверьте права доступа"
            404 -> "Ресурс не найден"
            408 -> "Превышено время ожидания. Попробуйте снова"
            413 -> "Файл слишком большой"
            422 -> "Неверные данные запроса"
            429 -> "Слишком много запросов. Подождите немного"
            500 -> "Внутренняя ошибка сервера"
            502 -> "Сервер недоступен"
            503 -> "Сервис временно недоступен"
            504 -> "Превышено время ответа сервера"
            else -> "Ошибка сети (код: $code)"
        }

        // Добавляем детали ошибки если есть
        return if (!errorBody.isNullOrBlank() && errorBody.length < 200) {
            "$baseMessage: $errorBody"
        } else {
            baseMessage
        }
    }
}
