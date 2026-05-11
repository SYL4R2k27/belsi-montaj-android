package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.CancelInviteRequest
import com.belsi.work.data.remote.api.ForemanInviteResponse
import com.belsi.work.data.remote.api.InviteApi
import com.belsi.work.data.remote.api.RedeemInviteRequest
import com.belsi.work.data.remote.api.RedeemInviteResponse
import com.belsi.work.data.remote.error.parseApiError
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с инвайтами бригадира
 */
interface InviteRepository {
    suspend fun createInvite(): Result<ForemanInviteResponse>
    suspend fun getForemanInvites(): Result<List<ForemanInviteResponse>>
    suspend fun cancelInvite(code: String): Result<ForemanInviteResponse>
    suspend fun redeemInvite(code: String): Result<RedeemInviteResponse>
}

@Singleton
class InviteRepositoryImpl @Inject constructor(
    private val inviteApi: InviteApi,
    private val json: Json
) : InviteRepository {

    /**
     * Создать новый инвайт-код
     * POST /foreman/invites
     */
    override suspend fun createInvite(): Result<ForemanInviteResponse> {
        return try {
            android.util.Log.d("InviteRepository", "Creating new invite...")

            val response = inviteApi.createInvite()

            android.util.Log.d("InviteRepository", "Create invite response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { invite ->
                    android.util.Log.d("InviteRepository", "Invite created: ${invite.code}")
                    Result.success(invite)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("InviteRepository", "Create invite error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("InviteRepository", "createInvite exception", e)
            Result.failure(e)
        }
    }

    /**
     * Получить список всех инвайтов бригадира
     * GET /foreman/invites
     */
    override suspend fun getForemanInvites(): Result<List<ForemanInviteResponse>> {
        return try {
            android.util.Log.d("InviteRepository", "Loading foreman invites...")

            val response = inviteApi.getForemanInvites()

            android.util.Log.d("InviteRepository", "Get invites response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { wrapper ->
                    android.util.Log.d("InviteRepository", "Received ${wrapper.items.size} invites")

                    // Сортируем: активные сверху, потом по дате создания (новые сначала)
                    val sorted = wrapper.items.sortedWith(
                        compareByDescending<ForemanInviteResponse> { it.status == "active" }
                            .thenByDescending { it.createdAt }
                    )

                    Result.success(sorted)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("InviteRepository", "Get invites error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("InviteRepository", "getForemanInvites exception", e)
            Result.failure(e)
        }
    }

    /**
     * Отменить инвайт
     * POST /foreman/invites/cancel
     */
    override suspend fun cancelInvite(code: String): Result<ForemanInviteResponse> {
        return try {
            android.util.Log.d("InviteRepository", "Cancelling invite by code: $code")

            val request = CancelInviteRequest(code = code)
            val response = inviteApi.cancelInvite(request)

            android.util.Log.d("InviteRepository", "Cancel invite response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { invite ->
                    android.util.Log.d("InviteRepository", "Invite cancelled: ${invite.code}")
                    Result.success(invite)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("InviteRepository", "Cancel invite error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("InviteRepository", "cancelInvite exception", e)
            Result.failure(e)
        }
    }

    /**
     * Монтажник активирует инвайт-код
     * POST /foreman/invites/redeem
     * Response: { "success": true, "foreman_name": "...", "message": "..." }
     */
    override suspend fun redeemInvite(code: String): Result<RedeemInviteResponse> {
        return try {
            android.util.Log.d("InviteRepository", "Redeeming invite code: $code")

            val request = RedeemInviteRequest(code = code)
            val response = inviteApi.redeemInvite(request)

            android.util.Log.d("InviteRepository", "Redeem invite response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { result ->
                    android.util.Log.d("InviteRepository", "Invite redeemed: success=${result.success}, foreman=${result.foremanName}")
                    Result.success(result)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val error = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("InviteRepository", "Redeem invite error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            android.util.Log.e("InviteRepository", "redeemInvite exception", e)
            Result.failure(e)
        }
    }
}
