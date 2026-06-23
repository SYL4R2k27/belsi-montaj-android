package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.object_v3.CabinetCreateRequest
import com.belsi.work.data.remote.dto.object_v3.CabinetDto
import com.belsi.work.data.remote.dto.object_v3.CabinetStatusRequest
import com.belsi.work.data.remote.dto.object_v3.FloorDto
import com.belsi.work.data.remote.dto.object_v3.ImportZameryResultDto
import com.belsi.work.data.remote.dto.object_v3.ObjectProgressDto
import com.belsi.work.data.remote.dto.object_v3.ReadinessRequest
import com.belsi.work.data.remote.dto.object_v3.StageStatusRequest
import com.belsi.work.data.remote.dto.object_v3.WindowCreateRequest
import com.belsi.work.data.remote.dto.object_v3.WindowDto
import com.belsi.work.data.remote.dto.object_v3.ZoneDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * API модели данных v3 — backend site_object_v3.py (router prefix /v3).
 * Объект → этаж → зона → кабинет → окно (3 этапа: каркас/подоконник/экран).
 */
interface ObjectV3Api {

    /** Агрегат прогресса по 3 этапам на каждый объект (Tab AI курaтора, мок 4.4). */
    @GET("v3/progress")
    suspend fun getProgress(): Response<List<ObjectProgressDto>>

    @GET("v3/objects/{objectId}/floors")
    suspend fun getFloors(@Path("objectId") objectId: String): Response<List<FloorDto>>

    @GET("v3/floors/{floorId}/zones")
    suspend fun getZones(@Path("floorId") floorId: String): Response<List<ZoneDto>>

    @GET("v3/zones/{zoneId}/cabinets")
    suspend fun getCabinets(@Path("zoneId") zoneId: String): Response<List<CabinetDto>>

    @GET("v3/cabinets/{cabinetId}/windows")
    suspend fun getWindows(@Path("cabinetId") cabinetId: String): Response<List<WindowDto>>

    /** Создать кабинет в зоне на лету (2.1.0 — installer/foreman/coord/curator). */
    @POST("v3/zones/{zoneId}/cabinets")
    suspend fun createCabinet(
        @Path("zoneId") zoneId: String,
        @Body request: CabinetCreateRequest,
    ): Response<CabinetDto>

    /** Создать окно в кабинете на лету (2.1.0). */
    @POST("v3/cabinets/{cabinetId}/windows")
    suspend fun createWindow(
        @Path("cabinetId") cabinetId: String,
        @Body request: WindowCreateRequest,
    ): Response<WindowDto>

    @POST("v3/cabinets/{cabinetId}/readiness")
    suspend fun setReadiness(
        @Path("cabinetId") cabinetId: String,
        @Body request: ReadinessRequest,
    ): Response<CabinetDto>

    @POST("v3/cabinets/{cabinetId}/status")
    suspend fun setCabinetStatus(
        @Path("cabinetId") cabinetId: String,
        @Body request: CabinetStatusRequest,
    ): Response<CabinetDto>

    @POST("v3/windows/{windowId}/stage-status")
    suspend fun setStageStatus(
        @Path("windowId") windowId: String,
        @Body request: StageStatusRequest,
    ): Response<WindowDto>

    @Multipart
    @POST("v3/objects/{objectId}/import-zamery")
    suspend fun importZamery(
        @Path("objectId") objectId: String,
        @Part file: MultipartBody.Part,
    ): Response<ImportZameryResultDto>
}
