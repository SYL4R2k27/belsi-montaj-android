package com.belsi.work.data.repositories

import com.belsi.work.data.models.*
import com.belsi.work.data.remote.api.ToolsApi
import com.belsi.work.data.remote.api.ToolPhotoUploadResponse
import com.belsi.work.data.remote.api.ToolTransactionsResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с инструментами
 */
interface ToolsRepository {
    // Просмотр
    suspend fun getTools(status: String? = null, category: String? = null): Result<List<Tool>>
    suspend fun getMyTools(): Result<List<ToolTransaction>>
    suspend fun getTeamTools(): Result<List<ToolTransaction>>
    suspend fun getAllTransactions(status: String? = null, installerId: String? = null, page: Int = 1): Result<ToolTransactionsResponse>

    // Создание инструмента
    suspend fun createTool(name: String, category: String?, serialNumber: String?): Result<Tool>

    // Загрузка фото (универсальная)
    suspend fun uploadToolPhoto(photoFile: File): Result<ToolPhotoUploadResponse>

    // Выдача инструмента
    suspend fun issueTool(toolId: String, installerId: String, comment: String?, photoUrl: String? = null): Result<ToolTransaction>

    // Возврат инструмента
    suspend fun returnTool(transactionId: String, condition: ToolCondition, comment: String?, photoUrl: String? = null): Result<ToolTransaction>

    // История
    suspend fun getToolTransactions(toolId: String): Result<List<ToolTransaction>>
    suspend fun getInstallerTransactions(installerId: String): Result<List<ToolTransaction>>
}

