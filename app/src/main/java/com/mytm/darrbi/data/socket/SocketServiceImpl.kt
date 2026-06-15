package com.mytm.darrbi.data.socket

import android.util.Log
import com.mytm.darrbi.core.common.EnvConfig
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.domain.repository.SocketConnectionState
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.repository.TripSocketEvent
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Socket.IO connection to the socket host, reproducing ride-android's `SocketTask`:
 * - connects to [EnvConfig.socketUrl] on the default namespace with no handshake auth;
 * - uses an OkHttp factory with infinite timeouts (so the connection isn't dropped on idle);
 * - registers identity AFTER connecting by emitting `subscribe-user` with `{"userID": <id>}`.
 * Reconnection uses the client's built-in logic; each (re)connect re-emits `subscribe-user`.
 */
@Singleton
class SocketServiceImpl @Inject constructor(
    private val env: EnvConfig,
    private val userIdProvider: UserIdProvider,
) : SocketService {

    private val _connectionState = MutableStateFlow(SocketConnectionState.Disconnected)
    override val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    // Buffered so events emitted from the socket callback thread are never dropped before collection.
    private val _tripEvents = MutableSharedFlow<TripSocketEvent>(extraBufferCapacity = 16)
    override val tripEvents: SharedFlow<TripSocketEvent> = _tripEvents.asSharedFlow()

    @Volatile
    private var socket: Socket? = null

    @Synchronized
    override fun connect() {
        socket?.let {
            if (it.connected()) return
            _connectionState.value = SocketConnectionState.Connecting
            it.connect()
            return
        }
        val created = runCatching { createSocket() }.getOrNull() ?: return
        socket = created
        _connectionState.value = SocketConnectionState.Connecting
        created.connect()
    }

    private fun createSocket(): Socket {
        // Infinite timeouts keep the long-lived connection alive (matches ride-android).
        val client = OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val options = IO.Options().apply {
            callFactory = client
            webSocketFactory = client
        }
        return IO.socket(env.socketUrl, options).apply {
            io().timeout(-1)
            on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "connected")
                _connectionState.value = SocketConnectionState.Connected
                subscribeUser()
            }
            on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "disconnected")
                _connectionState.value = SocketConnectionState.Disconnected
            }
            on(Socket.EVENT_CONNECT_ERROR) {
                Log.d(TAG, "connect error")
                _connectionState.value = SocketConnectionState.Disconnected
            }
            on(TRIP_DETAIL) { args -> onTripDetail(args) }
        }
    }

    /** Parses a `trip-detail` push, acks it (if it carries an ackId), and maps known actions to events. */
    private fun onTripDetail(args: Array<out Any?>) {
        val data = args.getOrNull(0) ?: return
        val json = runCatching { JSONObject(data.toString()) }.getOrNull() ?: return
        // Acknowledge receipt so the server stops re-delivering (matches ride-android's emit-ack).
        json.optString("ackId").takeIf { it.isNotBlank() }?.let { ackId ->
            runCatching { socket?.emit(CLIENT_ACK, JSONObject().put("ackId", ackId)) }
        }
        when (json.optString("action")) {
            ACTION_NO_DRIVERS -> _tripEvents.tryEmit(TripSocketEvent.NoCaptainFound)
            ACTION_TRIP_EXPIRED -> _tripEvents.tryEmit(TripSocketEvent.TripExpired)
        }
    }

    /** Registers the session room so the server can push user-scoped events. */
    private fun subscribeUser() {
        val userId = userIdProvider.userId ?: return
        runCatching { socket?.emit("subscribe-user", JSONObject().put("userID", userId)) }
    }

    @Synchronized
    override fun disconnect() {
        socket?.disconnect()
        _connectionState.value = SocketConnectionState.Disconnected
    }

    override fun isConnected(): Boolean = socket?.connected() == true

    private companion object {
        const val TAG = "SocketService"
        // Channel + action names from ride-android's SocketTask.
        const val TRIP_DETAIL = "trip-detail"
        const val CLIENT_ACK = "emit-ack"
        const val ACTION_NO_DRIVERS = "no_drivers"
        const val ACTION_TRIP_EXPIRED = "trip_expired"
    }
}
