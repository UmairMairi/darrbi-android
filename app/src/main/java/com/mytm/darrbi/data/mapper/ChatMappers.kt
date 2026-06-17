package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.ChatHistoryMessageDto
import com.mytm.darrbi.domain.model.ChatMessage
import com.mytm.darrbi.domain.model.ChatMessageStatus
import com.mytm.darrbi.domain.model.ChatMessageType

private const val MESSAGE_TYPE_IMAGE = 2
private const val MESSAGE_TYPE_AUDIO = 3
private const val STATUS_DELIVERED = 3
private const val STATUS_READ = 4
private const val STATUS_SENT = 1

/** Maps a history row to a domain message; [currentUserId] decides ownership (bubble side + ticks). */
fun ChatHistoryMessageDto.toDomain(currentUserId: String?): ChatMessage {
    val sender = senderId.orEmpty()
    return ChatMessage(
        messageId = messageId.orEmpty(),
        mediaIdentifier = "",
        content = messageContent.orEmpty(),
        type = when (messageType) {
            MESSAGE_TYPE_IMAGE -> ChatMessageType.Image
            MESSAGE_TYPE_AUDIO -> ChatMessageType.Audio
            1 -> ChatMessageType.Text
            else -> ChatMessageType.Other
        },
        senderId = sender,
        receiverId = receiverId.orEmpty(),
        conversationId = conversationId,
        timestampMillis = timestamp ?: 0L,
        status = when (status) {
            STATUS_READ -> ChatMessageStatus.Read
            STATUS_DELIVERED -> ChatMessageStatus.Delivered
            STATUS_SENT -> ChatMessageStatus.Sent
            else -> ChatMessageStatus.Sent
        },
        mediaUrl = metadata?.url?.takeIf { it.isNotBlank() },
        isMine = sender.isNotBlank() && sender == currentUserId,
    )
}
