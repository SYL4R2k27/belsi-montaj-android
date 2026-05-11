package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.CoordinatorApi
import com.belsi.work.data.remote.api.CoordinatorRejectPhotoRequest
import com.belsi.work.data.remote.dto.coordinator.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы координатора
 * Бэкенд: coordinator.py
 */
interface CoordinatorRepository {
    suspend fun getDashboard(): Result<CoordinatorDashboardDto>
    suspend fun getPhotos(): Result<List<CoordinatorPhotoDto>>
    suspend fun approvePhoto(photoId: String): Result<Unit>
    suspend fun rejectPhoto(photoId: String, reason: String?): Result<Unit>
    suspend fun getTasks(): Result<List<CoordinatorTaskDto>>
    suspend fun createTask(request: CreateCoordinatorTaskRequest): Result<CoordinatorTaskDto>
    suspend fun getTeam(): Result<List<CoordinatorTeamMemberDto>>
    suspend fun getReports(): Result<List<CoordinatorReportDto>>
    suspend fun createReport(request: CreateReportRequest): Result<CoordinatorReportDto>
    suspend fun getReport(reportId: String): Result<CoordinatorReportDto>
    suspend fun updateReport(reportId: String, request: UpdateReportRequest): Result<CoordinatorReportDto>
    suspend fun getSite(): Result<CoordinatorSiteDto?>
    suspend fun updateSite(request: UpdateSiteRequest): Result<CoordinatorSiteDto>
}

@Singleton
class CoordinatorRepositoryImpl @Inject constructor(
    private val coordinatorApi: CoordinatorApi
) : CoordinatorRepository {

    override suspend fun getDashboard(): Result<CoordinatorDashboardDto> {
        return safeApiCall("дашборда") { coordinatorApi.getDashboard() }
    }

    override suspend fun getPhotos(): Result<List<CoordinatorPhotoDto>> {
        return try {
            val response = coordinatorApi.getPhotos()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.photos)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CoordinatorRepo", "Ошибка загрузки фото", e)
            Result.failure(Exception("Ошибка загрузки фото: ${e.message}", e))
        }
    }

    override suspend fun approvePhoto(photoId: String): Result<Unit> {
        return safeUnitApiCall("одобрения фото") { coordinatorApi.approvePhoto(photoId) }
    }

    override suspend fun rejectPhoto(photoId: String, reason: String?): Result<Unit> {
        return safeUnitApiCall("отклонения фото") {
            coordinatorApi.rejectPhoto(photoId, CoordinatorRejectPhotoRequest(reason ?: ""))
        }
    }

    override suspend fun getTasks(): Result<List<CoordinatorTaskDto>> {
        return try {
            val response = coordinatorApi.getTasks()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.tasks)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CoordinatorRepo", "Ошибка загрузки задач", e)
            Result.failure(Exception("Ошибка загрузки задач: ${e.message}", e))
        }
    }

    override suspend fun createTask(request: CreateCoordinatorTaskRequest): Result<CoordinatorTaskDto> {
        return safeApiCall("создания задачи") { coordinatorApi.createTask(request) }
    }

    override suspend fun getTeam(): Result<List<CoordinatorTeamMemberDto>> {
        return try {
            val response = coordinatorApi.getTeam()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.team)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CoordinatorRepo", "Ошибка загрузки команды", e)
            Result.failure(Exception("Ошибка загрузки команды: ${e.message}", e))
        }
    }

    override suspend fun getReports(): Result<List<CoordinatorReportDto>> {
        return try {
            val response = coordinatorApi.getReports()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.reports)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CoordinatorRepo", "Ошибка загрузки отчётов", e)
            Result.failure(Exception("Ошибка загрузки отчётов: ${e.message}", e))
        }
    }

    override suspend fun createReport(request: CreateReportRequest): Result<CoordinatorReportDto> {
        return safeApiCall("создания отчёта") { coordinatorApi.createReport(request) }
    }

    override suspend fun getReport(reportId: String): Result<CoordinatorReportDto> {
        return safeApiCall("отчёта") { coordinatorApi.getReport(reportId) }
    }

    override suspend fun updateReport(
        reportId: String,
        request: UpdateReportRequest
    ): Result<CoordinatorReportDto> {
        return safeApiCall("обновления отчёта") { coordinatorApi.updateReport(reportId, request) }
    }

    override suspend fun getSite(): Result<CoordinatorSiteDto?> {
        return try {
            val response = coordinatorApi.getSite()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.site)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CoordinatorRepo", "Ошибка загрузки объекта", e)
            Result.failure(Exception("Ошибка загрузки объекта: ${e.message}", e))
        }
    }

    override suspend fun updateSite(request: UpdateSiteRequest): Result<CoordinatorSiteDto> {
        return safeApiCall("обновления объекта") { coordinatorApi.updateSite(request) }
    }

    private suspend fun <T> safeApiCall(
        entityName: String,
        call: suspend () -> retrofit2.Response<T>
    ): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CoordinatorRepo", "Ошибка загрузки $entityName", e)
            Result.failure(Exception("Ошибка загрузки $entityName: ${e.message}", e))
        }
    }

    private suspend fun safeUnitApiCall(
        entityName: String,
        call: suspend () -> retrofit2.Response<Unit>
    ): Result<Unit> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CoordinatorRepo", "Ошибка $entityName", e)
            Result.failure(Exception("Ошибка $entityName: ${e.message}", e))
        }
    }

    private fun parseErrorMessage(code: Int): String = when (code) {
        400 -> "Неверный формат данных"
        401 -> "Требуется авторизация"
        403 -> "Доступ запрещен"
        404 -> "Ресурс не найден"
        500, 502, 503 -> "Ошибка сервера, попробуйте позже"
        else -> "Ошибка ($code)"
    }
}
