package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.DriverApi
import com.belsi.work.data.remote.api.LogistApi
import com.belsi.work.data.remote.dto.driver.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-11) BELSI 2.0.0: репозитории для driver и logistician.
 * Result<T>-обёртки на все вызовы. Ошибки сети/HTTP не пробрасываются — конвертируются в Result.failure.
 */

@Singleton
class DriverRepository @Inject constructor(
    private val api: DriverApi,
) {
    suspend fun routesToday(): Result<List<RouteOutDto>> = safe { api.routesToday() }
    suspend fun routeDetail(id: String): Result<RouteOutDto> = safe { api.routeDetail(id) }
    suspend fun startRoute(id: String): Result<RouteOutDto> = safe { api.startRoute(id) }
    // FIX(2026-05-11) BELSI 2.0.0 build11: multipart photo + GPS
    suspend fun pointArrived(
        routeId: String, pointId: String,
        photoFile: File? = null,
        latitude: Double? = null, longitude: Double? = null, accuracyMeters: Float? = null,
        notes: String? = null,
    ): Result<RoutePointOutDto> = safe {
        api.pointArrived(
            routeId, pointId,
            photo = photoFile?.toMultipartPart("photo"),
            latitude = latitude?.toRequestBodyPart(),
            longitude = longitude?.toRequestBodyPart(),
            accuracyMeters = accuracyMeters?.toRequestBodyPart(),
            notes = notes?.toRequestBodyPart(),
        )
    }

    suspend fun pointDelivered(
        routeId: String, pointId: String,
        photoFile: File? = null,
        latitude: Double? = null, longitude: Double? = null, accuracyMeters: Float? = null,
        notes: String? = null,
    ): Result<RoutePointOutDto> = safe {
        api.pointDelivered(
            routeId, pointId,
            photo = photoFile?.toMultipartPart("photo"),
            latitude = latitude?.toRequestBodyPart(),
            longitude = longitude?.toRequestBodyPart(),
            accuracyMeters = accuracyMeters?.toRequestBodyPart(),
            notes = notes?.toRequestBodyPart(),
        )
    }

    suspend fun pointDeparture(
        routeId: String, pointId: String,
        photoFile: File? = null,
        latitude: Double? = null, longitude: Double? = null, accuracyMeters: Float? = null,
        notes: String? = null,
    ): Result<Map<String, String>> = safe {
        api.pointDeparture(
            routeId, pointId,
            photo = photoFile?.toMultipartPart("photo"),
            latitude = latitude?.toRequestBodyPart(),
            longitude = longitude?.toRequestBodyPart(),
            accuracyMeters = accuracyMeters?.toRequestBodyPart(),
            notes = notes?.toRequestBodyPart(),
        )
    }

    suspend fun pointEvents(routeId: String, pointId: String): Result<List<DriverPointEventDto>> =
        safe { api.pointEvents(routeId, pointId) }

    suspend fun shiftStartPhoto(
        shiftId: String, photoFile: File,
        latitude: Double? = null, longitude: Double? = null,
    ): Result<Map<String, String>> = safe {
        api.driverShiftStartPhoto(
            shiftId,
            photo = photoFile.toMultipartPart("photo")!!,
            latitude = latitude?.toRequestBodyPart(),
            longitude = longitude?.toRequestBodyPart(),
        )
    }

    suspend fun shiftEndPhoto(
        shiftId: String, photoFile: File,
        latitude: Double? = null, longitude: Double? = null,
    ): Result<Map<String, String>> = safe {
        api.driverShiftEndPhoto(
            shiftId,
            photo = photoFile.toMultipartPart("photo")!!,
            latitude = latitude?.toRequestBodyPart(),
            longitude = longitude?.toRequestBodyPart(),
        )
    }

    suspend fun pointSkip(routeId: String, pointId: String, reason: String): Result<RoutePointOutDto> =
        safe { api.pointSkip(routeId, pointId, SkipPointIn(reason)) }
    suspend fun completeRoute(id: String): Result<RouteOutDto> = safe { api.completeRoute(id) }
    suspend fun history(days: Int = 7): Result<List<RouteOutDto>> = safe { api.driverHistory(days) }
    suspend fun dashboard(): Result<DriverDashboardDto> = safe { api.driverDashboard() }
}

// FIX(2026-05-11) build11: helpers для multipart
private fun File.toMultipartPart(name: String): MultipartBody.Part? {
    if (!exists()) return null
    val rb = asRequestBody("image/jpeg".toMediaTypeOrNull())
    return MultipartBody.Part.createFormData(name, this.name, rb)
}
private fun Number.toRequestBodyPart() =
    toString().toRequestBody("text/plain".toMediaTypeOrNull())
private fun String.toRequestBodyPart() =
    toRequestBody("text/plain".toMediaTypeOrNull())

@Singleton
class LogistRepository @Inject constructor(
    private val api: LogistApi,
) {
    suspend fun listRoutes(dateFrom: String? = null, status: String? = null): Result<List<RouteOutDto>> =
        safe { api.listRoutes(dateFrom, status) }
    suspend fun createRoute(driverId: String, plannedDate: String, points: List<RoutePointIn>, notes: String? = null): Result<RouteOutDto> =
        safe { api.createRoute(RouteCreateIn(driverId, plannedDate, notes, points)) }
    suspend fun getRoute(id: String): Result<RouteOutDto> = safe { api.getRoute(id) }
    suspend fun cancelRoute(id: String): Result<Unit> = safe { api.cancelRoute(id) }
    suspend fun listRequests(status: String? = null): Result<List<DeliveryRequestOutDto>> =
        safe { api.listRequests(status) }

    // FIX(2026-05-12) BELSI 2.0.0 build16
    suspend fun getRequest(requestId: String): Result<DeliveryRequestOutDto> =
        safe { api.getRequest(requestId) }
    suspend fun createRequest(body: DeliveryRequestIn): Result<DeliveryRequestOutDto> =
        safe { api.createRequest(body) }
    suspend fun assignRequest(requestId: String, routeId: String, pointId: String? = null): Result<DeliveryRequestOutDto> =
        safe { api.assignRequest(requestId, AssignRequestIn(routeId, pointId)) }
    suspend fun listDrivers(): Result<List<DriverFleetItemDto>> = safe { api.listDrivers() }
    suspend fun dashboard(): Result<LogistDashboardDto> = safe { api.logistDashboard() }

    // FIX(2026-05-11) BELSI 2.0.0 build13: composite endpoints для UI
    suspend fun driverDetail(driverId: String, days: Int = 14): Result<DriverDetailDto> =
        safe { api.driverDetail(driverId, days) }

    suspend fun routeFull(routeId: String): Result<RouteFullDto> =
        safe { api.routeFull(routeId) }
}

/** Универсальный wrapper для Retrofit Response → Result. */
private suspend fun <T> safe(block: suspend () -> Response<T>): Result<T> = try {
    val r = block()
    if (r.isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        val body = r.body() ?: (Unit as T)
        Result.success(body)
    } else {
        Result.failure(Exception("HTTP ${r.code()}: ${r.errorBody()?.string() ?: r.message()}"))
    }
} catch (e: Exception) {
    Result.failure(e)
}
