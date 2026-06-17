package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Chat DTOs mirroring ride-android:
 * - `GET chat/get-user-messages/{userId}` → `MainEnvelope<List<ChatHistoryMessageDto>>`.
 * - `POST chat/mark-messages` body → [MarkMessagesRequest].
 * Ids/timestamps are typed as String/Long-tolerant since the server sends them as numbers (lenient Json).
 */
@Serializable
data class ChatHistoryMessageDto(
    val messageId: String? = null,
    val conversationId: String? = null,
    val chatType: Int? = null,
    val messageContent: String? = null,
    val messageType: Int? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    val status: Int? = null,
    /** Epoch millis (numeric on the wire). */
    val timestamp: Long? = null,
    val metadata: ChatMetadataDto? = null,
)

@Serializable
data class ChatMetadataDto(
    val url: String? = null,
    val mime: String? = null,
)

@Serializable
data class MarkMessagesRequest(
    val conversationId: String,
)
