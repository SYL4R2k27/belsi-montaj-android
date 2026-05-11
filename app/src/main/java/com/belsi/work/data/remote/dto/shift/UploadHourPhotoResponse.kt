package com.belsi.work.data.remote.dto.shift

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ответ на POST /shift/hour/photo
 *
 * Example:
 * {
 *   "id": "UUID-строка",
 *   "shift_id": "UUID-строка",
 *   "hour_label": "2025-12-08T10:00:00+03:00",
 *   "status": "pending",
 *   "comment": null,
 *   "photo_url": "https://bucket.api.belsi.ru/shift_photos/....jpg",
 *   "created_at": "2025-12-08T10:01:23+03:00"
 * }
 */
@Serializable
data class UploadHourPhotoResponse(
    @SerialName("id")
    val id: String,

    @SerialName("shift_id")
    val shiftId: String,

    @SerialName("hour_label")
    val hourLabel: String,

    @SerialName("status")
    val status: String,

    @SerialName("comment")
    val comment: String? = null,

    @SerialName("photo_url")
    val photoUrl: String,

    @SerialName("category")
    val category: String = "hourly",

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("ai_comment")
    val aiComment: String? = null
)
