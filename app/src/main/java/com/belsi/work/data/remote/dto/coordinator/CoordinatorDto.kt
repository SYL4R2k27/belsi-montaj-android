package com.belsi.work.data.remote.dto.coordinator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// /coordinator/dashboard
// ============================================================

@Serializable
data class CoordinatorDashboardDto(
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("site_address") val siteAddress: String? = null,
    @SerialName("site_status") val siteStatus: String = "no_site",
    @SerialName("active_shift") val activeShift: Boolean = false,
    @SerialName("shift_duration_seconds") val shiftDurationSeconds: Int = 0,
    @SerialName("total_foremen") val totalForemen: Int = 0,
    @SerialName("total_installers") val totalInstallers: Int = 0,
    @SerialName("active_workers_today") val activeWorkersToday: Int = 0,
    @SerialName("pending_photos") val pendingPhotos: Int = 0,
    @SerialName("total_photos_today") val totalPhotosToday: Int = 0,
    @SerialName("tasks_total") val tasksTotal: Int = 0,
    @SerialName("tasks_completed") val tasksCompleted: Int = 0,
    @SerialName("reports_today") val reportsToday: Int = 0
)

// ============================================================
// /coordinator/photos → {"photos": [...]}
// ============================================================

@Serializable
data class CoordinatorPhotosResponse(
    @SerialName("photos") val photos: List<CoordinatorPhotoDto> = emptyList()
)

@Serializable
data class CoordinatorPhotoDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_phone") val userPhone: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_role") val userRole: String = "installer",
    @SerialName("photo_url") val photoUrl: String = "",
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("comment") val comment: String? = null,
    @SerialName("category") val category: String = "hourly",
    @SerialName("ai_comment") val aiComment: String? = null
)

// ============================================================
// /coordinator/team → {"team": [...]}
// ============================================================

@Serializable
data class CoordinatorTeamResponse(
    @SerialName("team") val team: List<CoordinatorTeamMemberDto> = emptyList()
)

@Serializable
data class CoordinatorTeamMemberDto(
    @SerialName("id") val id: String,
    @SerialName("phone") val phone: String = "",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("role") val role: String = "installer",
    @SerialName("is_active_today") val isActiveToday: Boolean = false,
    @SerialName("current_shift_status") val currentShiftStatus: String? = null,
    @SerialName("shift_duration_seconds") val shiftDurationSeconds: Int = 0,
    @SerialName("photos_today") val photosToday: Int = 0,
    @SerialName("team_size") val teamSize: Int = 0
) {
    val displayName: String
        get() = fullName?.ifBlank { phone } ?: phone

    val isForeman: Boolean get() = role == "foreman"
    val isInstaller: Boolean get() = role == "installer"
}

// ============================================================
// /coordinator/reports → {"reports": [...]}
// ============================================================

@Serializable
data class CoordinatorReportsResponse(
    @SerialName("reports") val reports: List<CoordinatorReportDto> = emptyList()
)

@Serializable
data class CoordinatorReportDto(
    @SerialName("id") val id: String,
    @SerialName("report_date") val reportDate: String? = null,
    @SerialName("content") val content: String = "",
    @SerialName("status") val status: String = "submitted",
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("curator_feedback") val curatorFeedback: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// Request: обратная связь куратора на отчёт
@Serializable
data class ReportFeedbackRequest(
    val feedback: String
)

// ============================================================
// /coordinator/tasks → {"tasks": [...]}
// ============================================================

@Serializable
data class CoordinatorTasksResponse(
    @SerialName("tasks") val tasks: List<CoordinatorTaskDto> = emptyList()
)

@Serializable
data class CoordinatorTaskDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("status") val status: String = "new",
    @SerialName("priority") val priority: String = "normal",
    @SerialName("assigned_to") val assignedTo: String = "",
    @SerialName("assigned_name") val assignedName: String? = null,
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("creator_name") val creatorName: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

// ============================================================
// /coordinator/site → {"site": {...} | null}
// ============================================================

@Serializable
data class CoordinatorSiteResponse(
    @SerialName("site") val site: CoordinatorSiteDto? = null
)

@Serializable
data class CoordinatorSiteDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "",
    @SerialName("address") val address: String? = null,
    @SerialName("status") val status: String = "active",
    @SerialName("measurements") val measurements: Map<String, String> = emptyMap(),
    @SerialName("comments") val comments: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ============================================================
// Request models
// ============================================================

@Serializable
data class CreateReportRequest(
    val content: String,
    @SerialName("report_date") val reportDate: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList()
)

@Serializable
data class UpdateReportRequest(
    val content: String? = null,
    val status: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String>? = null
)

@Serializable
data class UpdateSiteRequest(
    val measurements: Map<String, String>? = null,
    val comments: String? = null,
    val status: String? = null
)

@Serializable
data class CreateCoordinatorTaskRequest(
    val title: String,
    val description: String? = null,
    @SerialName("assigned_to") val assignedTo: String,
    val priority: String = "normal",
    @SerialName("due_at") val dueAt: String? = null
)
