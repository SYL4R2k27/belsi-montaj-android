package com.belsi.work.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш задач для offline просмотра
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String? = null,
    val assignedTo: String,
    val createdBy: String,
    val status: String,
    val priority: String,
    val dueAt: String? = null,
    val createdAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
