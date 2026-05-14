package com.belsi.work.data.remote.api

import com.belsi.work.data.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * FIX(2026-05-06): API для production-домена (variant C).
 *
 * Соответствует серверным routers:
 *   - production_brigade.py
 *   - production_materials.py
 *   - production_engineer.py
 */
interface ProductionApi {

    // ─────────────────────────────────────────────────────────────
    // Бригады
    // ─────────────────────────────────────────────────────────────

    @GET("production/brigades")
    suspend fun listBrigades(
        @Query("facility_id") facilityId: String? = null,
    ): Response<List<Brigade>>

    @POST("production/brigades")
    suspend fun createBrigade(@Body request: BrigadeCreateRequest): Response<Brigade>

    /**
     * FIX(2026-05-14) BELSI 2.0.1: senior_worker может держать до 15 бригад.
     * Раньше endpoint возвращал одну (Brigade?), теперь — List<Brigade>.
     */
    @GET("production/brigades/mine")
    suspend fun getMyBrigades(): Response<List<Brigade>>

    @GET("production/brigades/{brigadeId}/members")
    suspend fun getBrigadeMembers(@Path("brigadeId") brigadeId: String): Response<List<BrigadeMember>>

    @POST("production/brigades/{brigadeId}/members")
    suspend fun addBrigadeMember(
        @Path("brigadeId") brigadeId: String,
        @Body request: BrigadeMemberAddRequest,
    ): Response<Unit>

    @DELETE("production/brigades/{brigadeId}/members/{userId}")
    suspend fun removeBrigadeMember(
        @Path("brigadeId") brigadeId: String,
        @Path("userId") userId: String,
    ): Response<Unit>

    // FIX(2026-05-12) BELSI 2.0.0 build14: реальный tools catalog из БД
    @GET("production/engineer/tools-catalog")
    suspend fun getToolsCatalog(): Response<List<ToolCatalogItem>>

    // ─────────────────────────────────────────────────────────────
    // Фабрика
    // ─────────────────────────────────────────────────────────────

    @GET("production/facilities")
    suspend fun listFacilities(): Response<List<Facility>>

    @GET("production/facility/{facilityId}/dashboard")
    suspend fun getFacilityDashboard(
        @Path("facilityId") facilityId: String,
    ): Response<FacilityDashboard>

    // ─────────────────────────────────────────────────────────────
    // Материалы
    // ─────────────────────────────────────────────────────────────

    @GET("production/materials/catalog")
    suspend fun getMaterialsCatalog(
        @Query("active_only") activeOnly: Boolean = true,
        @Query("category") category: String? = null,
    ): Response<List<Material>>

    @GET("production/materials/inventory")
    suspend fun getInventory(
        @Query("facility_id") facilityId: String,
        @Query("only_low") onlyLow: Boolean = false,
    ): Response<List<InventoryItem>>

    @POST("production/materials/inventory/adjust")
    suspend fun adjustInventory(@Body request: InventoryAdjustRequest): Response<Unit>

    @GET("production/materials/orders")
    suspend fun getMaterialOrders(
        @Query("facility_id") facilityId: String? = null,
        @Query("status") status: String? = null,
        @Query("mine") mine: Boolean = false,
    ): Response<List<MaterialOrder>>

    @POST("production/materials/orders")
    suspend fun createMaterialOrder(@Body request: MaterialOrderCreateRequest): Response<MaterialOrder>

    @PATCH("production/materials/orders/{orderId}")
    suspend fun updateMaterialOrderStatus(
        @Path("orderId") orderId: String,
        @Body request: MaterialOrderStatusRequest,
    ): Response<MaterialOrder>

    // ─────────────────────────────────────────────────────────────
    // Инженерные задачи
    // ─────────────────────────────────────────────────────────────

    @GET("production/engineer/tasks")
    suspend fun getEngineerTasks(
        @Query("mine") mine: Boolean = false,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null,
        @Query("facility_id") facilityId: String? = null,   // FIX(2026-05-14) BELSI 2.0.1
    ): Response<List<EngineerTask>>

    @POST("production/engineer/tasks")
    suspend fun createEngineerTask(@Body request: EngineerTaskCreateRequest): Response<EngineerTask>

    @PATCH("production/engineer/tasks/{taskId}/status")
    suspend fun updateEngineerTaskStatus(
        @Path("taskId") taskId: String,
        @Body request: EngineerTaskStatusRequest,
    ): Response<EngineerTask>

    /** FIX(2026-05-14) BELSI 2.0.1: передача задачи другому инженеру. */
    @PATCH("production/engineer/tasks/{taskId}/reassign")
    suspend fun reassignEngineerTask(
        @Path("taskId") taskId: String,
        @Body request: EngineerTaskReassignRequest,
    ): Response<EngineerTask>

    /** FIX(2026-05-14) BELSI 2.0.1: список инженеров для выбора при reassign. */
    @GET("production/engineer/engineers")
    suspend fun listEngineers(
        @Query("facility_id") facilityId: String? = null,
    ): Response<List<EngineerPickItem>>
}
