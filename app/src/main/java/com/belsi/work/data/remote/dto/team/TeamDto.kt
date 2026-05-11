package com.belsi.work.data.remote.dto.team

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs для бригадира - полностью соответствуют ответам сервера
 *
 * GET /foreman/team → ForemanTeamResponse {"items": [...], "count": N}
 * GET /foreman/photos → ForemanPhotosResponse {"photos": [...]}
 * GET /foreman/photos/latest → {"photos": [...]}
 * GET /foreman/tools → List<ForemanToolDto> (прямой массив)
 * GET /foreman/tools/history → List<ForemanToolTransactionDto> (прямой массив)
 * GET /tasks/created → List<ForemanTaskDto> (прямой массив)
 */

// ==========================================
// GET /foreman/team
// Ответ: {"items": [...], "count": N}
// Источник: foreman_team.py → ForemanTeamOut, ForemanTeamMemberOut
// ==========================================

@Serializable
data class ForemanTeamResponse(
    val items: List<ForemanTeamMemberDto> = emptyList(),
    val count: Int? = null
)

@Serializable
data class ForemanTeamMemberDto(
    val id: String? = null,
    val phone: String,
    @SerialName("user_id") val userId: String? = null,
    val role: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,

    // Статус работы
    @SerialName("last_shift_at") val lastShiftAt: String? = null,
    @SerialName("active_shift_id") val activeShiftId: String? = null,
    @SerialName("is_working_now") val isWorkingNow: Boolean = false,

    // Фото
    @SerialName("last_photo_at") val lastPhotoAt: String? = null,
    @SerialName("pending_photos_count") val pendingPhotosCount: Int = 0,

    // Статистика
    @SerialName("total_shifts") val totalShifts: Int = 0,
    @SerialName("total_hours") val totalHours: Double = 0.0,

    // Когда присоединился
    @SerialName("joined_at") val joinedAt: String? = null
) {
    /**
     * Получить отображаемое имя (имя + фамилия или телефон)
     */
    fun displayName(): String {
        if (!fullName.isNullOrBlank()) return fullName
        val name = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return name.ifBlank { phone }
    }

    /**
     * Получить ID монтажника (id или userId)
     */
    fun memberId(): String = id ?: userId ?: phone
}

// ==========================================
// GET /foreman/team/{installerId} - детали монтажника
// ==========================================

@Serializable
data class InstallerDetailResponse(
    val member: ForemanTeamMemberDto,
    val photos: List<InstallerPhotoDto> = emptyList(),
    val shifts: List<InstallerShiftDto> = emptyList()
)

@Serializable
data class InstallerPhotoDto(
    val id: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val comment: String? = null,
    @SerialName("hour_label") val hourLabel: String? = null
)

@Serializable
data class InstallerShiftDto(
    val id: String,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("finish_at") val finishAt: String? = null,
    @SerialName("duration_hours") val durationHours: Double? = null,
    val status: String? = null
)

// ==========================================
// GET /foreman/photos
// Ответ: {"photos": [...]} (обёртка с массивом PhotoForReviewOut)
// Источник: foreman.py → PhotosListResponse
// ==========================================

@Serializable
data class ForemanPhotosResponse(
    val photos: List<ForemanPhotoDto> = emptyList()
)

@Serializable
data class ForemanPhotoDto(
    val id: String,
    @SerialName("installer_id") val installerId: String,
    @SerialName("installer_name") val installerName: String,
    @SerialName("shift_id") val shiftId: String,
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("hour_label") val hourLabel: String? = null,
    val status: String, // pending, approved, rejected
    val comment: String? = null,
    val category: String? = "hourly",
    @SerialName("created_at") val createdAt: String,
    @SerialName("ai_comment") val aiComment: String? = null,
    @SerialName("ai_score") val aiScore: Int? = null,
    @SerialName("ai_category") val aiCategory: String = "unknown"
)

// ==========================================
// GET /foreman/tools
// Ответ: [] (прямой массив ToolOut)
// Источник: foreman.py → List[ToolOut]
// ==========================================

@Serializable
data class ForemanToolDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val status: String, // available, issued, lost, repair
    @SerialName("issued_to_id") val issuedToId: String? = null,
    @SerialName("issued_to_name") val issuedToName: String? = null,
    @SerialName("issued_at") val issuedAt: String? = null
)

// ==========================================
// GET /foreman/tools/history
// Ответ: [] (прямой массив ToolTransactionOut)
// Источник: foreman.py → List[ToolTransactionOut]
// ==========================================

@Serializable
data class ForemanToolTransactionDto(
    val id: String,
    @SerialName("tool_id") val toolId: String,
    @SerialName("tool_name") val toolName: String,
    @SerialName("installer_id") val installerId: String,
    @SerialName("installer_name") val installerName: String,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("issue_comment") val issueComment: String? = null,
    @SerialName("returned_at") val returnedAt: String? = null,
    @SerialName("return_condition") val returnCondition: String? = null,
    @SerialName("return_comment") val returnComment: String? = null,
    val status: String // issued, returned
)

// ==========================================
// GET /tasks/created
// Ответ: [] массив задач
// ==========================================

@Serializable
data class ForemanTaskDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val status: String = "new", // new, in_progress, done
    val priority: String = "medium", // low, medium, high
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("assigned_to_name") val assignedToName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null
)

// ==========================================
// LEGACY - оставляем для совместимости с UserPickerDialog и другими экранами
// ==========================================

@Serializable
data class TeamMemberDto(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val phone: String = "",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("current_shift_id") val currentShiftId: String? = null,
    @SerialName("completed_hours") val completedHours: Int = 0
)

@Serializable
data class TeamPhotoDto(
    @SerialName("id") val id: String,
    @SerialName("installer_name") val installerName: String?,
    @SerialName("hour_label") val hourLabel: String?,
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("status") val status: String
)
