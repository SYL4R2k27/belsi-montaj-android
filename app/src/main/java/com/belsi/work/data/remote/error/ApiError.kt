package com.belsi.work.data.remote.error

import kotlinx.serialization.Serializable
import retrofit2.Response

/**
 * Unified error handling for FastAPI responses
 */

@Serializable
data class FastApiError(
    val detail: String
)

sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized(message: String = "Необходима авторизация") : ApiException(message)
    class Forbidden(message: String = "Нет прав доступа") : ApiException(message)
    class NotFound(message: String = "Ресурс не найден") : ApiException(message)
    class BadRequest(message: String) : ApiException(message)
    class ServerError(message: String = "Ошибка сервера") : ApiException(message)
    class NetworkError(message: String = "Ошибка сети") : ApiException(message)
    class Unknown(message: String = "Неизвестная ошибка") : ApiException(message)
}

object ApiErrorHandler {

    fun <T> handleResponse(response: Response<T>): Result<T> {
        return when {
            response.isSuccessful && response.body() != null -> {
                Result.success(response.body()!!)
            }
            response.code() == 401 -> {
                Result.failure(ApiException.Unauthorized())
            }
            response.code() == 403 -> {
                val detail = parseErrorDetail(response)
                Result.failure(ApiException.Forbidden(detail ?: "Нет прав доступа"))
            }
            response.code() == 404 -> {
                Result.failure(ApiException.NotFound())
            }
            response.code() == 400 -> {
                val detail = parseErrorDetail(response)
                Result.failure(ApiException.BadRequest(detail ?: "Неверный запрос"))
            }
            response.code() == 413 -> {
                Result.failure(ApiException.BadRequest("Файл слишком большой. Попробуйте сжать фото"))
            }
            response.code() in 500..599 -> {
                val detail = parseErrorDetail(response)
                Result.failure(ApiException.ServerError(detail ?: "Сервер недоступен"))
            }
            else -> {
                val detail = parseErrorDetail(response)
                Result.failure(ApiException.Unknown(detail ?: "Ошибка: ${response.code()}"))
            }
        }
    }

    private fun <T> parseErrorDetail(response: Response<T>): String? {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody != null) {
                // Пытаемся распарсить FastAPI error: {"detail": "message"}
                val detailRegex = "\"detail\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                detailRegex.find(errorBody)?.groupValues?.get(1)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getUserFriendlyMessage(exception: Throwable): String {
        return when (exception) {
            is ApiException.Unauthorized -> "Необходимо войти в систему"
            is ApiException.Forbidden -> exception.message ?: "Нет прав доступа"
            is ApiException.NotFound -> "Данные не найдены"
            is ApiException.BadRequest -> exception.message ?: "Неверные данные"
            is ApiException.ServerError -> exception.message ?: "Сервер временно недоступен"
            is ApiException.NetworkError -> "Проверьте подключение к интернету"
            else -> exception.message ?: "Произошла ошибка"
        }
    }
}
