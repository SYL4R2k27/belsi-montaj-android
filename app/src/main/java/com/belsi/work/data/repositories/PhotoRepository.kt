package com.belsi.work.data.repositories

import com.belsi.work.data.local.database.dao.PhotoDao
import com.belsi.work.data.local.database.entities.PhotoEntity
import com.belsi.work.data.models.PhotoStatus
import com.belsi.work.data.models.ShiftPhoto
import com.belsi.work.data.remote.api.PhotoApi
import com.belsi.work.data.remote.api.PhotoReviewRequest
import com.belsi.work.data.remote.api.ShiftApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

interface PhotoRepository {
    suspend fun savePhotoLocally(photo: ShiftPhoto)
    suspend fun getPhotosByShiftId(shiftId: String): List<ShiftPhoto>
    fun observePhotosByShiftId(shiftId: String): Flow<List<ShiftPhoto>>
    suspend fun getPendingPhotos(): List<ShiftPhoto>
    fun observePendingPhotos(): Flow<List<ShiftPhoto>>
    suspend fun getAllPhotos(): Result<List<ShiftPhoto>>
    suspend fun syncPhotosFromServer(shiftId: String): Result<Unit>
    suspend fun deletePhoto(photoId: String): Result<Unit>
    suspend fun reviewPhoto(photoId: String, status: String, comment: String? = null): Result<Unit>
}

@Singleton
class PhotoRepositoryImpl @Inject constructor(
    private val photoApi: PhotoApi,
    private val shiftApi: ShiftApi,
    private val photoDao: PhotoDao
) : PhotoRepository {
    
    override suspend fun savePhotoLocally(photo: ShiftPhoto) {
        val entity = modelToEntity(photo)
        photoDao.insertPhoto(entity)
    }
    
    // FIX(2026-04-30): Удалена дублирующая функция uploadPhoto.
    // Все upload'ы фото теперь идут только через PhotoUploadWorker — один путь,
    // одна логика retry/backoff/markAsUploaded.

    override suspend fun getPhotosByShiftId(shiftId: String): List<ShiftPhoto> {
        return photoDao.getPhotosByShiftId(shiftId).map { entityToModel(it) }
    }

    override fun observePhotosByShiftId(shiftId: String): Flow<List<ShiftPhoto>> {
        return photoDao.observePhotosByShiftId(shiftId).map { entities ->
            entities.map { entityToModel(it) }
        }
    }
    
    override suspend fun getPendingPhotos(): List<ShiftPhoto> {
        return photoDao.getPendingPhotos().map { entityToModel(it) }
    }
    
    override fun observePendingPhotos(): Flow<List<ShiftPhoto>> {
        return photoDao.observePendingPhotos().map { entities ->
            entities.map { entityToModel(it) }
        }
    }
    
    private fun modelToEntity(photo: ShiftPhoto): PhotoEntity {
        return PhotoEntity(
            id = photo.id,
            hourLabel = photo.hourLabel,
            localPath = photo.localPath,
            remoteUrl = photo.remoteUrl,
            status = photo.status.name,
            createdAt = photo.createdAt,
            slotIndex = photo.slotIndex,
            shiftId = photo.shiftId ?: "", // Default to empty if null
            latitude = photo.latitude,
            longitude = photo.longitude,
            comment = photo.comment,
            rejectionReason = photo.rejectionReason,
            uploadProgress = photo.uploadProgress ?: 0
        )
    }

    private fun entityToModel(entity: PhotoEntity): ShiftPhoto {
        return ShiftPhoto(
            id = entity.id,
            hourLabel = entity.hourLabel,
            localPath = entity.localPath,
            url = entity.remoteUrl,
            remoteUrl = entity.remoteUrl,
            status = PhotoStatus.valueOf(entity.status),
            createdAt = entity.createdAt,
            slotIndex = entity.slotIndex,
            shiftId = entity.shiftId,
            latitude = entity.latitude ?: 0.0,
            longitude = entity.longitude ?: 0.0,
            comment = entity.comment,
            rejectionReason = entity.rejectionReason,
            uploadProgress = entity.uploadProgress
        )
    }

    override suspend fun getAllPhotos(): Result<List<ShiftPhoto>> {
        return try {
            android.util.Log.d("PhotoRepository", "getAllPhotos called")
            val photos = photoDao.getAllPhotos().map { entityToModel(it) }
            android.util.Log.d("PhotoRepository", "Loaded ${photos.size} photos from database")
            photos.forEachIndexed { index, photo ->
                android.util.Log.d("PhotoRepository", "Photo $index: id=${photo.id}, hourLabel=${photo.hourLabel}, status=${photo.status}, remoteUrl=${photo.remoteUrl}, localPath=${photo.localPath}")
            }
            Result.success(photos)
        } catch (e: Exception) {
            android.util.Log.e("PhotoRepository", "Failed to load photos", e)
            Result.failure(e)
        }
    }

    override suspend fun syncPhotosFromServer(shiftId: String): Result<Unit> {
        return try {
            android.util.Log.d("PhotoRepository", "syncPhotosFromServer called for shift: $shiftId")
            val response = shiftApi.getShiftPhotos(shiftId)

            if (response.isSuccessful && response.body() != null) {
                val serverPhotos = response.body()!!
                android.util.Log.d("PhotoRepository", "Received ${serverPhotos.size} photos from server")

                serverPhotos.forEach { serverPhoto ->
                    android.util.Log.d("PhotoRepository", "Processing server photo: id=${serverPhoto.id}, hour=${serverPhoto.hour_label}, url=${serverPhoto.photo_url}")

                    // Проверяем, есть ли уже это фото в базе
                    val existingPhotos = photoDao.getAllPhotos()
                    val exists = existingPhotos.any { it.id == serverPhoto.id }

                    if (!exists) {
                        android.util.Log.d("PhotoRepository", "New photo from server, saving: ${serverPhoto.id}")
                        // Создаем модель фото из серверного ответа
                        val photo = ShiftPhoto(
                            id = serverPhoto.id,
                            shiftId = serverPhoto.shift_id,
                            hourLabel = serverPhoto.hour_label ?: "",
                            localPath = null,
                            remoteUrl = serverPhoto.photo_url,
                            url = serverPhoto.photo_url,
                            status = when (serverPhoto.status) {
                                "approved" -> PhotoStatus.APPROVED
                                "rejected" -> PhotoStatus.REJECTED
                                "pending" -> PhotoStatus.UPLOADED
                                else -> PhotoStatus.UPLOADED
                            },
                            createdAt = System.currentTimeMillis(),
                            slotIndex = 0,
                            comment = serverPhoto.comment
                        )
                        savePhotoLocally(photo)
                    } else {
                        android.util.Log.d("PhotoRepository", "Photo already exists in DB: ${serverPhoto.id}")
                    }
                }

                android.util.Log.d("PhotoRepository", "Sync completed successfully")
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("PhotoRepository", "Failed to sync photos. Code: ${response.code()}, Error: $errorBody")
                Result.failure(Exception("Failed to sync photos: $errorBody"))
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoRepository", "syncPhotosFromServer exception", e)
            Result.failure(e)
        }
    }

    override suspend fun deletePhoto(photoId: String): Result<Unit> {
        return try {
            photoDao.deletePhotoById(photoId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reviewPhoto(photoId: String, status: String, comment: String?): Result<Unit> {
        return try {
            val response = photoApi.reviewPhoto(photoId, PhotoReviewRequest(status = status, comment = comment))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Ошибка проверки фото: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка проверки фото: ${e.message}", e))
        }
    }
}
