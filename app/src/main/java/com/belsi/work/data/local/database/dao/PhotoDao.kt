package com.belsi.work.data.local.database.dao

import androidx.room.*
import com.belsi.work.data.local.database.entities.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    fun observePhotosByShiftId(shiftId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    suspend fun getPhotosByShiftId(shiftId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE status = 'LOCAL' OR status = 'UPLOADING' ORDER BY createdAt ASC")
    suspend fun getPendingPhotos(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE status = 'LOCAL' OR status = 'UPLOADING' ORDER BY createdAt ASC")
    fun observePendingPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY createdAt DESC")
    suspend fun getAllPhotos(): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Query("UPDATE photos SET status = :status WHERE id = :photoId")
    suspend fun updatePhotoStatus(photoId: String, status: String)

    @Query("UPDATE photos SET status = :status, remoteUrl = :remoteUrl, uploadProgress = 100 WHERE id = :photoId")
    suspend fun markPhotoAsUploaded(
        photoId: String,
        status: String,
        remoteUrl: String
    )

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: String)

    @Query("DELETE FROM photos WHERE status = 'UPLOADED' AND createdAt < :timestampMillis")
    suspend fun deleteOldUploadedPhotos(timestampMillis: Long)

    // === Offline queue queries ===

    /**
     * Фото для фоновой загрузки: LOCAL, retryCount < 5, отсортированные по времени
     */
    @Query("""
        SELECT * FROM photos
        WHERE status = 'LOCAL' AND retryCount < 5
        ORDER BY createdAt ASC
        LIMIT 10
    """)
    suspend fun getPendingPhotosForUpload(): List<PhotoEntity>

    /**
     * Количество фото в очереди (для бэйджа в UI)
     */
    @Query("SELECT COUNT(*) FROM photos WHERE status = 'LOCAL' OR status = 'UPLOADING'")
    fun observePendingPhotoCount(): Flow<Int>

    /**
     * Синхронная версия для worker
     */
    @Query("SELECT COUNT(*) FROM photos WHERE status = 'LOCAL' OR status = 'UPLOADING'")
    suspend fun getPendingPhotoCount(): Int

    /**
     * Увеличить счётчик попыток и обновить время
     */
    @Query("UPDATE photos SET retryCount = retryCount + 1, lastRetryAt = :now, status = 'LOCAL' WHERE id = :photoId")
    suspend fun incrementRetryCount(photoId: String, now: Long = System.currentTimeMillis())

    /**
     * Обновить shiftId для фото при reconciliation (offline shift → server ID)
     */
    @Query("UPDATE photos SET shiftId = :newShiftId WHERE shiftId = :oldShiftId")
    suspend fun updateShiftId(oldShiftId: String, newShiftId: String)

    /**
     * Сбросить retryCount для всех застрявших фото (после починки сервера)
     */
    @Query("UPDATE photos SET retryCount = 0, status = 'LOCAL' WHERE retryCount >= 5 AND status = 'LOCAL'")
    suspend fun resetFailedPhotos(): Int
}
