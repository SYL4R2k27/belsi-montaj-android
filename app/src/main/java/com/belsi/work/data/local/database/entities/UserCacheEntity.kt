package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш данных пользователя
 */
@Entity(tableName = "user_cache")
data class UserCacheEntity(
    @PrimaryKey
    val id: String,
    val phone: String,
    val fullName: String? = null,
    val role: String,
    val cachedAt: Long = System.currentTimeMillis()
)
