package com.belsi.work.data.repositories

import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.local.TokenManager
import com.belsi.work.data.models.User
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.remote.api.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с профилем пользователя
 * Бэкенд: user_names.py, profiles.py
 */
interface UserRepository {
    suspend fun getProfile(): Result<User>
    suspend fun updateProfile(fullName: String?, email: String?): Result<User>
    suspend fun updateMyName(fullName: String): Result<User>
    suspend fun updateMyRole(role: UserRole): Result<User>
    suspend fun getProfileExtended(): Result<ProfileResponse>
    suspend fun updateProfileExtended(
        fullName: String? = null,
        city: String? = null,
        email: String? = null,
        telegram: String? = null,
        about: String? = null
    ): Result<ProfileResponse>
    suspend fun lookupByShortId(shortId: String): Result<User>
    suspend fun uploadAvatar(avatarFile: File): Result<String>
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
    private val prefsManager: PrefsManager,
    private val tokenManager: TokenManager
) : UserRepository {

    override suspend fun getProfile(): Result<User> {
        return try {
            val response = userApi.getProfile()

            if (response.isSuccessful && response.body() != null) {
                val serverUser = response.body()!!
                val localUser = prefsManager.getUser()

                // Сервер — источник правды для роли.
                // Если по какой-то причине role не десериализовалась (null),
                // сохраняем локальную роль как fallback.
                val mergedUser = if (serverUser.role != null) {
                    serverUser
                } else {
                    serverUser.copy(role = localUser?.role)
                }

                prefsManager.saveUser(mergedUser)
                // Всегда синхронизируем userId в encrypted storage
                // (нужно для мессенджера и других фич)
                prefsManager.setUserId(mergedUser.id.toString())
                Result.success(mergedUser)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки профиля: ${e.message}", e))
        }
    }

    override suspend fun updateProfile(fullName: String?, email: String?): Result<User> {
        return try {
            // Разбиваем fullName на first_name и last_name для совместимости с сервером
            val nameParts = fullName?.trim()?.split(" ", limit = 2)
            val firstName = nameParts?.getOrNull(0)
            val lastName = nameParts?.getOrNull(1)

            val request = UpdateProfileRequest(
                fullName = fullName,
                firstName = firstName,
                lastName = lastName,
                email = email?.takeIf { it.isNotBlank() }
            )
            android.util.Log.d("UserRepository", "Sending update: fullName=$fullName, firstName=$firstName, lastName=$lastName, email=$email")
            val response = userApi.updateProfile(request)

            if (response.isSuccessful && response.body() != null) {
                val serverUser = response.body()!!
                val localUser = prefsManager.getUser()
                // Сохраняем роль: сервер — приоритет, локальная — fallback
                val mergedUser = if (serverUser.role != null) {
                    serverUser
                } else {
                    serverUser.copy(role = localUser?.role)
                }
                prefsManager.saveUser(mergedUser)
                Result.success(mergedUser)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка обновления профиля: ${e.message}", e))
        }
    }

    override suspend fun updateMyName(fullName: String): Result<User> {
        return try {
            val response = userApi.updateMyName(UpdateNameRequest(fullName))
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                prefsManager.saveUser(user)
                Result.success(user)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка обновления имени: ${e.message}", e))
        }
    }

    override suspend fun updateMyRole(role: UserRole): Result<User> {
        return try {
            val response = userApi.updateMyRole(UpdateRoleRequest(role))
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                prefsManager.saveUser(user)
                Result.success(user)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка обновления роли: ${e.message}", e))
        }
    }

    override suspend fun getProfileExtended(): Result<ProfileResponse> {
        return try {
            val response = userApi.getProfileExtended()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки профиля: ${e.message}", e))
        }
    }

    override suspend fun updateProfileExtended(
        fullName: String?,
        city: String?,
        email: String?,
        telegram: String?,
        about: String?
    ): Result<ProfileResponse> {
        return try {
            val request = UpdateProfileExtendedRequest(fullName, city, email, telegram, about)
            val response = userApi.updateProfileExtended(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка обновления профиля: ${e.message}", e))
        }
    }

    override suspend fun lookupByShortId(shortId: String): Result<User> {
        return try {
            val response = userApi.lookupByShortId(shortId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка поиска пользователя: ${e.message}", e))
        }
    }

    override suspend fun uploadAvatar(avatarFile: File): Result<String> {
        return try {
            val userId = tokenManager.getUserId()
                ?: return Result.failure(Exception("Пользователь не авторизован"))

            val requestBody = avatarFile.asRequestBody("image/*".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Part.createFormData("avatar", avatarFile.name, requestBody)

            val response = userApi.uploadAvatar(UUID.fromString(userId), multipartBody)

            if (response.isSuccessful && response.body() != null) {
                val avatarUrl = response.body()!!.avatarUrl
                // Обновляем локальный кэш пользователя с новым аватаром
                prefsManager.getUser()?.let { user ->
                    prefsManager.saveUser(user.copy(avatarUrl = avatarUrl))
                }
                Result.success(avatarUrl)
            } else {
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки аватара: ${e.message}", e))
        }
    }

    private fun parseErrorMessage(code: Int): String {
        return when (code) {
            400 -> "Неверный формат данных"
            401 -> "Требуется авторизация"
            403 -> "Доступ запрещен"
            404 -> "Пользователь не найден"
            500, 502, 503 -> "Ошибка сервера, попробуйте позже"
            else -> "Неизвестная ошибка"
        }
    }
}
