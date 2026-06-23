package com.belsi.work.data.repositories

import com.belsi.work.data.models.SupportTicket
import com.belsi.work.data.models.TicketMessage
import com.belsi.work.data.remote.api.CreateTicketRequest
import com.belsi.work.data.remote.api.SendMessageRequest
import com.belsi.work.data.remote.api.SupportApi
import com.belsi.work.data.remote.error.parseApiError
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface TicketRepository {
    suspend fun createTicket(subject: String, description: String, priority: String = "MEDIUM"): Result<SupportTicket>
    suspend fun getTickets(page: Int = 1, limit: Int = 20): Result<List<SupportTicket>>
    suspend fun getTicketDetail(ticketId: UUID): Result<SupportTicket>
    suspend fun sendMessage(ticketId: UUID, message: String): Result<TicketMessage>
    suspend fun closeTicket(ticketId: UUID): Result<SupportTicket>
}

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val supportApi: SupportApi,
    private val json: Json
) : TicketRepository {

    override suspend fun createTicket(
        subject: String,
        description: String,
        priority: String
    ): Result<SupportTicket> {
        return try {
            android.util.Log.d("TicketRepository", "Creating ticket: subject=$subject, priority=$priority")

            val request = CreateTicketRequest(
                title = subject,
                text = description,
                category = "general"
            )

            val response = supportApi.createTicket(request)

            if (response.isSuccessful && response.body() != null) {
                val ticket = response.body()!!.ticket
                android.util.Log.d("TicketRepository", "Ticket created: ${ticket.id}")
                Result.success(ticket)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TicketRepository", "Failed to create ticket. Code: ${response.code()}, Error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketRepository", "createTicket exception", e)
            Result.failure(Exception(e.message ?: "Ошибка создания обращения"))
        }
    }

    override suspend fun getTickets(page: Int, limit: Int): Result<List<SupportTicket>> {
        return try {
            android.util.Log.d("TicketRepository", "Loading tickets page=$page, limit=$limit")

            val response = supportApi.getTickets()

            if (response.isSuccessful && response.body() != null) {
                val tickets = response.body()!!
                android.util.Log.d("TicketRepository", "Loaded ${tickets.size} tickets")
                Result.success(tickets)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TicketRepository", "Failed to load tickets. Code: ${response.code()}, Error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketRepository", "getTickets exception", e)
            Result.failure(Exception(e.message ?: "Ошибка загрузки обращений"))
        }
    }

    override suspend fun getTicketDetail(ticketId: UUID): Result<SupportTicket> {
        return try {
            android.util.Log.d("TicketRepository", "Loading ticket detail: $ticketId")

            val response = supportApi.getTicket(ticketId.toString())

            if (response.isSuccessful && response.body() != null) {
                val ticket = response.body()!!.ticket
                android.util.Log.d("TicketRepository", "Ticket detail loaded: ${ticket.id}")
                Result.success(ticket)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TicketRepository", "Failed to load ticket detail. Code: ${response.code()}, Error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketRepository", "getTicketDetail exception", e)
            Result.failure(Exception(e.message ?: "Ошибка загрузки обращения"))
        }
    }

    override suspend fun sendMessage(ticketId: UUID, message: String): Result<TicketMessage> {
        return try {
            android.util.Log.d("TicketRepository", "Sending message to ticket: $ticketId")

            val request = SendMessageRequest(text = message)
            val response = supportApi.sendMessage(ticketId.toString(), request)

            if (response.isSuccessful && response.body() != null) {
                android.util.Log.d("TicketRepository", "Message sent successfully")
                Result.success(response.body()!!)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TicketRepository", "Failed to send message. Code: ${response.code()}, Error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketRepository", "sendMessage exception", e)
            Result.failure(Exception(e.message ?: "Ошибка отправки сообщения"))
        }
    }

    override suspend fun closeTicket(ticketId: UUID): Result<SupportTicket> {
        return try {
            android.util.Log.d("TicketRepository", "Closing ticket: $ticketId")

            val response = supportApi.closeTicket(ticketId.toString())

            if (response.isSuccessful && response.body() != null) {
                android.util.Log.d("TicketRepository", "Ticket closed successfully")
                Result.success(response.body()!!)
            } else {
                val errorMessage = parseApiError(json, response.errorBody()?.string(), response.code())
                android.util.Log.e("TicketRepository", "Failed to close ticket. Code: ${response.code()}, Error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketRepository", "closeTicket exception", e)
            Result.failure(Exception(e.message ?: "Ошибка закрытия обращения"))
        }
    }
}
