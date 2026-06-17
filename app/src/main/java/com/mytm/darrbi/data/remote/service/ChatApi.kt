package com.mytm.darrbi.data.remote.service

import com.mytm.darrbi.core.network.MainEnvelope
import com.mytm.darrbi.data.remote.dto.ChatHistoryMessageDto
import com.mytm.darrbi.data.remote.dto.MarkMessagesRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** In-trip chat REST endpoints (Main API): message history + mark-as-read. */
interface ChatApi {

    /** Past messages with [userId] (the other party), newest-first. */
    @GET("chat/get-user-messages/{userId}")
    suspend fun getUserMessages(@Path("userId") userId: String): MainEnvelope<List<ChatHistoryMessageDto>>

    /** Marks all messages in a conversation as read. */
    @POST("chat/mark-messages")
    suspend fun markMessages(@Body body: MarkMessagesRequest): MainEnvelope<JsonElement>
}
