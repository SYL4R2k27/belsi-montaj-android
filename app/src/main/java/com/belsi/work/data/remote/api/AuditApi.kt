package com.belsi.work.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * FIX(2026-05-11) BELSI 2.0.0: audit endpoint для Update Gate.
 *
 * При тапе «Скачать» в UpdateGateDialog фронт обязательно сначала
 * шлёт сюда согласия и только при HTTP 200 + can_download=true
 * открывает Intent на скачивание APK.
 *
 * Это юридический след для 152-ФЗ — без него обработка ПД в новой
 * версии не имеет основания.
 */
interface AuditApi {
    @POST("audit/update-consent")
    suspend fun submitUpdateConsent(
        @Body request: UpdateConsentRequest,
    ): Response<UpdateConsentResponse>
}

@Serializable
data class ConsentItemDto(
    @SerialName("id") val id: String,
    @SerialName("text") val text: String,
)

@Serializable
data class UpdateConsentRequest(
    @SerialName("from_version") val fromVersion: String,
    @SerialName("to_version") val toVersion: String,
    @SerialName("items") val items: List<ConsentItemDto>,
    @SerialName("device_info") val deviceInfo: String? = null,
    @SerialName("app_build") val appBuild: Int? = null,
)

@Serializable
data class UpdateConsentResponse(
    @SerialName("id") val id: String,
    @SerialName("accepted") val accepted: Boolean = true,
    @SerialName("agreed_at") val agreedAt: String,
    @SerialName("can_download") val canDownload: Boolean = true,
)
