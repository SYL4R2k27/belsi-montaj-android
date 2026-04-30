package com.belsi.work.data.remote.error

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ApiError(
    val detail: String? = null
)

/**
 * Парсинг FastAPI error response
 * Пытается извлечь detail из JSON, если не получается - возвращает текст ошибки или дефолтное сообщение
 */
fun parseApiError(json: Json, errorBody: String?, code: Int): String {
    if (errorBody != null) {
        // Попытка 1: Парсинг как JSON с полем detail
        try {
            val apiError = json.decodeFromString<ApiError>(errorBody)
            if (apiError.detail != null) {
                return apiError.detail
            }
        } catch (e: Exception) {
            android.util.Log.d("ApiErrorHandler", "Not a JSON error, trying plain text")
        }

        // Попытка 2: Если это обычный текст (например "Internal Server Error")
        if (errorBody.trim().isNotEmpty() && !errorBody.trim().startsWith("{")) {
            val trimmed = errorBody.trim()
            // Если это стандартная HTTP ошибка, вернем локализованное сообщение
            if (trimmed == "Internal Server Error") {
                return "Внутренняя ошибка сервера"
            }
            // Иначе вернем как есть если текст короткий
            if (trimmed.length < 100) {
                return trimmed
            }
        }
    }

    return when (code) {
        400 -> "Неверный запрос"
        401 -> "Требуется авторизация"
        403 -> "Доступ запрещен"
        404 -> "Ресурс не найден"
        500, 502, 503 -> "Ошибка сервера, попробуйте позже"
        else -> "Неизвестная ошибка (код $code)"
    }
}
