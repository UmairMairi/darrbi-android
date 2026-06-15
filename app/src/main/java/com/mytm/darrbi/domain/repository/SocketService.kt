package com.mytm.darrbi.domain.repository

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Live state of the real-time socket connection. */
enum class SocketConnectionState { Disconnected, Connecting, Connected }

/** Real-time trip events pushed by the server on the `trip-detail` channel (subset we currently handle). */
sealed interface TripSocketEvent {
    /** No available captain was found for the pending request (`action == "no_drivers"`). */
    data object NoCaptainFound : TripSocketEvent

    /** The pending request expired before any captain accepted (`action == "trip_expired"`). */
    data object TripExpired : TripSocketEvent
}

/**
 * Real-time Socket.IO connection (mirrors ride-android's `SocketTask`): an unauthenticated connection to
 * the socket host that registers the session by emitting `subscribe-user` once connected. A single
 * app-scoped service so both rider and captain dashboards share one connection.
 */
interface SocketService {
    val connectionState: StateFlow<SocketConnectionState>

    /** Trip events from the `trip-detail` channel (e.g. [TripSocketEvent.NoCaptainFound]). */
    val tripEvents: SharedFlow<TripSocketEvent>

    /** Opens the connection (idempotent) and, on connect, registers the user via `subscribe-user`. */
    fun connect()

    /** Closes the connection and stops auto-reconnection. */
    fun disconnect()

    fun isConnected(): Boolean
}
