package com.belsi.work.data.repositories

import com.belsi.work.data.remote.api.ObjectsApi
import com.belsi.work.data.remote.dto.objects.*
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

interface ObjectsRepository {
    suspend fun getObjects(status: String? = null, search: String? = null): Result<List<SiteObjectDto>>
    suspend fun createObject(request: CreateObjectRequest): Result<SiteObjectDto>
    suspend fun getObjectDetail(id: String): Result<SiteObjectDetailDto>
    suspend fun updateObject(id: String, fields: Map<String, String>): Result<SiteObjectDto>
    suspend fun archiveObject(id: String): Result<Unit>
    suspend fun getCuratorObjects(): Result<List<SiteObjectDto>>
    suspend fun getCuratorObjectDetail(id: String): Result<SiteObjectDetailDto>
    suspend fun createCuratorObject(request: CreateObjectRequest): Result<SiteObjectDto>
    suspend fun updateCuratorObject(id: String, fields: Map<String, String>): Result<SiteObjectDto>
    suspend fun archiveCuratorObject(id: String): Result<Unit>
    suspend fun changeShiftObject(siteObjectId: String): Result<Unit>
}

@Singleton
class ObjectsRepositoryImpl @Inject constructor(
    private val objectsApi: ObjectsApi
) : ObjectsRepository {

    override suspend fun getObjects(status: String?, search: String?): Result<List<SiteObjectDto>> {
        return safeApiCall("объектов") { objectsApi.getObjects(status = status, search = search) }
    }

    override suspend fun createObject(request: CreateObjectRequest): Result<SiteObjectDto> {
        return safeApiCall("создания объекта") { objectsApi.createObject(request) }
    }

    override suspend fun getObjectDetail(id: String): Result<SiteObjectDetailDto> {
        return safeApiCall("деталей объекта") { objectsApi.getObjectDetail(id) }
    }

    override suspend fun updateObject(id: String, fields: Map<String, String>): Result<SiteObjectDto> {
        return safeApiCall("обновления объекта") { objectsApi.updateObject(id, fields) }
    }

    override suspend fun archiveObject(id: String): Result<Unit> {
        return try {
            val response = objectsApi.archiveObject(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Ошибка архивации: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка архивации: ${e.message}", e))
        }
    }

    override suspend fun getCuratorObjects(): Result<List<SiteObjectDto>> {
        return safeApiCall("объектов куратора") { objectsApi.getCuratorObjects() }
    }

    override suspend fun getCuratorObjectDetail(id: String): Result<SiteObjectDetailDto> {
        return safeApiCall("деталей объекта") { objectsApi.getCuratorObjectDetail(id) }
    }

    override suspend fun createCuratorObject(request: CreateObjectRequest): Result<SiteObjectDto> {
        return safeApiCall("создания объекта") { objectsApi.createCuratorObject(request) }
    }

    override suspend fun updateCuratorObject(id: String, fields: Map<String, String>): Result<SiteObjectDto> {
        return safeApiCall("обновления объекта") { objectsApi.updateCuratorObject(id, fields) }
    }

    override suspend fun archiveCuratorObject(id: String): Result<Unit> {
        return try {
            val response = objectsApi.archiveCuratorObject(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Ошибка архивации: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка архивации: ${e.message}", e))
        }
    }

    override suspend fun changeShiftObject(siteObjectId: String): Result<Unit> {
        return try {
            val response = objectsApi.changeShiftObject(ChangeObjectRequest(siteObjectId))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Ошибка смены объекта: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка смены объекта: ${e.message}", e))
        }
    }

    private suspend fun <T> safeApiCall(context: String, call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка загрузки $context: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки $context: ${e.message}", e))
        }
    }
}
