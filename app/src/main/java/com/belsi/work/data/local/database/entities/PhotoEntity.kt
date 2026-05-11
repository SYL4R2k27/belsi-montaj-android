package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Локальное хранилище фото для offline режима
 * Фото ВСЕГДА сохраняется локально первым делом, потом загружается в фоне.
 * PhotoUploadWorker подхватывает фото со статусом LOCAL и загружает на сервер.
 */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey
    val id: String,
    val shiftId: String,
    val hourLabel: String,
    val localPath: String?, // Путь к файлу на устройстве (может быть null для фото с сервера)
    val remoteUrl: String? = null, // URL после загрузки
    val status: String = "LOCAL", // LOCAL, UPLOADING, UPLOADED, APPROVED, REJECTED
    val createdAt: Long = System.currentTimeMillis(),
    val slotIndex: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val comment: String? = null,
    val rejectionReason: String? = null,
    val uploadProgress: Int = 0, // 0-100

    // === Offline queue fields ===
    val category: String = "hourly", // hourly, problem, question
    val aiComment: String? = null, // комментарий от AI после анализа
    val retryCount: Int = 0, // кол-во попыток загрузки
    val lastRetryAt: Long? = null // последняя попытка загрузки
)
