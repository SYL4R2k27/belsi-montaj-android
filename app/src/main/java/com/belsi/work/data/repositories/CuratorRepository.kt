package com.belsi.work.data.repositories

import com.belsi.work.data.models.Task
import com.belsi.work.data.remote.api.*
import com.belsi.work.data.remote.dto.curator.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы куратора
 * Все методы возвращают серверные DTO напрямую — маппинг не нужен,
 * т.к. DTO уже точно соответствуют JSON-ответам сервера.
 */
interface CuratorRepository {
    suspend fun getDashboard(): Result<CuratorDashboardDto>
    suspend fun getForemen(): Result<List<CuratorForemanDto>>
    suspend fun getForemenFull(): Result<List<ForemanDto>>
    suspend fun getUnassignedInstallers(): Result<List<UnassignedInstallerDto>>
    suspend fun getPhotos(siteObjectId: String? = null, status: String? = null): Result<List<CuratorPhotoDto>>
    suspend fun getLatestPhotos(): Result<List<CuratorPhotoDto>>
    suspend fun approvePhoto(photoId: String): Result<Unit>
    suspend fun rejectPhoto(photoId: String, reason: String): Result<Unit>
    suspend fun reviewPhoto(photoId: String, status: String, comment: String?): Result<Unit>
    suspend fun getSupportTickets(): Result<List<CuratorSupportTicketDto>>
    suspend fun getToolTransactions(
        status: String? = null,
        installerId: String? = null,
        page: Int = 1
    ): Result<CuratorToolTransactionsResponse>
    suspend fun curatorIssueTool(toolId: String, installerId: String, comment: String?): Result<Unit>
    suspend fun getAllUsers(role: String? = null): Result<List<AllUserDto>>
    suspend fun getUserDetail(userId: String): Result<UserDetailDto>
    suspend fun changeUserRole(userId: String, newRole: String): Result<ChangeRoleResponse>
    suspend fun deleteUser(userId: String): Result<Unit>
    suspend fun createTask(request: CreateTaskRequest): Result<Task>
    suspend fun deleteTask(taskId: String): Result<Unit>
    suspend fun deleteShift(shiftId: String): Result<Unit>
    suspend fun getUserReports(userId: String): Result<List<CuratorReportDto>>
    suspend fun setReportFeedback(userId: String, reportId: String, feedback: String, rating: Int?): Result<Unit>
    suspend fun uploadScreenshot(file: File): Result<String>
    suspend fun batchReviewPhotos(photoIds: List<String>, action: String, reason: String? = null): Result<BatchReviewResponse>
    suspend fun getUserRoleHistory(userId: String): Result<RoleHistoryResponse>
    suspend fun getAnalytics(period: String = "week"): Result<AnalyticsResponse>
}

