package com.belsi.work.data.repositories

import com.belsi.work.data.models.Task
import com.belsi.work.data.models.ToolTransaction
import com.belsi.work.data.remote.api.CancelInviteRequest
import com.belsi.work.data.remote.api.CreateTaskRequest
import com.belsi.work.data.remote.api.ForemanInviteResponse
import com.belsi.work.data.remote.api.ForemanIssueToolRequest
import com.belsi.work.data.remote.api.ForemanReturnToolRequest
import com.belsi.work.data.remote.api.InviteApi
import com.belsi.work.data.remote.api.PhotoReviewRequest
import com.belsi.work.data.remote.api.PhotoReminderRequest
import com.belsi.work.data.remote.api.RedeemInviteRequest
import com.belsi.work.data.remote.api.TeamApi
import com.belsi.work.data.remote.dto.team.*
import com.belsi.work.data.remote.error.parseApiError
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с командой бригадира
 * Полностью переписан для реальных серверных ответов
 */
interface TeamRepository {
    // Команда
    suspend fun getTeamMembers(): Result<List<ForemanTeamMemberDto>>
    suspend fun removeTeamMember(installerId: String): Result<Unit>

    // Инвайты
    suspend fun createInvite(): Result<ForemanInviteResponse>
    suspend fun getActiveInvites(): Result<List<ForemanInviteResponse>>
    suspend fun cancelInvite(code: String): Result<Unit>
    suspend fun joinTeam(code: String): Result<JoinTeamResult>

    // Фото
    suspend fun getTeamPhotos(): Result<List<ForemanPhotoDto>>
    suspend fun getLatestPhotos(): Result<List<ForemanPhotoDto>>
    suspend fun approvePhoto(photoId: String): Result<Unit>
    suspend fun rejectPhoto(photoId: String, reason: String): Result<Unit>
    suspend fun sendPhotoReminder(installerId: String, message: String?): Result<Unit>

    // Инструменты
    suspend fun getForemanTools(): Result<List<ForemanToolDto>>
    suspend fun getToolsHistory(): Result<List<ForemanToolTransactionDto>>
    suspend fun foremanIssueTool(toolId: String, installerId: String, comment: String?, photoUrl: String?): Result<ToolTransaction>
    suspend fun foremanReturnTool(transactionId: String, condition: String, comment: String?, photoUrl: String?): Result<ToolTransaction>

    // Задачи
    suspend fun getCreatedTasks(): Result<List<ForemanTaskDto>>
    suspend fun createForemanTask(request: CreateTaskRequest): Result<Task>

    // Детали монтажника
    suspend fun getTeamMemberDetail(installerId: String): Result<InstallerDetailResponse>

    // Групповой чат
    suspend fun getOrCreateGroupThread(): Result<String>

    // Статистика простоев
    suspend fun getInstallerPauseStats(installerId: String, from: String? = null, to: String? = null): Result<com.belsi.work.data.remote.api.PauseStatsResponse>

    // Перевод монтажника на объект
    suspend fun reassignInstaller(installerId: String, siteObjectId: String): Result<com.belsi.work.data.remote.api.ReassignResponse>
}

data class JoinTeamResult(
    val success: Boolean,
    val message: String
)

