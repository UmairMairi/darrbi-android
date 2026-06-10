package com.mytm.darrbi.core.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionEvent {
    /** Emitted when any request returns HTTP 401; the shell collects this and routes to login. */
    data object SessionExpired : SessionEvent
}

/** App-wide bus for session lifecycle events (replaces ride-android's `AppUtils.handleSessionExpired`). */
@Singleton
class SessionEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<SessionEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    fun tryEmit(event: SessionEvent) {
        _events.tryEmit(event)
    }
}
