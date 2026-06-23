package com.belsi.work.data.repositories

import com.belsi.work.data.models.*
import com.belsi.work.data.remote.api.ProductionApi
import com.belsi.work.data.remote.error.parseApiError
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-06): Репозиторий production-домена (variant C).
 * Объединяет Brigade / Facility / Materials / Engineer endpoints.
 */
interface ProductionRepository {
    // Brigade
    suspend fun listBrigades(facilityId: String? = null): Result<List<Brigade>>
    /** FIX(2026-05-14) BELSI 2.0.1: senior может держать до 15 бригад. */
    suspend fun getMyBrigades(): Result<List<Brigade>>
    /** Совместимость со старым кодом: возвращает первую бригаду или null. */
    suspend fun getMyBrigade(): Result<Brigade?>
    suspend fun getBrigadeMembers(brigadeId: String): Result<List<BrigadeMember>>
    suspend fun createBrigade(name: String, facilityId: String, seniorWorkerId: String? = null): Result<Brigade>
    suspend fun addBrigadeMember(brigadeId: String, userId: String, role: String = "worker"): Result<Unit>
    suspend fun removeBrigadeMember(brigadeId: String, userId: String): Result<Unit>

    // Facility
    suspend fun listFacilities(): Result<List<Facility>>
    suspend fun getFacilityDashboard(facilityId: String): Result<FacilityDashboard>

    // Materials
    suspend fun getMaterialsCatalog(category: String? = null): Result<List<Material>>
    suspend fun getInventory(facilityId: String, onlyLow: Boolean = false): Result<List<InventoryItem>>
    suspend fun adjustInventory(facilityId: String, materialId: String, delta: Int, reason: String? = null): Result<Unit>
    suspend fun listOrders(facilityId: String? = null, status: String? = null, mine: Boolean = false): Result<List<MaterialOrder>>
    suspend fun createOrder(facilityId: String, materialId: String, quantity: Int, note: String? = null): Result<MaterialOrder>
    suspend fun updateOrderStatus(orderId: String, status: String, quantityDelivered: Int? = null, note: String? = null): Result<MaterialOrder>

    // Engineer
    suspend fun getEngineerTasks(
        mine: Boolean = false,
        status: String? = null,
        type: String? = null,
        facilityId: String? = null,    // FIX(2026-05-14) BELSI 2.0.1: facility scope
    ): Result<List<EngineerTask>>
    suspend fun createEngineerTask(request: EngineerTaskCreateRequest): Result<EngineerTask>
    suspend fun updateEngineerTaskStatus(taskId: String, status: String): Result<EngineerTask>
    /** FIX(2026-05-14) BELSI 2.0.1: передача задачи другому. */
    suspend fun reassignEngineerTask(taskId: String, newAssigneeId: String, comment: String? = null): Result<EngineerTask>
    /** FIX(2026-05-14) BELSI 2.0.1: список инженеров для UI picker. */
    suspend fun listEngineers(facilityId: String? = null): Result<List<EngineerPickItem>>

    // Tools catalog (build14)
    suspend fun getToolsCatalog(): Result<List<ToolCatalogItem>>
}

@Singleton
class ProductionRepositoryImpl @Inject constructor(
    private val api: ProductionApi,
    private val json: Json,
) : ProductionRepository {

    private suspend fun <T> safeCall(op: String, call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                Result.success(response.body() as T)
            } else {
                val msg = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception("$op: $msg"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ProductionRepo", op, e)
            Result.failure(Exception("$op: ${e.message}", e))
        }
    }

    // ─── Brigade ──────────────────────────────────────────────────

    override suspend fun listBrigades(facilityId: String?) =
        safeCall("listBrigades") { api.listBrigades(facilityId) }

    override suspend fun getMyBrigades(): Result<List<Brigade>> {
        return try {
            val response = api.getMyBrigades()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("getMyBrigades: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Backward compat: возвращает первую из списка. */
    override suspend fun getMyBrigade(): Result<Brigade?> =
        getMyBrigades().map { it.firstOrNull() }

    override suspend fun getBrigadeMembers(brigadeId: String) =
        safeCall("getBrigadeMembers") { api.getBrigadeMembers(brigadeId) }

    override suspend fun createBrigade(name: String, facilityId: String, seniorWorkerId: String?) =
        safeCall("createBrigade") {
            api.createBrigade(BrigadeCreateRequest(name, facilityId, seniorWorkerId))
        }

    override suspend fun addBrigadeMember(brigadeId: String, userId: String, role: String) =
        safeCall("addBrigadeMember") {
            api.addBrigadeMember(brigadeId, BrigadeMemberAddRequest(userId, role))
        }

    override suspend fun removeBrigadeMember(brigadeId: String, userId: String) =
        safeCall("removeBrigadeMember") { api.removeBrigadeMember(brigadeId, userId) }

    // ─── Facility ─────────────────────────────────────────────────

    override suspend fun listFacilities() = safeCall("listFacilities") { api.listFacilities() }

    override suspend fun getFacilityDashboard(facilityId: String) =
        safeCall("getFacilityDashboard") { api.getFacilityDashboard(facilityId) }

    // ─── Materials ────────────────────────────────────────────────

    override suspend fun getMaterialsCatalog(category: String?) =
        safeCall("getMaterialsCatalog") { api.getMaterialsCatalog(true, category) }

    override suspend fun getInventory(facilityId: String, onlyLow: Boolean) =
        safeCall("getInventory") { api.getInventory(facilityId, onlyLow) }

    override suspend fun adjustInventory(facilityId: String, materialId: String, delta: Int, reason: String?) =
        safeCall("adjustInventory") {
            api.adjustInventory(InventoryAdjustRequest(facilityId, materialId, delta, reason))
        }

    override suspend fun listOrders(facilityId: String?, status: String?, mine: Boolean) =
        safeCall("listOrders") { api.getMaterialOrders(facilityId, status, mine) }

    override suspend fun createOrder(facilityId: String, materialId: String, quantity: Int, note: String?) =
        safeCall("createOrder") {
            api.createMaterialOrder(MaterialOrderCreateRequest(facilityId, materialId, quantity, note))
        }

    override suspend fun updateOrderStatus(orderId: String, status: String, quantityDelivered: Int?, note: String?) =
        safeCall("updateOrderStatus") {
            api.updateMaterialOrderStatus(orderId, MaterialOrderStatusRequest(status, quantityDelivered, note))
        }

    // ─── Engineer ─────────────────────────────────────────────────

    override suspend fun getEngineerTasks(mine: Boolean, status: String?, type: String?, facilityId: String?) =
        safeCall("getEngineerTasks") { api.getEngineerTasks(mine, status, type, facilityId) }

    override suspend fun createEngineerTask(request: EngineerTaskCreateRequest) =
        safeCall("createEngineerTask") { api.createEngineerTask(request) }

    override suspend fun updateEngineerTaskStatus(taskId: String, status: String) =
        safeCall("updateEngineerTaskStatus") {
            api.updateEngineerTaskStatus(taskId, EngineerTaskStatusRequest(status))
        }

    override suspend fun reassignEngineerTask(taskId: String, newAssigneeId: String, comment: String?) =
        safeCall("reassignEngineerTask") {
            api.reassignEngineerTask(taskId, EngineerTaskReassignRequest(newAssigneeId, comment))
        }

    override suspend fun listEngineers(facilityId: String?) =
        safeCall("listEngineers") { api.listEngineers(facilityId) }

    // FIX(2026-05-12) build14: tools catalog
    override suspend fun getToolsCatalog() =
        safeCall("getToolsCatalog") { api.getToolsCatalog() }
}
