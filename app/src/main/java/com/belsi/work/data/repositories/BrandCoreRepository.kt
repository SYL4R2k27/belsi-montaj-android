package com.belsi.work.data.repositories

import com.belsi.work.data.local.ActiveRoleManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.remote.api.BrandCoreApi
import com.belsi.work.data.remote.dto.brand.*
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-11) BELSI 2.0.0 build3: Repository для brand-core API
 * (мульти-роль, timeline, pipeline, idle-reasons).
 */
@Singleton
class BrandCoreRepository @Inject constructor(
    private val api: BrandCoreApi,
    private val activeRoleManager: ActiveRoleManager,
) {
    // ─── multi-role ───
    suspend fun fetchMyRoles(): Result<List<RoleAssignmentDto>> = safe { api.myRoles() }.also { res ->
        // Side-effect: при успешной загрузке кешируем список ролей в ActiveRoleManager
        res.onSuccess { list ->
            val roles = list.mapNotNull { runCatching { UserRole.valueOf(it.role.uppercase()) }.getOrNull() }
            activeRoleManager.setAvailableRoles(roles)
        }
    }

    suspend fun setActiveRole(role: UserRole): Result<Unit> {
        val r = safe { api.setActiveRole(SetActiveRoleIn(role.name.lowercase())) }
        r.onSuccess { activeRoleManager.setActiveRole(role) }
        return r.map { Unit }
    }

    suspend fun grantRole(userId: String, role: String, facilityId: String? = null, isPrimary: Boolean = false): Result<RoleAssignmentDto> =
        safe { api.grantRole(userId, RoleGrantIn(role, facilityId, isPrimary)) }

    suspend fun revokeRole(userId: String, roleId: String): Result<RoleAssignmentDto> =
        safe { api.revokeRole(userId, roleId) }

    suspend fun listUserRoles(userId: String): Result<List<RoleAssignmentDto>> =
        safe { api.userRoles(userId) }

    // ─── timeline ───
    suspend fun objectTimeline(
        objectId: String,
        dateFrom: String? = null,
        types: String? = null,
        limit: Int = 200,
    ): Result<List<TimelineEventDto>> = safe { api.objectTimeline(objectId, dateFrom, types, limit) }

    // ─── pipeline ───
    suspend fun batchPipeline(batchId: String): Result<List<BatchTimelineEventDto>> =
        safe { api.batchPipeline(batchId) }

    // ─── idle reasons ───
    suspend fun idleReasons(domain: String): Result<List<IdleReasonDto>> =
        safe { api.idleReasons(domain) }
}

private suspend fun <T> safe(block: suspend () -> Response<T>): Result<T> = try {
    val r = block()
    if (r.isSuccessful) {
        @Suppress("UNCHECKED_CAST") val body = r.body() ?: (Unit as T)
        Result.success(body)
    } else {
        Result.failure(Exception("HTTP ${r.code()}: ${r.errorBody()?.string() ?: r.message()}"))
    }
} catch (e: Exception) {
    Result.failure(e)
}