@Singleton
class ToolsRepositoryImpl @Inject constructor(
    private val toolsApi: ToolsApi
) : ToolsRepository {

    /**
     * Получить список всех инструментов (справочник)
     */
    override suspend fun getTools(status: String?, category: String?): Result<List<Tool>> {
        return try {
            android.util.Log.d("ToolsRepository", "getTools: status=$status, category=$category")
            val response = toolsApi.getTools(status, category)

            if (response.isSuccessful && response.body() != null) {
                val tools = response.body()!!.items
                android.util.Log.d("ToolsRepository", "getTools: loaded ${tools.size} tools")
                Result.success(tools)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                android.util.Log.e("ToolsRepository", "getTools failed: code=${response.code()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "getTools exception", e)
            Result.failure(Exception("Ошибка загрузки инструментов: ${e.message}", e))
        }
    }

    /**
     * Получить мои активные инструменты (для монтажника)
     */
    override suspend fun getMyTools(): Result<List<ToolTransaction>> {
        return try {
            android.util.Log.d("ToolsRepository", "getMyTools called")
            val response = toolsApi.getMyTools()

            if (response.isSuccessful && response.body() != null) {
                val transactions = response.body()!!.items
                android.util.Log.d("ToolsRepository", "getMyTools: loaded ${transactions.size} transactions")
                Result.success(transactions)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                android.util.Log.e("ToolsRepository", "getMyTools failed: code=${response.code()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "getMyTools exception", e)
            Result.failure(Exception("Ошибка загрузки моих инструментов: ${e.message}", e))
        }
    }

    /**
     * Получить инструменты команды (для бригадира)
     */
    override suspend fun getTeamTools(): Result<List<ToolTransaction>> {
        return try {
            android.util.Log.d("ToolsRepository", "getTeamTools called")
            val response = toolsApi.getTeamTools()

            if (response.isSuccessful && response.body() != null) {
                val transactions = response.body()!!.items
                android.util.Log.d("ToolsRepository", "getTeamTools: loaded ${transactions.size} transactions")
                Result.success(transactions)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                android.util.Log.e("ToolsRepository", "getTeamTools failed: code=${response.code()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "getTeamTools exception", e)
            Result.failure(Exception("Ошибка загрузки инструментов команды: ${e.message}", e))
        }
    }

    /**
     * Получить все транзакции (для куратора)
     */
    override suspend fun getAllTransactions(
        status: String?,
        installerId: String?,
        page: Int
    ): Result<ToolTransactionsResponse> {
        return try {
            android.util.Log.d("ToolsRepository", "getAllTransactions: status=$status, page=$page")
            val response = toolsApi.getAllTransactions(status, installerId, page)

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                android.util.Log.d("ToolsRepository", "getAllTransactions: loaded ${result.items.size} items")
                Result.success(result)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                android.util.Log.e("ToolsRepository", "getAllTransactions failed: code=${response.code()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "getAllTransactions exception", e)
            Result.failure(Exception("Ошибка загрузки транзакций: ${e.message}", e))
        }
    }

    /**
     * Создать новый инструмент
     */
    override suspend fun createTool(
        name: String,
        category: String?,
        serialNumber: String?
    ): Result<Tool> {
        return try {
            android.util.Log.d("ToolsRepository", "createTool: name=$name, category=$category")

            val request = CreateToolRequest(
                name = name,
                category = category,
                serialNumber = serialNumber
            )

            val response = toolsApi.createTool(request)

            if (response.isSuccessful && response.body() != null) {
                val tool = response.body()!!
                android.util.Log.d("ToolsRepository", "createTool: created tool id=${tool.id}")
                Result.success(tool)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                android.util.Log.e("ToolsRepository", "createTool failed: code=${response.code()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "createTool exception", e)
            Result.failure(Exception("Ошибка создания инструмента: ${e.message}", e))
        }
    }

    /**
     * Загрузить фото инструмента (универсальный метод)
     */
    override suspend fun uploadToolPhoto(photoFile: File): Result<ToolPhotoUploadResponse> {
        return try {
            android.util.Log.d("ToolsRepository", "uploadToolPhoto: size=${photoFile.length()}")

            if (!photoFile.exists()) {
                return Result.failure(Exception("Файл не найден"))
            }

            // Проверка размера файла (макс 10MB)
            val maxSize = 10 * 1024 * 1024
            if (photoFile.length() > maxSize) {
                return Result.failure(Exception("Файл слишком большой (макс 10MB)"))
            }

            val requestFile = photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photoFile.name, requestFile)

            val response = toolsApi.uploadToolPhoto(photoPart)

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                android.util.Log.d("ToolsRepository", "uploadToolPhoto: success, url=${result.photoUrl}")
                Result.success(result)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                android.util.Log.e("ToolsRepository", "uploadToolPhoto failed: code=${response.code()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "uploadToolPhoto exception", e)
            Result.failure(Exception("Ошибка загрузки фото: ${e.message}", e))
        }
    }

    /**
     * Выдать инструмент монтажнику
     */
    override suspend fun issueTool(
        toolId: String,
        installerId: String,
        comment: String?,
        photoUrl: String?
    ): Result<ToolTransaction> {
        return try {
            android.util.Log.d("ToolsRepository", "issueTool: toolId=$toolId, installerId=$installerId")

            val request = IssueToolRequest(
                toolId = toolId,
                installerId = installerId,
                comment = comment,
                photoUrl = photoUrl
            )

            val response = toolsApi.issueTool(request)

            if (response.isSuccessful && response.body() != null) {
                val transaction = response.body()!!
                android.util.Log.d("ToolsRepository", "issueTool: created transaction id=${transaction.id}, status=${transaction.status}")
                Result.success(transaction)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("ToolsRepository", "issueTool failed: code=${response.code()}, error=$errorBody")
                val errorMsg = parseErrorMessage(response.code(), errorBody)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "issueTool exception", e)
            Result.failure(Exception("Ошибка выдачи инструмента: ${e.message}", e))
        }
    }


    /**
     * Вернуть инструмент
     */
    override suspend fun returnTool(
        transactionId: String,
        condition: ToolCondition,
        comment: String?,
        photoUrl: String?
    ): Result<ToolTransaction> {
        return try {
            android.util.Log.d("ToolsRepository", "returnTool: transactionId=$transactionId, condition=$condition")

            val request = ReturnToolRequest(
                returnCondition = condition,
                returnComment = comment,
                returnPhotoUrl = photoUrl
            )

            val response = toolsApi.returnTool(transactionId, request)

            if (response.isSuccessful && response.body() != null) {
                val transaction = response.body()!!
                android.util.Log.d("ToolsRepository", "returnTool: success, status=${transaction.status}")
                Result.success(transaction)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("ToolsRepository", "returnTool failed: code=${response.code()}, error=$errorBody")
                val errorMsg = parseErrorMessage(response.code(), errorBody)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "returnTool exception", e)
            Result.failure(Exception("Ошибка возврата инструмента: ${e.message}", e))
        }
    }

    /**
     * Получить историю транзакций инструмента
     */
    override suspend fun getToolTransactions(toolId: String): Result<List<ToolTransaction>> {
        return try {
            android.util.Log.d("ToolsRepository", "getToolTransactions: toolId=$toolId")
            val response = toolsApi.getToolTransactions(toolId)

            if (response.isSuccessful && response.body() != null) {
                val transactions = response.body()!!
                android.util.Log.d("ToolsRepository", "getToolTransactions: loaded ${transactions.size} transactions")
                Result.success(transactions)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                android.util.Log.e("ToolsRepository", "getToolTransactions failed: code=${response.code()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "getToolTransactions exception", e)
            Result.failure(Exception("Ошибка загрузки истории: ${e.message}", e))
        }
    }

    /**
     * Получить историю транзакций монтажника
     */
    override suspend fun getInstallerTransactions(installerId: String): Result<List<ToolTransaction>> {
        return try {
            android.util.Log.d("ToolsRepository", "getInstallerTransactions: installerId=$installerId")
            val response = toolsApi.getInstallerTransactions(installerId)

            if (response.isSuccessful && response.body() != null) {
                val transactions = response.body()!!
                android.util.Log.d("ToolsRepository", "getInstallerTransactions: loaded ${transactions.size} transactions")
                Result.success(transactions)
            } else {
                val errorMsg = parseErrorMessage(response.code())
                android.util.Log.e("ToolsRepository", "getInstallerTransactions failed: code=${response.code()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolsRepository", "getInstallerTransactions exception", e)
            Result.failure(Exception("Ошибка загрузки истории: ${e.message}", e))
        }
    }

    private fun parseErrorMessage(code: Int, errorBody: String? = null): String {
        val baseMessage = when (code) {
            400 -> "Неверный запрос"
            401 -> "Требуется авторизация"
            403 -> "Доступ запрещен. Проверьте права"
            404 -> "Инструмент не найден"
            409 -> "Инструмент уже выдан"
            422 -> "Неверные данные"
            500 -> "Ошибка сервера"
            502 -> "Сервер недоступен"
            503 -> "Сервис временно недоступен"
            else -> "Ошибка сети (код: $code)"
        }

        return if (!errorBody.isNullOrBlank() && errorBody.length < 200) {
            "$baseMessage: $errorBody"
        } else {
            baseMessage
        }
    }
}
