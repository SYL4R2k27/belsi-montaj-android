package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш инструментов для offline выбора
 */
@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    val cachedAt: Long = System.currentTimeMillis()
)
