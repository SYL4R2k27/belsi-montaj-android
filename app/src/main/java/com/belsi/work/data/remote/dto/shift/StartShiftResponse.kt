package com.belsi.work.data.remote.dto.shift

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ответ на POST /shifts/start
 *
 * Example:
 * {
 *   "id": "UUID-строка",
 *   "start_at": "2025-12-08T09:15:00+03:00",
 *   "status": "active"
 * }
 */
@Serializable
data class StartShiftResponse(
    @SerialName("id")
    val id: String,

    @SerialName("start_at")
    val startAt: String,

    @SerialName("status")
    val status: String = "active"
)
