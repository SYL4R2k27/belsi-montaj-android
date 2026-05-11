package com.belsi.work.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модель задачи
 */
@Serializable
data class Task(
    val id: String,
    @SerialName("created_by")
    val createdBy: String,
    @SerialName("assigned_to")
    val assignedTo: String,
    val title: String,
    val description: String? = null,
    val status: String,  // "new", "in_progress", "done", "cancelled"
    val priority: String,  // "low", "normal", "high"
    @SerialName("due_at")
    val dueAt: String? = null,
    val meta: Map<String, String>? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Статусы задач
 */
object TaskStatus {
    const val NEW = "new"
    const val IN_PROGRESS = "in_progress"
    const val DONE = "done"
    const val CANCELLED = "cancelled"
}

/**
 * Приоритеты задач
 */
object TaskPriority {
    const val LOW = "low"
    const val NORMAL = "normal"
    const val HIGH = "high"
}
