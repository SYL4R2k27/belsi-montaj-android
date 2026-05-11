package com.belsi.work.data.remote.dto.objects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SiteObjectDto(
    val id: String,
    val name: String,
    val address: String? = null,
    val description: String? = null,
    val status: String = "active",
    val measurements: Map<String, String> = emptyMap(),
    val comments: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("file_urls") val fileUrls: List<String> = emptyList(),
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("creator_name") val creatorName: String? = null,
    @SerialName("coordinator_id") val coordinatorId: String? = null,
    @SerialName("coordinator_name") val coordinatorName: String? = null,
    @SerialName("active_workers_count") val activeWorkersCount: Int = 0,
    @SerialName("shifts_today") val shiftsToday: Int = 0,
    @SerialName("total_photos") val totalPhotos: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SiteObjectDetailDto(
    val id: String,
    val name: String,
    val address: String? = null,
    val description: String? = null,
    val status: String = "active",
    val measurements: Map<String, String> = emptyMap(),
    val comments: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("file_urls") val fileUrls: List<String> = emptyList(),
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("creator_name") val creatorName: String? = null,
    @SerialName("coordinator_id") val coordinatorId: String? = null,
    @SerialName("coordinator_name") val coordinatorName: String? = null,
    @SerialName("active_workers_count") val activeWorkersCount: Int = 0,
    @SerialName("shifts_today") val shiftsToday: Int = 0,
    @SerialName("total_photos") val totalPhotos: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("active_workers") val activeWorkers: List<WorkerOnSiteDto> = emptyList(),
    @SerialName("recent_photos") val recentPhotos: List<ObjectPhotoDto> = emptyList(),
    @SerialName("reports") val reports: List<ObjectReportDto> = emptyList(),
    @SerialName("segments_today") val segmentsToday: List<SegmentDto> = emptyList(),
)

@Serializable
data class WorkerOnSiteDto(
    val id: String,
    val name: String,
    val role: String = "installer",
    @SerialName("shift_start") val shiftStart: String? = null,
)

@Serializable
data class ObjectPhotoDto(
    val id: String,
    @SerialName("photo_url") val photoUrl: String,
    val status: String = "pending",
    val category: String = "hourly",
    val comment: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ObjectReportDto(
    val id: String,
    @SerialName("report_date") val reportDate: String? = null,
    val content: String = "",
    val status: String = "submitted",
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("curator_feedback") val curatorFeedback: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SegmentDto(
    val id: String,
    @SerialName("worker_name") val workerName: String = "",
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
)

@Serializable
data class CreateObjectRequest(
    val name: String,
    val address: String? = null,
    val description: String? = null,
    @SerialName("coordinator_id") val coordinatorId: String? = null,
)

@Serializable
data class ChangeObjectRequest(
    @SerialName("site_object_id") val siteObjectId: String,
)
