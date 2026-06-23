package com.belsi.work.data.remote.dto.brand

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * FIX(2026-05-11) BELSI 2.0.0 build3: DTO для brand-core API.
 */

@Serializable
data class RoleAssignmentDto(
    @SerialName("id") val id: String,
    @SerialName("role") val role: String,
    @SerialName("facility_id") val facilityId: String? = null,
    @SerialName("facility_name") val facilityName: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("granted_by") val grantedBy: String? = null,
    @SerialName("granted_at") val grantedAt: String,
)

@Serializable
data class RoleGrantIn(
    @SerialName("role") val role: String,
    @SerialName("facility_id") val facilityId: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
)

@Serializable
data class SetActiveRoleIn(
    @SerialName("role") val role: String,
)

@Serializable
data class TimelineEventDto(
    @SerialName("type") val type: String,
    @SerialName("domain") val domain: String, // installation | logistics | production | curator
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("icon") val icon: String,
    @SerialName("title") val title: String,
    @SerialName("detail") val detail: String? = null,
    // FIX(2026-05-12) build17: target_id для deep-link навигации (batch_id, photo_id, shift_id).
    @SerialName("target_id") val targetId: String? = null,
)

@Serializable
data class BatchTimelineEventDto(
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("stage") val stage: String,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("icon") val icon: String,
    @SerialName("title") val title: String,
    @SerialName("detail") val detail: String? = null,
)

@Serializable
data class IdleReasonDto(
    @SerialName("code") val code: String,
    @SerialName("label_ru") val labelRu: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)
