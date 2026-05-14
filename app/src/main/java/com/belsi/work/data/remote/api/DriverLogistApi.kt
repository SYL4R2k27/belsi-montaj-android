package com.belsi.work.data.remote.api

import com.belsi.work.data.remote.dto.driver.*
import retrofit2.Response
import retrofit2.http.*

/**
 * FIX(2026-05-11) BELSI 2.0.0: Retrofit интерфейс для driver и logistician.
 * 18 endpoints, покрывают:
 *   - logistician/routes (CRUD + assign)
 *   - logistician/requests (заявки координаторов на доставку)
 *   - logistician/drivers (парк)
 *   - driver/routes/today + dashboard + history
 *   - driver actions: start / arrived / delivered / skip / complete
 */
interface DriverApi {

    // ============ Driver ============

    @GET("driver/routes/today")
    suspend fun routesToday(): Response<List<RouteOutDto>>

    @GET("driver/routes/{routeId}")
    suspend fun routeDetail(@Path("routeId") routeId: String): Response<RouteOutDto>

    @POST("driver/routes/{routeId}/start")
    suspend fun startRoute(@Path("routeId") routeId: String): Response<RouteOutDto>

    // FIX(2026-05-11) BELSI 2.0.0 build11: multipart photo + GPS + accuracy
    @retrofit2.http.Multipart
    @POST("driver/routes/{routeId}/points/{pointId}/arrived")
    suspend fun pointArrived(
        @Path("routeId") routeId: String,
        @Path("pointId") pointId: String,
        @retrofit2.http.Part photo: okhttp3.MultipartBody.Part? = null,
        @retrofit2.http.Part("latitude") latitude: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("longitude") longitude: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("accuracy_meters") accuracyMeters: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("notes") notes: okhttp3.RequestBody? = null,
    ): Response<RoutePointOutDto>

    @retrofit2.http.Multipart
    @POST("driver/routes/{routeId}/points/{pointId}/delivered")
    suspend fun pointDelivered(
        @Path("routeId") routeId: String,
        @Path("pointId") pointId: String,
        @retrofit2.http.Part photo: okhttp3.MultipartBody.Part? = null,
        @retrofit2.http.Part("latitude") latitude: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("longitude") longitude: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("accuracy_meters") accuracyMeters: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("notes") notes: okhttp3.RequestBody? = null,
    ): Response<RoutePointOutDto>

    // FIX(2026-05-11) build11: новый endpoint — отъезд от точки
    @retrofit2.http.Multipart
    @POST("driver/routes/{routeId}/points/{pointId}/departure")
    suspend fun pointDeparture(
        @Path("routeId") routeId: String,
        @Path("pointId") pointId: String,
        @retrofit2.http.Part photo: okhttp3.MultipartBody.Part? = null,
        @retrofit2.http.Part("latitude") latitude: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("longitude") longitude: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("accuracy_meters") accuracyMeters: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("notes") notes: okhttp3.RequestBody? = null,
    ): Response<Map<String, String>>

    @GET("driver/routes/{routeId}/points/{pointId}/events")
    suspend fun pointEvents(
        @Path("routeId") routeId: String,
        @Path("pointId") pointId: String,
    ): Response<List<DriverPointEventDto>>

    @retrofit2.http.Multipart
    @POST("driver/shifts/{shiftId}/start_photo")
    suspend fun driverShiftStartPhoto(
        @Path("shiftId") shiftId: String,
        @retrofit2.http.Part photo: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("latitude") latitude: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("longitude") longitude: okhttp3.RequestBody? = null,
    ): Response<Map<String, String>>

    @retrofit2.http.Multipart
    @POST("driver/shifts/{shiftId}/end_photo")
    suspend fun driverShiftEndPhoto(
        @Path("shiftId") shiftId: String,
        @retrofit2.http.Part photo: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("latitude") latitude: okhttp3.RequestBody? = null,
        @retrofit2.http.Part("longitude") longitude: okhttp3.RequestBody? = null,
    ): Response<Map<String, String>>

    @POST("driver/routes/{routeId}/points/{pointId}/skip")
    suspend fun pointSkip(
        @Path("routeId") routeId: String,
        @Path("pointId") pointId: String,
        @Body body: SkipPointIn,
    ): Response<RoutePointOutDto>

    @POST("driver/routes/{routeId}/complete")
    suspend fun completeRoute(@Path("routeId") routeId: String): Response<RouteOutDto>

    @GET("driver/history")
    suspend fun driverHistory(@Query("days") days: Int = 7): Response<List<RouteOutDto>>

    @GET("driver/dashboard")
    suspend fun driverDashboard(): Response<DriverDashboardDto>
}

interface LogistApi {

    @GET("logistician/routes")
    suspend fun listRoutes(
        @Query("date_from") dateFrom: String? = null,
        @Query("status") status: String? = null,
    ): Response<List<RouteOutDto>>

    @POST("logistician/routes")
    suspend fun createRoute(@Body body: RouteCreateIn): Response<RouteOutDto>

    @GET("logistician/routes/{routeId}")
    suspend fun getRoute(@Path("routeId") routeId: String): Response<RouteOutDto>

    @DELETE("logistician/routes/{routeId}")
    suspend fun cancelRoute(@Path("routeId") routeId: String): Response<Unit>

    @GET("logistician/requests")
    suspend fun listRequests(@Query("status") status: String? = null): Response<List<DeliveryRequestOutDto>>

    // FIX(2026-05-12) BELSI 2.0.0 build16: реальная одна заявка
    @GET("logistician/requests/{requestId}")
    suspend fun getRequest(@Path("requestId") requestId: String): Response<DeliveryRequestOutDto>

    @POST("logistician/requests")
    suspend fun createRequest(@Body body: DeliveryRequestIn): Response<DeliveryRequestOutDto>

    @POST("logistician/requests/{requestId}/assign")
    suspend fun assignRequest(
        @Path("requestId") requestId: String,
        @Body body: AssignRequestIn,
    ): Response<DeliveryRequestOutDto>

    @GET("logistician/drivers")
    suspend fun listDrivers(): Response<List<DriverFleetItemDto>>

    @GET("logistician/dashboard")
    suspend fun logistDashboard(): Response<LogistDashboardDto>

    // FIX(2026-05-11) BELSI 2.0.0 build13: composite endpoint для DriverDetail
    @GET("logistician/drivers/{driverId}/detail")
    suspend fun driverDetail(
        @Path("driverId") driverId: String,
        @Query("days") days: Int = 14,
    ): Response<DriverDetailDto>

    // FIX(2026-05-11) BELSI 2.0.0 build13: read-only маршрут + events
    @GET("logistician/routes/{routeId}/full")
    suspend fun routeFull(@Path("routeId") routeId: String): Response<RouteFullDto>
}