@Singleton
class CuratorRepositoryImpl @Inject constructor(
    private val curatorApi: CuratorApi
) : CuratorRepository {

    override suspend fun getDashboard(): Result<CuratorDashboardDto> {
        return safeApiCall("статистики") {
            curatorApi.getDashboard()
        }
    }

    override suspend fun getForemen(): Result<List<CuratorForemanDto>> {
        return safeApiCall("бригадиров") {
            curatorApi.getForemen()
        }
    }

    override suspend fun getForemenFull(): Result<List<ForemanDto>> {
        return safeApiCall("бригадиров с монтажниками") {
            curatorApi.getForemenFull()
        }
    }

    override suspend fun getUnassignedInstallers(): Result<List<UnassignedInstallerDto>> {
        return safeApiCall("незакрепленных монтажников") {
            curatorApi.getUnassignedInstallers()
        }
    }

    override suspend fun getPhotos(siteObjectId: String?, status: String?): Result<List<CuratorPhotoDto>> {
        return try {
            val response = curatorApi.getPhotos(siteObjectId, status)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.photos)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CuratorRepo", "Ошибка загрузки фото", e)
            Result.failure(Exception("Ошибка загрузки фото: ${e.message}", e))
        }
    }

    override suspend fun getLatestPhotos(): Result<List<CuratorPhotoDto>> {
        return try {
            val response = curatorApi.getLatestPhotos()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.photos)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CuratorRepo", "Ошибка загрузки последних фото", e)
            Result.failure(Exception("Ошибка загрузки последних фото: ${e.message}", e))
        }
    }

    override suspend fun approvePhoto(photoId: String): Result<Unit> {
        return try {
            val response = curatorApi.approvePhoto(photoId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка одобрения фото: ${e.message}", e))
        }
    }

    override suspend fun rejectPhoto(photoId: String, reason: String): Result<Unit> {
        return try {
            val response = curatorApi.rejectPhoto(photoId, RejectPhotoRequest(reason))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка отклонения фото: ${e.message}", e))
        }
    }

    override suspend fun reviewPhoto(photoId: String, status: String, comment: String?): Result<Unit> {
        return safeUnitApiCall("ревью фото") {
            curatorApi.reviewPhoto(photoId, PhotoReviewRequest(status, comment))
        }
    }

    override suspend fun getSupportTickets(): Result<List<CuratorSupportTicketDto>> {
        return safeApiCall("тикетов") {
            curatorApi.getSupportTickets()
        }
    }

    override suspend fun getToolTransactions(
        status: String?,
        installerId: String?,
        page: Int
    ): Result<CuratorToolTransactionsResponse> {
        return safeApiCall("транзакций") {
            curatorApi.getToolTransactions(status, installerId, page)
        }
    }

    override suspend fun curatorIssueTool(toolId: String, installerId: String, comment: String?): Result<Unit> {
        return safeUnitApiCall("выдачи инструмента") {
            curatorApi.curatorIssueTool(CuratorIssueToolRequest(toolId, installerId, comment))
        }
    }

    override suspend fun getAllUsers(role: String?): Result<List<AllUserDto>> {
        return safeApiCall("пользователей") {
            curatorApi.getAllUsers(role)
        }
    }

    override suspend fun getUserDetail(userId: String): Result<UserDetailDto> {
        return safeApiCall("пользователя") {
            curatorApi.getUserDetail(userId)
        }
    }

    override suspend fun changeUserRole(userId: String, newRole: String): Result<ChangeRoleResponse> {
        return safeApiCall("смены роли") {
            curatorApi.changeUserRole(userId, ChangeRoleRequest(newRole))
        }
    }

    override suspend fun deleteUser(userId: String): Result<Unit> {
        return safeUnitApiCall("удаления пользователя") {
            curatorApi.deleteUser(userId)
        }
    }

    override suspend fun createTask(request: CreateTaskRequest): Result<Task> {
        return safeApiCall("создания задачи") {
            curatorApi.createTask(request)
        }
    }

    override suspend fun deleteTask(taskId: String): Result<Unit> {
        return safeUnitApiCall("удаления задачи") {
            curatorApi.deleteTask(taskId)
        }
    }

    override suspend fun deleteShift(shiftId: String): Result<Unit> {
        return safeUnitApiCall("удаления смены") {
            curatorApi.deleteShift(shiftId)
        }
    }

    override suspend fun getUserReports(userId: String): Result<List<CuratorReportDto>> {
        return safeApiCall("отчётов") {
            curatorApi.getUserReports(userId)
        }
    }

    override suspend fun setReportFeedback(
        userId: String,
        reportId: String,
        feedback: String,
        rating: Int?
    ): Result<Unit> {
        return safeUnitApiCall("обратной связи") {
            curatorApi.setReportFeedback(userId, reportId, ReportFeedbackRequest(feedback, rating))
        }
    }

    override suspend fun uploadScreenshot(file: File): Result<String> {
        return try {
            val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Part.createFormData("screenshot", file.name, requestBody)
            val response = curatorApi.uploadScreenshot(multipartBody)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.url)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("CuratorRepo", "Ошибка загрузки скриншота", e)
            Result.failure(Exception("Ошибка загрузки скриншота: ${e.message}", e))
        }
    }

    override suspend fun batchReviewPhotos(photoIds: List<String>, action: String, reason: String?): Result<BatchReviewResponse> {
        return safeApiCall("массового ревью") {
            curatorApi.batchReviewPhotos(BatchReviewRequest(photoIds, action, reason))
        }
    }

    override suspend fun getUserRoleHistory(userId: String): Result<RoleHistoryResponse> {
        return safeApiCall("истории ролей") {
            curatorApi.getUserRoleHistory(userId)
        }
    }

    override suspend fun getAnalytics(period: String): Result<AnalyticsResponse> {
        return safeApiCall("аналитики") {
            curatorApi.getAnalytics(period)
        }
    }

    /**
     * Обёртка для безопасных API вызовов
     */
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
            android.util.Log.e("CuratorRepo", "Ошибка загрузки $entityName", e)
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
            android.util.Log.e("CuratorRepo", "Ошибка $entityName", e)
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
