package com.belsi.work.data.remote.api

import com.belsi.work.data.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Messenger API — personal and group chats
 * Completely separate from ChatApi (support chat)
 */
interface MessengerApi {

    /**
     * Get all threads for current user
     */
    @GET("messenger/threads")
    suspend fun getThreads(): Response<ThreadListResponse>

    /**
     * Create a new thread (direct or group)
     */
    @POST("messenger/threads")
    suspend fun createThread(
        @Body request: CreateThreadRequest
    ): Response<ThreadDTO>

    /**
     * Get messages in a thread
     */
    @GET("messenger/threads/{threadId}/messages")
    suspend fun getMessages(
        @Path("threadId") threadId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null
    ): Response<MessagesResponse>

    /**
     * Send a message in a thread
     */
    @POST("messenger/threads/{threadId}/messages")
    suspend fun sendMessage(
        @Path("threadId") threadId: String,
        @Body request: SendMessengerMessageRequest
    ): Response<MessengerMessageDTO>

    /**
     * Mark all messages as read
     */
    @POST("messenger/threads/{threadId}/read")
    suspend fun markRead(
        @Path("threadId") threadId: String
    ): Response<Unit>

    /**
     * Update thread (rename group)
     */
    @PUT("messenger/threads/{threadId}")
    suspend fun updateThread(
        @Path("threadId") threadId: String,
        @Body request: UpdateThreadRequest
    ): Response<ThreadDTO>

    /**
     * Add members to group
     */
    @POST("messenger/threads/{threadId}/members")
    suspend fun addMembers(
        @Path("threadId") threadId: String,
        @Body request: AddMembersRequest
    ): Response<ThreadDTO>

    /**
     * Remove member from group
     */
    @DELETE("messenger/threads/{threadId}/members/{userId}")
    suspend fun removeMember(
        @Path("threadId") threadId: String,
        @Path("userId") userId: String
    ): Response<Unit>

    /**
     * Get available contacts
     */
    @GET("messenger/contacts")
    suspend fun getContacts(): Response<List<ContactDTO>>

    /**
     * Upload file for messenger
     */
    @Multipart
    @POST("messenger/upload-file")
    suspend fun uploadFile(
        @Part file: okhttp3.MultipartBody.Part
    ): Response<FileUploadResponse>

    /**
     * Delete a message (soft delete, sender only)
     */
    @DELETE("messenger/threads/{threadId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("threadId") threadId: String,
        @Path("messageId") messageId: String
    ): Response<MessengerMessageDTO>

    /**
     * Search messages across threads
     */
    @GET("messenger/search")
    suspend fun searchMessages(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50
    ): Response<SearchMessagesResponse>
}