@Singleton
class TeamRepositoryImpl @Inject constructor(
    private val teamApi: TeamApi,
    private val inviteApi: InviteApi,
    private val json: Json
) : TeamRepository {

    private suspend fun <T> safeApiCall(
        tag: String,
        call: suspend () -> Response<T>
    ): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TeamRepository", "$tag failed: code=${response.code()}, error=$errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TeamRepository", "$tag exception", e)
            Result.failure(Exception("Ошибка: ${e.message}", e))
        }
    }

    private suspend fun safeUnitApiCall(
        tag: String,
        call: suspend () -> Response<Unit>
    ): Result<Unit> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TeamRepository", "$tag failed: code=${response.code()}, error=$errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TeamRepository", "$tag exception", e)
            Result.failure(Exception("Ошибка: ${e.message}", e))
        }
    }

    // ==========================================
    // Команда
    // ==========================================

    override suspend fun getTeamMembers(): Result<List<ForemanTeamMemberDto>> {
        return try {
            val response = teamApi.getTeamMembers()
            if (response.isSuccessful && response.body() != null) {
                val team = response.body()!!
                android.util.Log.d("TeamRepository", "Loaded ${team.items.size} team members")
                Result.success(team.items)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TeamRepository", "getTeamMembers failed: ${response.code()}")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TeamRepository", "getTeamMembers exception", e)
            Result.failure(Exception("Ошибка загрузки команды: ${e.message}", e))
        }
    }

    override suspend fun removeTeamMember(installerId: String): Result<Unit> {
        return safeUnitApiCall("removeTeamMember") { teamApi.removeTeamMember(installerId) }
    }

    // ==========================================
    // Инвайты
    // ==========================================

    override suspend fun createInvite(): Result<ForemanInviteResponse> {
        return safeApiCall("createInvite") { inviteApi.createInvite() }
    }

    override suspend fun getActiveInvites(): Result<List<ForemanInviteResponse>> {
        return try {
            val response = inviteApi.getForemanInvites()
            if (response.isSuccessful && response.body() != null) {
                val invites = response.body()!!.items
                android.util.Log.d("TeamRepository", "Loaded ${invites.size} invites")
                Result.success(invites)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки инвайтов: ${e.message}", e))
        }
    }

    override suspend fun cancelInvite(code: String): Result<Unit> {
        return try {
            val response = inviteApi.cancelInvite(CancelInviteRequest(code))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка отмены инвайта: ${e.message}", e))
        }
    }

    override suspend fun joinTeam(code: String): Result<JoinTeamResult> {
        return try {
            val response = inviteApi.redeemInvite(RedeemInviteRequest(code))
            if (response.isSuccessful) {
                Result.success(JoinTeamResult(true, "Вы присоединились к команде"))
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка присоединения: ${e.message}", e))
        }
    }

    // ==========================================
    // Фото
    // ==========================================

    override suspend fun getTeamPhotos(): Result<List<ForemanPhotoDto>> {
        return try {
            val response = teamApi.getTeamPhotos()
            if (response.isSuccessful && response.body() != null) {
                val photos = response.body()!!.photos
                android.util.Log.d("TeamRepository", "Loaded ${photos.size} team photos")
                Result.success(photos)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TeamRepository", "getTeamPhotos failed: ${response.code()}")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TeamRepository", "getTeamPhotos exception", e)
            Result.failure(Exception("Ошибка загрузки фото: ${e.message}", e))
        }
    }

    override suspend fun getLatestPhotos(): Result<List<ForemanPhotoDto>> {
        return try {
            val response = teamApi.getLatestPhotos()
            if (response.isSuccessful && response.body() != null) {
                val photos = response.body()!!.photos
                android.util.Log.d("TeamRepository", "Loaded ${photos.size} latest photos")
                Result.success(photos)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TeamRepository", "getLatestPhotos failed: ${response.code()}")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TeamRepository", "getLatestPhotos exception", e)
            Result.failure(Exception("Ошибка загрузки фото: ${e.message}", e))
        }
    }

    override suspend fun approvePhoto(photoId: String): Result<Unit> {
        return try {
            val request = PhotoReviewRequest(status = "approved")
            val response = teamApi.reviewPhoto(photoId, request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка одобрения фото: ${e.message}", e))
        }
    }

    override suspend fun rejectPhoto(photoId: String, reason: String): Result<Unit> {
        return try {
            val request = PhotoReviewRequest(status = "rejected", comment = reason)
            val response = teamApi.reviewPhoto(photoId, request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка отклонения фото: ${e.message}", e))
        }
    }

    override suspend fun sendPhotoReminder(installerId: String, message: String?): Result<Unit> {
        return try {
            val request = PhotoReminderRequest(installerId = installerId, message = message)
            val response = teamApi.sendPhotoReminder(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка отправки напоминания: ${e.message}", e))
        }
    }

    // ==========================================
    // Инструменты
    // ==========================================

    override suspend fun getForemanTools(): Result<List<ForemanToolDto>> {
        return safeApiCall("getForemanTools") { teamApi.getForemanTools() }
    }

    override suspend fun getToolsHistory(): Result<List<ForemanToolTransactionDto>> {
        return safeApiCall("getToolsHistory") { teamApi.getToolsHistory() }
    }

    override suspend fun foremanIssueTool(
        toolId: String,
        installerId: String,
        comment: String?,
        photoUrl: String?
    ): Result<ToolTransaction> {
        return safeApiCall("foremanIssueTool") {
            teamApi.foremanIssueTool(ForemanIssueToolRequest(toolId, installerId, comment, photoUrl))
        }
    }

    override suspend fun foremanReturnTool(
        transactionId: String,
        condition: String,
        comment: String?,
        photoUrl: String?
    ): Result<ToolTransaction> {
        return safeApiCall("foremanReturnTool") {
            teamApi.foremanReturnTool(ForemanReturnToolRequest(transactionId, condition, comment, photoUrl))
        }
    }

    // ==========================================
    // Задачи
    // ==========================================

    override suspend fun getCreatedTasks(): Result<List<ForemanTaskDto>> {
        return safeApiCall("getCreatedTasks") { teamApi.getCreatedTasks() }
    }

    override suspend fun createForemanTask(request: CreateTaskRequest): Result<Task> {
        return safeApiCall("createForemanTask") { teamApi.createForemanTask(request) }
    }

    override suspend fun getTeamMemberDetail(installerId: String): Result<InstallerDetailResponse> {
        return safeApiCall("getTeamMemberDetail") { teamApi.getTeamMemberDetail(installerId) }
    }

    // ==========================================
    // Групповой чат
    // ==========================================

    override suspend fun getOrCreateGroupThread(): Result<String> {
        return try {
            val response = teamApi.getOrCreateGroupThread()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.threadId)
            } else {
                Result.failure(Exception("Ошибка создания группового чата"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка: ${e.message}", e))
        }
    }

    // ==========================================
    // Статистика простоев
    // ==========================================

    override suspend fun getInstallerPauseStats(
        installerId: String,
        from: String?,
        to: String?
    ): Result<com.belsi.work.data.remote.api.PauseStatsResponse> {
        return safeApiCall("getInstallerPauseStats") {
            teamApi.getInstallerPauseStats(installerId, from, to)
        }
    }

    // ==========================================
    // Перевод монтажника
    // ==========================================

    override suspend fun reassignInstaller(
        installerId: String,
        siteObjectId: String
    ): Result<com.belsi.work.data.remote.api.ReassignResponse> {
        return safeApiCall("reassignInstaller") {
            teamApi.reassignInstaller(installerId, com.belsi.work.data.remote.api.ReassignRequest(siteObjectId))
        }
    }
}
