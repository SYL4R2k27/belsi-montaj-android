package com.belsi.work.data.local.dao

import androidx.room.*
import com.belsi.work.data.local.entities.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    
    @Query("SELECT * FROM photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: String): PhotoEntity?
    
    @Query("SELECT * FROM photos WHERE shiftId = :shiftId ORDER BY slotIndex ASC")
    suspend fun getPhotosByShiftId(shiftId: String): List<PhotoEntity>
    
    @Query("SELECT * FROM photos WHERE shiftId = :shiftId ORDER BY slotIndex ASC")
    fun observePhotosByShiftId(shiftId: String): Flow<List<PhotoEntity>>
    
    @Query("SELECT * FROM photos WHERE status = 'LOCAL' OR status = 'UPLOADING'")
    suspend fun getPendingPhotos(): List<PhotoEntity>
    
    @Query("SELECT * FROM photos WHERE status = 'LOCAL' OR status = 'UPLOADING'")
    fun observePendingPhotos(): Flow<List<PhotoEntity>>
    
    @Query("SELECT * FROM photos ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentPhotos(limit: Int = 50): List<PhotoEntity>

    @Query("SELECT * FROM photos ORDER BY createdAt DESC")
    suspend fun getAllPhotos(): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)
    
    @Update
    suspend fun updatePhoto(photo: PhotoEntity)
    
    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)
    
    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: String)
    
    @Query("DELETE FROM photos WHERE shiftId = :shiftId")
    suspend fun deletePhotosByShiftId(shiftId: String)
}
