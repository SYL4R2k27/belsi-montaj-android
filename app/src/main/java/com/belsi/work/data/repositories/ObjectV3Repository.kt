package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.ObjectV3Api
import com.belsi.work.data.remote.dto.object_v3.CabinetCreateRequest
import com.belsi.work.data.remote.dto.object_v3.CabinetDto
import com.belsi.work.data.remote.dto.object_v3.FloorDto
import com.belsi.work.data.remote.dto.object_v3.ObjectProgressDto
import com.belsi.work.data.remote.dto.object_v3.ReadinessRequest
import com.belsi.work.data.remote.dto.object_v3.StageStatusRequest
import com.belsi.work.data.remote.dto.object_v3.WindowCreateRequest
import com.belsi.work.data.remote.dto.object_v3.WindowDto
import com.belsi.work.data.remote.dto.object_v3.ZoneDto
import javax.inject.Inject
import javax.inject.Singleton

/** Узлы дерева объекта (DTO-through, как в CuratorRepository). */
data class CabinetNode(val cabinet: CabinetDto, val windows: List<WindowDto>)
data class ZoneNode(val zone: ZoneDto, val cabinets: List<CabinetNode>)
data class FloorNode(val floor: FloorDto, val zones: List<ZoneNode>)
data class ObjectTree(val floors: List<FloorNode>, val isDemo: Boolean = false)

/**
 * Репозиторий модели данных v3.
 *
 * Strangler Fig / «всё рабочее»: [getObjectTree] пробует backend /v3/... ; если эндпоинтов
 * ещё нет (404) или ошибка сети — отдаёт демо-фикстуру (isDemo=true), чтобы ObjectScreen
 * был рабочим ДО появления backend. Когда backend готов — те же вызовы вернут реальные данные.
 */
@Singleton
class ObjectV3Repository @Inject constructor(
    private val api: ObjectV3Api,
) {

    suspend fun getObjectTree(objectId: String, allowDemo: Boolean = false): Result<ObjectTree> {
        return try {
            val floorsResp = api.getFloors(objectId)
            val floors = floorsResp.body()
            if (!floorsResp.isSuccessful || floors.isNullOrEmpty()) {
                Result.success(if (allowDemo) demoTree() else ObjectTree(emptyList(), isDemo = false))
            } else {
                val nodes = floors.map { floor ->
                    val zones = api.getZones(floor.id).body().orEmpty().map { zone ->
                        val cabinets = api.getCabinets(zone.id).body().orEmpty().map { cab ->
                            val windows = api.getWindows(cab.id).body().orEmpty()
                            CabinetNode(cab, windows)
                        }
                        ZoneNode(zone, cabinets)
                    }
                    FloorNode(floor, zones)
                }
                Result.success(ObjectTree(nodes, isDemo = false))
            }
        } catch (e: Exception) {
            // backend ещё не задеплоен / нет сети.
            // По умолчанию НЕ фейкаем (реальные данные); demo — только если явно разрешено (ObjectScreen-плейсхолдер).
            Result.success(if (allowDemo) demoTree() else ObjectTree(emptyList(), isDemo = false))
        }
    }

    /** Прогресс по 3 этапам на каждый объект (Tab AI курaтора 4.4). */
    suspend fun getProgress(): Result<List<ObjectProgressDto>> = try {
        val resp = api.getProgress()
        if (resp.isSuccessful) Result.success(resp.body().orEmpty())
        else Result.failure(IllegalStateException("progress: HTTP ${resp.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setStageStatus(windowId: String, stage: String, status: String): Result<WindowDto> = try {
        val resp = api.setStageStatus(windowId, StageStatusRequest(stage, status))
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(IllegalStateException("stage-status: HTTP ${resp.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setCabinetStatus(cabinetId: String, status: String, comment: String? = null): Result<CabinetDto> = try {
        val resp = api.setCabinetStatus(cabinetId, com.belsi.work.data.remote.dto.object_v3.CabinetStatusRequest(status, comment))
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(IllegalStateException("cabinet-status: HTTP ${resp.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setReadiness(cabinetId: String, request: ReadinessRequest): Result<CabinetDto> = try {
        val resp = api.setReadiness(cabinetId, request)
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(IllegalStateException("readiness: HTTP ${resp.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Создать кабинет в зоне на лету (2.1.0 — «+Новый кабинет» в каскаде камеры). */
    suspend fun createCabinet(zoneId: String, cabinetNumber: String, totalLengthMm: Int? = null): Result<CabinetDto> = try {
        val resp = api.createCabinet(zoneId, CabinetCreateRequest(cabinetNumber = cabinetNumber, totalLengthMm = totalLengthMm))
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(IllegalStateException("create-cabinet: HTTP ${resp.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Создать окно в кабинете на лету (2.1.0 — «+Новое окно» в каскаде камеры). */
    suspend fun createWindow(cabinetId: String, windowNumber: Int, widthMm: Int? = null): Result<WindowDto> = try {
        val resp = api.createWindow(cabinetId, WindowCreateRequest(windowNumber = windowNumber, widthMm = widthMm))
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(IllegalStateException("create-window: HTTP ${resp.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ──────────────────────────────────────────────────────────────
    // Демо-фикстура (Малая Тульская, по реальным замерам parse_zamery)
    // ──────────────────────────────────────────────────────────────
    private fun demoTree(): ObjectTree {
        fun win(n: Int, w: Int, k: String, p: String, e: String) = WindowDto(
            id = "demo-w$n", windowNumber = n, widthMm = w,
            statusKarkas = k, statusPodokonnik = p, statusEkran = e,
        )
        val cab101_1 = CabinetNode(
            CabinetDto(
                id = "demo-c1", cabinetNumber = "101/1", parentCabinetId = null,
                totalLengthMm = 11380,
                readyWindows = true, readyOtkosy = true, readyRadiators = true, readyWalls = true,
            ),
            listOf(
                win(1, 2322, "done", "in_progress", "not_started"),
                win(2, 2319, "done", "done", "not_started"),
                win(3, 2326, "in_progress", "not_started", "not_started"),
            ),
        )
        val cab102 = CabinetNode(
            CabinetDto(
                id = "demo-c2", cabinetNumber = "102", totalLengthMm = 8366,
                readyWindows = true, readyOtkosy = true, readyRadiators = true, readyPipes = true,
                readyPlinth = true, readyWalls = true, readyFloor = true,
            ),
            listOf(
                win(1, 2334, "approved", "approved", "approved"),
                win(2, 2333, "accepted", "accepted", "done"),
            ),
        )
        val zoneA1 = ZoneNode(ZoneDto(id = "demo-z1", zoneCode = "А1", totalPm = 8.2), listOf(cab101_1, cab102))
        val floor1 = FloorNode(FloorDto(id = "demo-f1", floorNumber = 1), listOf(zoneA1))
        return ObjectTree(listOf(floor1), isDemo = true)
    }
}
