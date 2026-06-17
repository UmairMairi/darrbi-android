package com.mytm.darrbi.data.repository

import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.core.common.map
import com.mytm.darrbi.core.network.safeApiCall
import com.mytm.darrbi.core.network.unwrapMain
import com.mytm.darrbi.core.network.unwrapMainUnit
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.remote.dto.MarkMessagesRequest
import com.mytm.darrbi.data.remote.service.ChatApi
import com.mytm.darrbi.domain.model.ChatMessage
import com.mytm.darrbi.domain.repository.ChatRepository
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val api: ChatApi,
    private val userIdProvider: UserIdProvider,
) : ChatRepository {

    override suspend fun getHistory(otherUserId: String): ApiResult<List<ChatMessage>> =
        safeApiCall { api.getUserMessages(otherUserId) }
            .unwrapMain()
            // Server returns newest-first; reverse to oldest-first for the chat list (matches ride-android).
            .map { rows -> rows.asReversed().map { it.toDomain(userIdProvider.userId) } }

    override suspend fun markConversationRead(conversationId: String): ApiResult<Unit> =
        safeApiCall { api.markMessages(MarkMessagesRequest(conversationId)) }.unwrapMainUnit()
}
