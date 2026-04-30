package com.belsi.work.data.models

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class PhotoStatus {
    LOCAL,
    UPLOADING,
    UPLOADED,    // На проверке
    APPROVED,
    REJECTED;

    fun getDisplayName(): String = when (this) {
        LOCAL -> "Локально"
        UPLOADING -> "Загрузка..."
        UPLOADED -> "На проверке"
        APPROVED -> "Одобрено"
        REJECTED -> "Отклонено"
    }

    fun getColor(): Color = when (this) {
        LOCAL -> Color(0xFF757575)      // Серый
        UPLOADING -> com.belsi.work.presentation.theme.Sky500  // Синий
        UPLOADED -> com.belsi.work.presentation.theme.Amber500   // Оранжевый - на проверке
        APPROVED -> com.belsi.work.presentation.theme.Emerald500   // Зелёный - одобрено
        REJECTED -> com.belsi.work.presentation.theme.Rose500   // Красный - отклонено
    }
}

data class ShiftPhoto(
    val id: String,
    val hourLabel: String = "",
    val localPath: String? = null,
    val url: String? = null,
    val remoteUrl: String? = null,
    var status: PhotoStatus = PhotoStatus.LOCAL,
    val createdAt: Long = System.currentTimeMillis(),
    val takenAt: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    val slotIndex: Int = 0,
    val shiftId: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val comment: String? = null,
    val rejectionReason: String? = null,
    val uploadProgress: Int? = null
) {
    val isLocal: Boolean
        get() = status == PhotoStatus.LOCAL

    val isUploaded: Boolean
        get() = status == PhotoStatus.UPLOADED || status == PhotoStatus.APPROVED

    val needsAction: Boolean
        get() = status == PhotoStatus.REJECTED

    val color: Color
        get() = status.getColor()
}

data class PhotoUploadProgress(
    val photoId: UUID,
    val progress: Int,
    val isComplete: Boolean = false,
    val error: String? = null
)
