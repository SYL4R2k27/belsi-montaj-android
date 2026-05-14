package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.brand.*
import retrofit2.Response
import retrofit2.http.*

/**
 * FIX(2026-05-11) BELSI 2.0.0 build3: Retrofit интерфейс для brand-core endpoints:
 *   - мульти-роль (/user/me/roles, /users/{id}/roles/grant, /user/me/active-role)
 *   - timeline объекта (/objects/{id}/timeline)
 *   - pipeline партии (/production/batches/{id}/pipeline)
 *   - idle reasons по доменам (/idle-reasons?domain=)
 */
interface BrandCoreApi {

    // ─── multi-role ───
    @GET("user/me/roles")
    suspend fun myRoles(): Response<List<RoleAssignmentDto>>

    @POST("user/me/active-role")
    suspend fun setActiveRole(@Body body: SetActiveRoleIn): Response<Map<String, String>>

    @GET("users/{userId}/roles")
    suspend fun userRoles(@Path("userId") userId: String): Response<List<RoleAssignmentDto>>

    @POST("users/{userId}/roles/grant")
    suspend fun grantRole(
        @Path("userId") userId: String,
        @Body body: RoleGrantIn,
    ): Response<RoleAssignmentDto>

    @POST("users/{userId}/roles/{roleId}/revoke")
    suspend fun revokeRole(
        @Path("userId") userId: String,
        @Path("roleId") roleId: String,
    ): Response<RoleAssignmentDto>

    // ─── timeline объекта ───
    @GET("objects/{objectId}/timeline")
    suspend fun objectTimeline(
        @Path("objectId") objectId: String,
        @Query("date_from") dateFrom: String? = null,
        @Query("types") types: String? = null,
        @Query("limit") limit: Int = 200,
    ): Response<List<TimelineEventDto>>

    // ─── pipeline партии ───
    @GET("production/batches/{batchId}/pipeline")
    suspend fun batchPipeline(@Path("batchId") batchId: String): Response<List<BatchTimelineEventDto>>

    // ─── idle reasons ───
    @GET("idle-reasons")
    suspend fun idleReasons(@Query("domain") domain: String): Response<List<IdleReasonDto>>
}
