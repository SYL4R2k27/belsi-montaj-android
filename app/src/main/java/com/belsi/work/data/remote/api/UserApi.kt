package com.belsi.work.data.remote.api

import com.belsi.work.data.models.User
import com.belsi.work.data.models.UserRole
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
import java.util.UUID

/**
 * BELSI API — Пользователи и профиль
 * Бэкенд: user_names.py, profiles.py
 */
interface UserApi {

    /** GET /user/me → текущий пользователь (user_names.py) */
    @GET("user/me")
    suspend fun getProfile(): Response<User>

    /** PUT /user/me → обновить профиль (user_names.py) */
    @PUT("user/me")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest
    ): Response<User>

    /** PUT /user/me/name → обновить имя (user_names.py) */
    @PUT("user/me/name")
    suspend fun updateMyName(
        @Body request: UpdateNameRequest
    ): Response<User>

    /** POST /user/me/role → изменить свою роль (user_names.py) */
    @POST("user/me/role")
    suspend fun updateMyRole(
        @Body request: UpdateRoleRequest
    ): Response<User>

    /** GET /profile/me → профиль (profiles.py — full_name, city, email, telegram, about) */
    @GET("profile/me")
    suspend fun getProfileExtended(): Response<ProfileResponse>

    /** PUT /profile/me → обновить профиль (profiles.py) */
    @PUT("profile/me")
    suspend fun updateProfileExtended(
        @Body request: UpdateProfileExtendedRequest
    ): Response<ProfileResponse>

    /** GET /users/{userId} → информация о пользователе */
    @GET("users/{userId}")
    suspend fun getUserById(
        @Path("userId") userId: UUID
    ): Response<User>

    /** GET /users/lookup/{shortId} → поиск по short_id (user_names.py) */
    @GET("users/lookup/{shortId}")
    suspend fun lookupByShortId(
        @Path("shortId") shortId: String
    ): Response<User>

    /** PUT /users/{userId} → обновить пользователя */
    @PUT("users/{userId}")
    suspend fun updateUser(
        @Path("userId") userId: UUID,
        @Body request: UpdateUserRequest
    ): Response<User>

    /** POST /users/{userId}/role → сменить роль */
    @POST("users/{userId}/role")
    suspend fun updateUserRole(
        @Path("userId") userId: UUID,
        @Body request: UpdateRoleRequest
    ): Response<User>

    /** POST /users/{userId}/avatar → загрузить аватар */
    @Multipart
    @POST("users/{userId}/avatar")
    suspend fun uploadAvatar(
        @Path("userId") userId: UUID,
        @Part avatar: MultipartBody.Part
    ): Response<AvatarUploadResponse>

    /** DELETE /users/{userId}/avatar → удалить аватар */
    @DELETE("users/{userId}/avatar")
    suspend fun deleteAvatar(
        @Path("userId") userId: UUID
    ): Response<Unit>

    /** GET /users/{userId}/stats → статистика пользователя */
    @GET("users/{userId}/stats")
    suspend fun getUserStats(
        @Path("userId") userId: UUID
    ): Response<UserStatsResponse>

}

// Request/Response DTOs
@kotlinx.serialization.Serializable
data class UpdateProfileRequest(
    @kotlinx.serialization.SerialName("full_name")
    val fullName: String? = null,
    @kotlinx.serialization.SerialName("first_name")
    val firstName: String? = null,
    @kotlinx.serialization.SerialName("last_name")
    val lastName: String? = null,
    val email: String? = null
)

@kotlinx.serialization.Serializable
data class UpdateNameRequest(
    @kotlinx.serialization.SerialName("full_name")
    val fullName: String
)

@kotlinx.serialization.Serializable
data class UpdateProfileExtendedRequest(
    @kotlinx.serialization.SerialName("full_name")
    val fullName: String? = null,
    val city: String? = null,
    val email: String? = null,
    val telegram: String? = null,
    val about: String? = null
)

@kotlinx.serialization.Serializable
data class ProfileResponse(
    val id: String? = null,
    @kotlinx.serialization.SerialName("full_name")
    val fullName: String? = null,
    val city: String? = null,
    val email: String? = null,
    val telegram: String? = null,
    val about: String? = null,
    val phone: String? = null
)

@kotlinx.serialization.Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val email: String? = null
)

@kotlinx.serialization.Serializable
data class UpdateRoleRequest(
    val role: UserRole
)

@kotlinx.serialization.Serializable
data class AvatarUploadResponse(
    @kotlinx.serialization.SerialName("avatar_url")
    val avatarUrl: String
)

@kotlinx.serialization.Serializable
data class UserStatsResponse(
    @kotlinx.serialization.SerialName("total_shifts")
    val totalShifts: Int,
    @kotlinx.serialization.SerialName("total_hours")
    val totalHours: Int,
    @kotlinx.serialization.SerialName("total_earned")
    val totalEarned: Double,
    @kotlinx.serialization.SerialName("average_rating")
    val averageRating: Double,
    @kotlinx.serialization.SerialName("completed_photos")
    val completedPhotos: Int,
    @kotlinx.serialization.SerialName("rejected_photos")
    val rejectedPhotos: Int
)
