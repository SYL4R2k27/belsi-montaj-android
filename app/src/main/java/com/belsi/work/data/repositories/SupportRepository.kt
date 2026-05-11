package com.belsi.work.data.repositories

import com.belsi.work.data.models.SupportTicket
import com.belsi.work.data.models.TicketMessage
import com.belsi.work.data.remote.api.CreateTicketRequest
import com.belsi.work.data.remote.api.SendMessageRequest
import com.belsi.work.data.remote.api.SupportApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с поддержкой
 */
interface SupportRepository {
    suspend fun getTickets(page: Int = 1, limit: Int = 20): Result<List<SupportTicket>>
    suspend fun getTicket(ticketId: String): Result<SupportTicket>
    suspend fun createTicket(subject: String, description: String, priority: String): Result<SupportTicket>
    suspend fun sendMessage(ticketId: String, message: String): Result<TicketMessage>
    suspend fun getMessages(ticketId: String): Result<List<TicketMessage>>
    suspend fun closeTicket(ticketId: String): Result<SupportTicket>
    suspend fun replyToTicket(ticketId: String, message: String): Result<TicketMessage>
}

@Singleton
class SupportRepositoryImpl @Inject constructor(
    private val supportApi: SupportApi
) : SupportRepository {

    override suspend fun getTickets(page: Int, limit: Int): Result<List<SupportTicket>> {
        return try {
            android.util.Log.d("SupportRepository", "getTickets: page=$page, limit=$limit")
            val response = supportApi.getTickets()
            android.util.Log.d("SupportRepository", "getTickets response: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val tickets = response.body()!!
                android.util.Log.d("SupportRepository", "Loaded ${tickets.size} tickets")
                Result.success(tickets)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("SupportRepository", "getTickets error: $errorBody")
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportRepository", "getTickets exception", e)
            Result.failure(Exception("Ошибка загрузки тикетов: ${e.message}", e))
        }
    }

    override suspend fun getTicket(ticketId: String): Result<SupportTicket> {
        return try {
            android.util.Log.d("SupportRepository", "getTicket: $ticketId")
            val response = supportApi.getTicket(ticketId)
            android.util.Log.d("SupportRepository", "getTicket response: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                android.util.Log.d("SupportRepository", "Loaded ticket with ${body.messages.size} messages")
                Result.success(body.ticket)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("SupportRepository", "getTicket error: $errorBody")
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportRepository", "getTicket exception", e)
            Result.failure(Exception("Ошибка загрузки тикета: ${e.message}", e))
        }
    }

    override suspend fun createTicket(
        subject: String,
        description: String,
        priority: String
    ): Result<SupportTicket> {
        return try {
            android.util.Log.d("SupportRepository", "createTicket: $subject")
            val request = CreateTicketRequest(
                title = subject,
                text = description,
                category = "general"
            )

            val response = supportApi.createTicket(request)
            android.util.Log.d("SupportRepository", "createTicket response: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.ticket)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("SupportRepository", "createTicket error: $errorBody")
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportRepository", "createTicket exception", e)
            Result.failure(Exception("Ошибка создания тикета: ${e.message}", e))
        }
    }

    override suspend fun sendMessage(ticketId: String, message: String): Result<TicketMessage> {
        return try {
            android.util.Log.d("SupportRepository", "sendMessage: ticketId=$ticketId, message=$message")
            val request = SendMessageRequest(text = message)

            val response = supportApi.sendMessage(ticketId, request)
            android.util.Log.d("SupportRepository", "sendMessage response: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("SupportRepository", "sendMessage error: $errorBody")
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportRepository", "sendMessage exception", e)
            Result.failure(Exception("Ошибка отправки сообщения: ${e.message}", e))
        }
    }

    override suspend fun getMessages(ticketId: String): Result<List<TicketMessage>> {
        return try {
            android.util.Log.d("SupportRepository", "getMessages: $ticketId")
            val response = supportApi.getMessages(ticketId)
            android.util.Log.d("SupportRepository", "getMessages response: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val messages = response.body()!!
                android.util.Log.d("SupportRepository", "Loaded ${messages.size} messages")
                Result.success(messages)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("SupportRepository", "getMessages error: $errorBody")
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportRepository", "getMessages exception", e)
            Result.failure(Exception("Ошибка загрузки сообщений: ${e.message}", e))
        }
    }

    override suspend fun closeTicket(ticketId: String): Result<SupportTicket> {
        return try {
            android.util.Log.d("SupportRepository", "closeTicket: $ticketId")
            val response = supportApi.closeTicket(ticketId)
            android.util.Log.d("SupportRepository", "closeTicket response: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("SupportRepository", "closeTicket error: $errorBody")
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportRepository", "closeTicket exception", e)
            Result.failure(Exception("Ошибка закрытия тикета: ${e.message}", e))
        }
    }

    override suspend fun replyToTicket(ticketId: String, message: String): Result<TicketMessage> {
        return try {
            android.util.Log.d("SupportRepository", "replyToTicket: ticketId=$ticketId")
            val request = SendMessageRequest(text = message)
            val response = supportApi.replyToTicket(ticketId, request)
            android.util.Log.d("SupportRepository", "replyToTicket response: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("SupportRepository", "replyToTicket error: $errorBody")
                Result.failure(Exception(parseErrorMessage(response.code())))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportRepository", "replyToTicket exception", e)
            Result.failure(Exception("Ошибка ответа на тикет: ${e.message}", e))
        }
    }

    private fun parseErrorMessage(code: Int): String {
        return when (code) {
            400 -> "Неверный формат данных"
            401 -> "Требуется авторизация"
            403 -> "Доступ запрещен"
            404 -> "Ресурс не найден"
            422 -> "Неверные данные"
            500, 502, 503 -> "Ошибка сервера, попробуйте позже"
            else -> "Неизвестная ошибка"
        }
    }
}
