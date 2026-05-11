package com.belsi.work.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.belsi.work.data.models.ShiftStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val startedAt: Long?,
    val endedAt: Long?,
    val status: String,
    val totalElapsed: Long,
    val pauseElapsed: Long,
    val pauseStartedAt: Long?,
    val objectName: String?,
    val objectAddress: String?,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shift_slots")
data class ShiftSlotEntity(
    @PrimaryKey
    val id: String,
    val shiftId: String,
    val index: Int,
    val plannedStart: Long,
    val status: String,
    val localPhotoID: String?,
    val remotePhotoUrl: String?,
    val rejectionReason: String?
)

class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        val listType = object : TypeToken<List<String>>() {}.type
        return value?.let { gson.fromJson(it, listType) }
    }
    
    @TypeConverter
    fun toStringList(list: List<String>?): String? {
        return gson.toJson(list)
    }
    
    @TypeConverter
    fun fromMap(value: String?): Map<String, String>? {
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return value?.let { gson.fromJson(it, mapType) }
    }
    
    @TypeConverter
    fun toMap(map: Map<String, String>?): String? {
        return gson.toJson(map)
    }
}
