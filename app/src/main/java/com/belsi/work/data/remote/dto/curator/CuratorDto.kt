package com.belsi.work.data.remote.dto.curator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// /curator/foremen — возвращает List<CuratorForemanDto> (массив!)
// ============================================================

@Serializable
data class CuratorInstallerDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("last_photo_status") val lastPhotoStatus: String? = null,
    @SerialName("pending_photos_count") val pendingPhotosCount: Int = 0,
    @SerialName("total_shifts") val totalShifts: Int = 0,
    @SerialName("total_hours") val totalHours: Double = 0.0
)

@Serializable
data class CuratorForemanDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("team_size") val teamSize: Int = 0,
    @SerialName("active_installers_count") val activeInstallersCount: Int = 0,
    @SerialName("total_shifts_today") val totalShiftsToday: Int = 0,
    @SerialName("tools_count") val toolsCount: Int = 0,
    @SerialName("active_tools_issued") val activeToolsIssued: Int = 0,
    @SerialName("pending_photos_count") val pendingPhotosCount: Int = 0,
    @SerialName("completion_percentage") val completionPercentage: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("installers") val installers: List<CuratorInstallerDto> = emptyList()
)

// ============================================================
// /curator/dashboard — объект CuratorDashboardDto
// ============================================================

@Serializable
data class CuratorDashboardDto(
    @SerialName("total_installers") val totalInstallers: Int = 0,
    @SerialName("active_installers_today") val activeInstallersToday: Int = 0,
    @SerialName("total_foremen") val totalForemen: Int = 0,
    @SerialName("active_foremen_today") val activeForemenToday: Int = 0,
    @SerialName("pending_photos") val pendingPhotos: Int = 0,
    @SerialName("total_shifts_today") val totalShiftsToday: Int = 0,
    @SerialName("total_tools") val totalTools: Int = 0,
    @SerialName("tools_issued") val toolsIssued: Int = 0,
    @SerialName("open_support_tickets") val openSupportTickets: Int = 0,
    @SerialName("average_completion_percentage") val averageCompletionPercentage: Double = 0.0,
    @SerialName("total_coordinators") val totalCoordinators: Int = 0,
    @SerialName("active_coordinators_today") val activeCoordinatorsToday: Int = 0
)

// ============================================================
// /curator/photos — возвращает {"photos": [...]}
// ============================================================

/**
 * Wrapper для ответа /curator/photos
 */
@Serializable
data class CuratorPhotosResponse(
    @SerialName("photos") val photos: List<CuratorPhotoDto> = emptyList()
)

@Serializable
data class CuratorPhotoDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_phone") val userPhone: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("foreman_id") val foremanId: String? = null,
    @SerialName("foreman_name") val foremanName: String? = null,
    @SerialName("photo_url") val photoUrl: String = "",
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("comment") val comment: String? = null,
    @SerialName("category") val category: String? = "hourly",
    @SerialName("ai_comment") val aiComment: String? = null,
    @SerialName("ai_score") val aiScore: Int? = null,
    @SerialName("ai_category") val aiCategory: String = "unknown"
)

// ============================================================
// /curator/support — List<CuratorSupportTicketDto> (массив!)
// ============================================================

@Serializable
data class CuratorSupportTicketDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_phone") val userPhone: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("last_message_snippet") val lastMessageSnippet: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("status") val status: String = "open",
    @SerialName("category") val category: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ============================================================
// /curator/tools/transactions — пагинированный ответ
// ============================================================

@Serializable
data class CuratorToolTransactionDto(
    @SerialName("id") val id: String,
    @SerialName("tool_id") val toolId: String = "",
    @SerialName("installer_id") val installerId: String = "",
    @SerialName("issued_by") val issuedBy: String = "",
    @SerialName("issued_at") val issuedAt: String? = null,
    @SerialName("issue_comment") val issueComment: String? = null,
    @SerialName("issue_photo_url") val issuePhotoUrl: String? = null,
    @SerialName("returned_at") val returnedAt: String? = null,
    @SerialName("returned_to") val returnedTo: String? = null,
    @SerialName("return_condition") val returnCondition: String? = null,
    @SerialName("return_comment") val returnComment: String? = null,
    @SerialName("return_photo_url") val returnPhotoUrl: String? = null,
    @SerialName("status") val status: String = "issued",
    @SerialName("created_at") val createdAt: String? = null
) {
    val isActive: Boolean get() = status == "issued"
    val isReturned: Boolean get() = status == "returned"
}

@Serializable
data class CuratorToolTransactionsResponse(
    @SerialName("items") val items: List<CuratorToolTransactionDto> = emptyList(),
    @SerialName("page") val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_items") val totalItems: Int = 0
)

// ============================================================
// DTO для детальных страниц пользователей (CuratorUserDetailScreen, CuratorTasksScreen)
// ============================================================

/**
 * DTO для бригадира с полной информацией и списком монтажников
 * Используется в CuratorUserDetailScreen и CuratorTasksScreen
 */
@Serializable
data class ForemanDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("team_size") val teamSize: Int = 0,
    @SerialName("active_installers_count") val activeInstallersCount: Int = 0,
    @SerialName("total_shifts_today") val totalShiftsToday: Int = 0,
    @SerialName("tools_count") val toolsCount: Int = 0,
    @SerialName("active_tools_issued") val activeToolsIssued: Int = 0,
    @SerialName("pending_photos_count") val pendingPhotosCount: Int = 0,
    @SerialName("completion_percentage") val completionPercentage: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("installers") val installers: List<InstallerDto> = emptyList()
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return name.ifBlank { fullName.ifBlank { phone } }
        }
}

