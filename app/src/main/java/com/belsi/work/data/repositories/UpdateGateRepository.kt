package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.AuditApi
import com.belsi.work.data.remote.api.ConsentItemDto
import com.belsi.work.data.remote.api.UpdateConsentRequest
import com.belsi.work.data.remote.api.UpdateConsentResponse
import com.belsi.work.data.remote.api.VersionApi
import com.belsi.work.data.remote.api.VersionPolicyDto
import javax.inject.Inject
import javax.inject.Singleton

/** Domain-модель чекбокса (отвязана от DTO). */
data class ConsentItem(
    val id: String,   // features | ai_helper | xerocode | deprecate | ready | age_18 | tos | privacy | eula
    val text: String, // текст как показан юзеру в диалоге
)

/**
 * FIX(2026-05-11) BELSI 2.0.0: репозиторий для Update Gate (B4 + B5).
 *
 * Объединяет 2 связанных вызова:
 * 1. GET /version → политика обновлений (есть ли свежая, обязательная ли)
 * 2. POST /audit/update-consent → запись согласия перед скачиванием APK
 *
 * Все методы возвращают Result<T> (silent-fail-friendly).
 */
interface UpdateGateRepository {
    suspend fun getVersionPolicy(): Result<VersionPolicyDto>
    suspend fun submitConsent(
        fromVersion: String,
        toVersion: String,
        items: List<ConsentItem>,
        deviceInfo: String? = null,
        appBuild: Int? = null,
    ): Result<UpdateConsentResponse>

    /** Множество id пунктов согласия, которые юзер КОГДА-ЛИБО принимал. */
    suspend fun getAcceptedConsentIds(): Result<Set<String>>
}

@Singleton
class UpdateGateRepositoryImpl @Inject constructor(
    private val versionApi: VersionApi,
    private val auditApi: AuditApi,
) : UpdateGateRepository {

    override suspend fun getAcceptedConsentIds(): Result<Set<String>> {
        return try {
            val response = auditApi.getAcceptedConsentIds()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.acceptedIds.toSet())
            } else {
                Result.failure(Exception("getAcceptedConsentIds: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateGate", "getAcceptedConsentIds", e)
            Result.failure(e)
        }
    }

    override suspend fun getVersionPolicy(): Result<VersionPolicyDto> {
        return try {
            val response = versionApi.getVersionPolicy()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("getVersionPolicy: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateGate", "getVersionPolicy", e)
            Result.failure(e)
        }
    }

    override suspend fun submitConsent(
        fromVersion: String,
        toVersion: String,
        items: List<ConsentItem>,
        deviceInfo: String?,
        appBuild: Int?,
    ): Result<UpdateConsentResponse> {
        return try {
            val response = auditApi.submitUpdateConsent(
                UpdateConsentRequest(
                    fromVersion = fromVersion,
                    toVersion = toVersion,
                    items = items.map { ConsentItemDto(it.id, it.text) },
                    deviceInfo = deviceInfo,
                    appBuild = appBuild,
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("submitConsent: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateGate", "submitConsent", e)
            Result.failure(e)
        }
    }
}
