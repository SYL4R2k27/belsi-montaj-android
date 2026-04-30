package com.belsi.work.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey
    val id: String,
    val hourLabel: String,
    val localPath: String?,
    val remoteUrl: String?,
    val status: String,
    val createdAt: Long,
    val slotIndex: Int,
    val shiftId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val comment: String?,
    val rejectionReason: String?,
    val uploadProgress: Int
)
