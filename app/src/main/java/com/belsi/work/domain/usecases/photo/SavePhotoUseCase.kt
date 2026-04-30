package com.belsi.work.domain.usecases.photo

import com.belsi.work.data.models.PhotoStatus
import com.belsi.work.data.models.ShiftPhoto
import com.belsi.work.data.repositories.PhotoRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject

class SavePhotoUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    suspend operator fun invoke(
        file: File,
        shiftId: String,
        slotIndex: Int,
        hourLabel: String,
        latitude: Double?,
        longitude: Double?,
        comment: String?
    ): Result<ShiftPhoto> {
        return try {
            // Validate file
            if (!file.exists()) {
                return Result.failure(Exception("Файл не найден"))
            }

            if (file.length() == 0L) {
                return Result.failure(Exception("Файл пустой"))
            }

            // Create photo model
            val photo = ShiftPhoto(
                id = UUID.randomUUID().toString(),
                hourLabel = hourLabel,
                localPath = file.absolutePath,
                status = PhotoStatus.LOCAL,
                createdAt = System.currentTimeMillis(),
                slotIndex = slotIndex,
                shiftId = shiftId,
                latitude = latitude ?: 0.0,
                longitude = longitude ?: 0.0,
                comment = comment
            )
            
            // Save locally
            photoRepository.savePhotoLocally(photo)
            
            Result.success(photo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
