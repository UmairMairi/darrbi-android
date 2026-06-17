package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.ChatMessage

/** In-trip chat history operations (REST). Live messaging is handled by [SocketService]. */
interface ChatRepository {

    /** Past messages with [otherUserId] (the driver), returned oldest-first for display. */
    suspend fun getHistory(otherUserId: String): ApiResult<List<ChatMessage>>

    /** Marks every message in [conversationId] as read (`chat/mark-messages`). */
    suspend fun markConversationRead(conversationId: String): ApiResult<Unit>
}
