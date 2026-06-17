package com.mytm.darrbi.domain.usecase

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.domain.model.ChatMessage
import com.mytm.darrbi.domain.repository.ChatRepository
import javax.inject.Inject

/** Loads the chat history with the driver (oldest-first). */
class GetChatHistoryUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(otherUserId: String): ApiResult<List<ChatMessage>> =
        repository.getHistory(otherUserId)
}

/** Marks every message in a conversation as read. */
class MarkConversationReadUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(conversationId: String): ApiResult<Unit> =
        repository.markConversationRead(conversationId)
}
