package com.mytm.darrbi.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytm.darrbi.core.common.ApiResult
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.domain.model.ChatMessage
import com.mytm.darrbi.domain.model.ChatMessageStatus
import com.mytm.darrbi.domain.model.ChatMessageType
import com.mytm.darrbi.domain.repository.ChatSocketEvent
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.usecase.GetChatHistoryUseCase
import com.mytm.darrbi.domain.usecase.MarkConversationReadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A driver↔rider chat conversation. */
data class ChatUiState(
    val driverName: String = "",
    val driverImageUrl: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isLoading: Boolean = false,
    /** The other party is typing right now (from the `typing-event` socket push). */
    val otherTyping: Boolean = false,
)

private const val TYPING_STOP_DEBOUNCE_MS = 1500L

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val socketService: SocketService,
    private val getChatHistory: GetChatHistoryUseCase,
    private val markConversationRead: MarkConversationReadUseCase,
    private val userIdProvider: UserIdProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** The driver's user id (chat `receiverId` + history key). */
    private var receiverId: String = ""

    /** Latest known conversation id (empty until the first message/history establishes it). */
    private var conversationId: String? = null

    private var started = false
    private var typingStopJob: Job? = null
    private var localTyping = false

    /** Called once from the screen with the accepted-trip's driver details. Idempotent. */
    fun start(driverId: String, driverName: String, driverImageUrl: String?) {
        if (started) return
        started = true
        receiverId = driverId
        _state.update { it.copy(driverName = driverName, driverImageUrl = driverImageUrl) }
        observeSocket()
        loadHistory()
    }

    private fun loadHistory() {
        if (receiverId.isBlank()) return
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = getChatHistory(receiverId)) {
                is ApiResult.Success -> {
                    val history = result.data
                    conversationId = history.lastOrNull { !it.conversationId.isNullOrBlank() }?.conversationId
                        ?: conversationId
                    _state.update { it.copy(isLoading = false, messages = history) }
                    markReadIfPossible()
                }
                is ApiResult.Error, is ApiResult.Failure -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun observeSocket() {
        viewModelScope.launch {
            socketService.chatEvents.collect { event ->
                when (event) {
                    is ChatSocketEvent.Received -> onReceived(event.message)
                    is ChatSocketEvent.Ack -> onAck(event.mediaIdentifier, event.messageId, event.status)
                    is ChatSocketEvent.Delivered -> updateStatus(event.messageId, ChatMessageStatus.Delivered)
                    is ChatSocketEvent.Read -> updateStatus(event.messageId, ChatMessageStatus.Read)
                    is ChatSocketEvent.Typing ->
                        if (event.senderId == receiverId) _state.update { it.copy(otherTyping = event.typing) }
                }
            }
        }
    }

    private fun onReceived(message: ChatMessage) {
        // The socket layer already excludes our own echo; in this 1:1 trip chat any incoming message is
        // the driver's (matches ride-android, which only filters out self). Track the conversation id and
        // de-dupe by messageId in case the same message also arrived via history.
        message.conversationId?.let { conversationId = it }
        _state.update { st ->
            if (message.messageId.isNotBlank() && st.messages.any { it.messageId == message.messageId }) {
                st
            } else {
                st.copy(messages = st.messages + message, otherTyping = false)
            }
        }
    }

    private fun onAck(mediaIdentifier: String, messageId: String, status: ChatMessageStatus) {
        _state.update { st ->
            st.copy(
                messages = st.messages.map {
                    if (it.mediaIdentifier.isNotBlank() && it.mediaIdentifier == mediaIdentifier) {
                        it.copy(messageId = messageId, status = maxOf(it.status, status))
                    } else {
                        it
                    }
                },
            )
        }
    }

    private fun updateStatus(messageId: String, status: ChatMessageStatus) {
        if (messageId.isBlank()) return
        _state.update { st ->
            st.copy(
                messages = st.messages.map {
                    if (it.messageId == messageId && it.isMine) it.copy(status = maxOf(it.status, status)) else it
                },
            )
        }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
        // Emit typing start on first keystroke; auto-send a stop after a short idle (matches ride-android).
        if (value.isNotBlank()) {
            if (!localTyping) {
                localTyping = true
                socketService.sendChatTyping(receiverId, conversationId, typing = true)
            }
            typingStopJob?.cancel()
            typingStopJob = viewModelScope.launch {
                delay(TYPING_STOP_DEBOUNCE_MS)
                stopTyping()
            }
        } else {
            stopTyping()
        }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        if (localTyping) {
            localTyping = false
            socketService.sendChatTyping(receiverId, conversationId, typing = false)
        }
    }

    fun sendDraft() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(draft = "") }
        send(text)
    }

    /** Quick-reply chip tapped — send immediately. */
    fun sendQuickReply(text: String) = send(text.trim())

    private fun send(text: String) {
        if (text.isEmpty() || receiverId.isBlank()) return
        stopTyping()
        val mediaIdentifier = System.currentTimeMillis().toString()
        val optimistic = ChatMessage(
            messageId = mediaIdentifier,
            mediaIdentifier = mediaIdentifier,
            content = text,
            type = ChatMessageType.Text,
            senderId = userIdProvider.userId.orEmpty(),
            receiverId = receiverId,
            conversationId = conversationId,
            timestampMillis = System.currentTimeMillis(),
            status = ChatMessageStatus.Sending,
            mediaUrl = null,
            isMine = true,
        )
        _state.update { it.copy(messages = it.messages + optimistic) }
        socketService.sendChatMessage(receiverId, conversationId, text, mediaIdentifier)
    }

    private fun markReadIfPossible() {
        val id = conversationId
        if (id.isNullOrBlank()) return
        viewModelScope.launch { markConversationRead(id) }
    }

    override fun onCleared() {
        stopTyping()
        super.onCleared()
    }
}

/** Orders statuses so a later socket event can't regress a message (Sending < Sent < Delivered < Read). */
private fun maxOf(a: ChatMessageStatus, b: ChatMessageStatus): ChatMessageStatus =
    if (a.ordinal >= b.ordinal) a else b
