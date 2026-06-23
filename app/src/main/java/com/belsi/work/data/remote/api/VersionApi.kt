package com.belsi.work.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

interface VersionApi {
    @GET("version")
    suspend fun getVersionPolicy(): Response<VersionPolicyDto>
}

@Serializable
data class VersionPolicyDto(
    @SerialName("latest_version") val latestVersion: String,
    @SerialName("latest_build") val latestBuild: Int,
    @SerialName("min_supported_build") val minSupportedBuild: Int,
    @SerialName("recommended_build") val recommendedBuild: Int,
    @SerialName("client_build") val clientBuild: Int? = null,
    @SerialName("update_required") val updateRequired: Boolean = false,
    @SerialName("update_recommended") val updateRecommended: Boolean = false,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("changelog") val changelog: String? = null,
)