/**
 * DTO для монтажника (закрепленного за бригадиром)
 * Используется в CuratorUserDetailScreen и CuratorTasksScreen
 */
@Serializable
data class InstallerDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("last_photo_status") val lastPhotoStatus: String? = null,
    @SerialName("pending_photos_count") val pendingPhotosCount: Int = 0,
    @SerialName("total_shifts") val totalShifts: Int = 0,
    @SerialName("total_hours") val totalHours: Double = 0.0,
    @SerialName("is_active_today") val isActiveToday: Boolean = false
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return name.ifBlank { fullName.ifBlank { phone } }
        }
}

/**
 * DTO для монтажника без бригадира
 * Используется в CuratorUserDetailScreen и CuratorTasksScreen
 */
@Serializable
data class UnassignedInstallerDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("pending_photos_count") val pendingPhotosCount: Int = 0,
    @SerialName("total_shifts") val totalShifts: Int = 0,
    @SerialName("total_hours") val totalHours: Double = 0.0,
    @SerialName("is_active_today") val isActiveToday: Boolean = false
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return name.ifBlank { fullName.ifBlank { phone } }
        }
}

// ============================================================
// /curator/users/all - Все пользователи
// ============================================================

/**
 * DTO для пользователя в общем списке
 */
@Serializable
data class AllUserDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("role") val role: String = "installer",
    @SerialName("foreman_id") val foremanId: String? = null,
    @SerialName("foreman_name") val foremanName: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("is_active_today") val isActiveToday: Boolean = false,
    @SerialName("pending_photos_count") val pendingPhotosCount: Int = 0,
    @SerialName("total_shifts") val totalShifts: Int = 0,
    @SerialName("total_hours") val totalHours: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return name.ifBlank { fullName?.ifBlank { phone } ?: phone }
        }

    val isForeman: Boolean get() = role == "foreman"
    val isInstaller: Boolean get() = role == "installer"
    val isCoordinator: Boolean get() = role == "coordinator"
}

// ============================================================
// /curator/users/{id} - Детальная информация о пользователе
// ============================================================

/**
 * Член команды бригадира
 */
@Serializable
data class TeamMemberDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("is_active_today") val isActiveToday: Boolean = false
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return name.ifBlank { fullName?.ifBlank { phone } ?: phone }
        }
}

/**
 * DTO с детальной информацией о пользователе
 */
@Serializable
data class UserDetailDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("role") val role: String = "installer",

    // Связи
    @SerialName("foreman_id") val foremanId: String? = null,
    @SerialName("foreman_name") val foremanName: String? = null,
    @SerialName("team_members") val teamMembers: List<TeamMemberDto> = emptyList(),

    // Статистика смен
    @SerialName("total_shifts") val totalShifts: Int = 0,
    @SerialName("total_hours") val totalHours: Double = 0.0,

    // Статистика фото
    @SerialName("pending_photos_count") val pendingPhotosCount: Int = 0,
    @SerialName("approved_photos_count") val approvedPhotosCount: Int = 0,
    @SerialName("rejected_photos_count") val rejectedPhotosCount: Int = 0,

    // Задачи
    @SerialName("active_tasks_count") val activeTasksCount: Int = 0,
    @SerialName("completed_tasks_count") val completedTasksCount: Int = 0,

    // Активность
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("current_shift_id") val currentShiftId: String? = null,
    @SerialName("is_on_shift") val isOnShift: Boolean = false,

    // Детали текущей смены
    @SerialName("current_shift_start_at") val currentShiftStartAt: String? = null,
    @SerialName("current_shift_photos_count") val currentShiftPhotosCount: Int = 0,
    @SerialName("current_shift_elapsed_hours") val currentShiftElapsedHours: Double = 0.0,

    // Статус смены / паузы / простой
    @SerialName("shift_status") val shiftStatus: String = "working",
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("is_idle") val isIdle: Boolean = false,
    @SerialName("current_pause_reason") val currentPauseReason: String? = null,
    @SerialName("current_shift_pause_seconds") val currentShiftPauseSeconds: Int = 0,
    @SerialName("current_shift_idle_seconds") val currentShiftIdleSeconds: Int = 0,
    @SerialName("total_pause_duration") val totalPauseDuration: Double? = null,
    @SerialName("total_idle_duration") val totalIdleDuration: Double? = null,

    // Объект
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("site_address") val siteAddress: String? = null,
    @SerialName("site_status") val siteStatus: String? = null,
    @SerialName("site_measurements") val siteMeasurements: Map<String, String> = emptyMap(),
    @SerialName("site_comments") val siteComments: String? = null,

    @SerialName("created_at") val createdAt: String? = null
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return name.ifBlank { fullName?.ifBlank { phone } ?: phone }
        }

    val isForeman: Boolean get() = role == "foreman"
    val isInstaller: Boolean get() = role == "installer"
    val isCoordinator: Boolean get() = role == "coordinator"
    val hasTeam: Boolean get() = teamMembers.isNotEmpty()
}
