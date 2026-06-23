package com.belsi.work.data.remote.api

import com.belsi.work.data.models.SupportTicket
import com.belsi.work.data.models.TicketMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

interface SupportApi {

    /**
     * Создать тикет
     * POST /support/tickets
     */
    @POST("support/tickets")
    suspend fun createTicket(
        @Body request: CreateTicketRequest
    ): Response<TicketWithMessagesResponse>

    /**
     * Получить список тикетов
     * GET /support/tickets
     */
    @GET("support/tickets")
    suspend fun getTickets(): Response<List<SupportTicket>>

    /**
     * Получить тикет с сообщениями
     * GET /support/tickets/{ticketId}
     */
    @GET("support/tickets/{ticketId}")
    suspend fun getTicket(
        @Path("ticketId") ticketId: String
    ): Response<TicketWithMessagesResponse>

    /**
     * Отправить сообщение в тикет
     * POST /support/tickets/{ticketId}/messages (или /reply)
     */
    @POST("support/tickets/{ticketId}/messages")
    suspend fun sendMessage(
        @Path("ticketId") ticketId: String,
        @Body request: SendMessageRequest
    ): Response<TicketMessage>

    /**
     * Получить сообщения тикета
     * GET /support/tickets/{ticketId}/messages
     */
    @GET("support/tickets/{ticketId}/messages")
    suspend fun getMessages(
        @Path("ticketId") ticketId: String
    ): Response<List<TicketMessage>>

    /**
     * Закрыть тикет
     * PUT /support/tickets/{ticketId}/close
     */
    @PUT("support/tickets/{ticketId}/close")
    suspend fun closeTicket(
        @Path("ticketId") ticketId: String
    ): Response<SupportTicket>

    /**
     * Ответить на тикет (для куратора)
     * POST /support/tickets/{ticketId}/reply
     */
    @POST("support/tickets/{ticketId}/reply")
    suspend fun replyToTicket(
        @Path("ticketId") ticketId: String,
        @Body request: SendMessageRequest
    ): Response<TicketMessage>
}

// Request/Response DTOs

/**
 * Запрос на создание тикета (согласно TicketCreateIn на бэкенде)
 */
@Serializable
data class CreateTicketRequest(
    @SerialName("title") val title: String,
    @SerialName("category") val category: String = "general",
    @SerialName("text") val text: String,
    @SerialName("photo_url") val photoUrl: String? = null
)

/**
 * Запрос на отправку сообщения (согласно ReplyIn на бэкенде)
 */
@Serializable
data class SendMessageRequest(
    @SerialName("text") val text: String,
    @SerialName("is_internal") val isInternal: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null
)

/**
 * Ответ с тикетом и сообщениями (согласно TicketWithMessagesOut на бэкенде)
 */
@Serializable
data class TicketWithMessagesResponse(
    @SerialName("ticket") val ticket: SupportTicket,
    @SerialName("messages") val messages: List<TicketMessage>
)
