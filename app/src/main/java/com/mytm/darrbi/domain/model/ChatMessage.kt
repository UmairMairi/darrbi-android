package com.mytm.darrbi.domain.model

/** Kind of chat message. Mirrors ride-android's numeric `messageType` (1=text, 2=image, 3=audio). */
enum class ChatMessageType { Text, Image, Audio, Other }

/**
 * Delivery state of an outgoing message, mirroring ride-android's numeric status:
 * 0 = [Sending] (clock), 1 = [Sent] (single tick), 3 = [Delivered] (double grey tick),
 * 4 = [Read] (double green tick).
 */
enum class ChatMessageStatus { Sending, Sent, Delivered, Read }

/**
 * A single rider↔driver chat message. [mediaIdentifier] is the client-generated id used to reconcile an
 * optimistic message with the server's `send-message-ack` (which assigns the real [messageId]).
 */
data class ChatMessage(
    val messageId: String,
    val mediaIdentifier: String,
    val content: String,
    val type: ChatMessageType,
    val senderId: String,
    val receiverId: String,
    val conversationId: String?,
    val timestampMillis: Long,
    val status: ChatMessageStatus,
    val mediaUrl: String?,
    /** True when this device's user sent it (sender == current user) — drives bubble alignment + ticks. */
    val isMine: Boolean,
)
