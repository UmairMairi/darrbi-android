package com.mytm.darrbi.data.socket

import android.util.Log
import com.mytm.darrbi.core.common.EnvConfig
import com.mytm.darrbi.core.common.UserIdProvider
import com.mytm.darrbi.data.mapper.toDomain
import com.mytm.darrbi.data.remote.dto.BidDto
import com.mytm.darrbi.data.remote.dto.FareRangeDto
import com.mytm.darrbi.data.remote.dto.OpenTripDto
import com.mytm.darrbi.domain.model.AcceptedTrip
import com.mytm.darrbi.domain.model.Bid
import com.mytm.darrbi.domain.model.ChatMessage
import com.mytm.darrbi.domain.model.ChatMessageStatus
import com.mytm.darrbi.domain.model.ChatMessageType
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.OpenTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.RideRequest
import com.mytm.darrbi.domain.repository.ChatSocketEvent
import com.mytm.darrbi.domain.repository.NearbyDriver
import com.mytm.darrbi.domain.repository.SocketConnectionState
import com.mytm.darrbi.domain.repository.SocketService
import com.mytm.darrbi.domain.repository.TripSocketEvent
import com.mytm.darrbi.domain.repository.V2SocketEvent
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
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
 *
 * Every socket task is logged under the [TAG] tag (filter logcat by `SocketService`).
 */
@Singleton
class SocketServiceImpl @Inject constructor(
    private val env: EnvConfig,
    private val userIdProvider: UserIdProvider,
    private val json: Json,
) : SocketService {

    private val _connectionState = MutableStateFlow(SocketConnectionState.Disconnected)
    override val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    // Buffered so events emitted from the socket callback thread are never dropped before collection.
    private val _tripEvents = MutableSharedFlow<TripSocketEvent>(extraBufferCapacity = 16)
    override val tripEvents: SharedFlow<TripSocketEvent> = _tripEvents.asSharedFlow()

    private val _nearbyDrivers = MutableStateFlow<List<NearbyDriver>>(emptyList())
    override val nearbyDrivers: StateFlow<List<NearbyDriver>> = _nearbyDrivers.asStateFlow()

    private val _driverLocation = MutableStateFlow<LatLngPoint?>(null)
    override val driverLocation: StateFlow<LatLngPoint?> = _driverLocation.asStateFlow()

    // Buffered so chat pushes from the socket callback thread aren't dropped before the chat screen collects.
    private val _chatEvents = MutableSharedFlow<ChatSocketEvent>(extraBufferCapacity = 64)
    override val chatEvents: SharedFlow<ChatSocketEvent> = _chatEvents.asSharedFlow()

    // V2 broadcast-dispatch: the driver's live open-trips set + the bidding event stream.
    private val _openTrips = MutableStateFlow<List<OpenTrip>>(emptyList())
    override val openTrips: StateFlow<List<OpenTrip>> = _openTrips.asStateFlow()

    private val _v2Events = MutableSharedFlow<V2SocketEvent>(extraBufferCapacity = 32)
    override val v2Events: SharedFlow<V2SocketEvent> = _v2Events.asSharedFlow()

    @Volatile
    private var socket: Socket? = null

    @Synchronized
    override fun connect() {
        socket?.let {
            if (it.connected()) {
                // Already connected → re-assert the user's room so user-targeted pushes (bid updates,
                // trip-detail) reach the CURRENT user even if the socket connected before login/with a stale id.
                Log.d(TAG, "connect(): already connected; re-subscribing")
                subscribeUser()
                return
            }
            Log.d(TAG, "connect(): reconnecting existing socket")
            _connectionState.value = SocketConnectionState.Connecting
            it.connect()
            return
        }
        Log.d(TAG, "connect(): creating socket → ${env.socketUrl}")
        val created = runCatching { createSocket() }.getOrElse {
            Log.w(TAG, "connect(): failed to create socket: ${it.message}")
            return
        }
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
                Log.d(TAG, "EVENT_CONNECT: connected")
                _connectionState.value = SocketConnectionState.Connected
                subscribeUser()
            }
            on(Socket.EVENT_DISCONNECT) { args ->
                Log.d(TAG, "EVENT_DISCONNECT: ${args.joinToString()}")
                _connectionState.value = SocketConnectionState.Disconnected
            }
            on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.w(TAG, "EVENT_CONNECT_ERROR: ${args.joinToString()}")
                _connectionState.value = SocketConnectionState.Disconnected
            }
            on(TRIP_DETAIL) { args ->
                Log.d(TAG, "recv $TRIP_DETAIL: ${args.joinToString()}")
                onTripDetail(args)
            }
            on(FIND_DRIVERS) { args ->
                Log.d(TAG, "recv $FIND_DRIVERS: ${args.joinToString()}")
                onFindDrivers(args)
            }
            on(DRIVER_LOCATION_UPDATE) { args ->
                Log.d(TAG, "recv $DRIVER_LOCATION_UPDATE: ${args.joinToString()}")
                onDriverLocationUpdate(args)
            }
            // --- Chat channels (shared connection, matching ride-android's SocketTask) ---
            on(RECEIVE_MESSAGE) { args ->
                Log.d(TAG, "recv $RECEIVE_MESSAGE: ${args.joinToString()}")
                onIncomingMessage(args)
            }
            on(SEND_MESSAGE_ACK) { args ->
                Log.d(TAG, "recv $SEND_MESSAGE_ACK: ${args.joinToString()}")
                onSendMessageAck(args)
            }
            on(MESSAGE_DELIVERED) { args ->
                Log.d(TAG, "recv $MESSAGE_DELIVERED: ${args.joinToString()}")
                onMessageStatus(args, delivered = true)
            }
            on(MESSAGE_READ) { args ->
                Log.d(TAG, "recv $MESSAGE_READ: ${args.joinToString()}")
                onMessageStatus(args, delivered = false)
            }
            on(TYPING_EVENT) { args ->
                Log.d(TAG, "recv $TYPING_EVENT: ${args.joinToString()}")
                onTypingEvent(args)
            }
            // --- V2 broadcast-dispatch + bidding (envelope {v,event,emittedAt,data}) ---
            on(NEW_TRIP_REQUEST) { args -> Log.d(TAG, "recv $NEW_TRIP_REQUEST"); onNewTripRequest(args) }
            on(OPEN_TRIPS_LIST) { args -> Log.d(TAG, "recv $OPEN_TRIPS_LIST"); onOpenTripsList(args) }
            on(OPEN_TRIPS_UPDATE) { args -> Log.d(TAG, "recv $OPEN_TRIPS_UPDATE"); onOpenTripsUpdate(args) }
            on(V2_BIDS_UPDATE) { args -> Log.d(TAG, "recv $V2_BIDS_UPDATE"); onBidsUpdate(args) }
            on(V2_BID_ACCEPTED) { args -> Log.d(TAG, "recv $V2_BID_ACCEPTED"); onBidAccepted(args) }
            on(V2_DRIVER_SELECTED) { args -> Log.d(TAG, "recv $V2_DRIVER_SELECTED"); onBidAccepted(args) }
            on(V2_BID_WON) { args -> Log.d(TAG, "recv $V2_BID_WON"); onBidWon(args) }
            on(V2_BID_LOST) { args -> Log.d(TAG, "recv $V2_BID_LOST"); onBidLost(args) }
            on(V2_BID_REJECTED) { args -> Log.d(TAG, "recv $V2_BID_REJECTED"); onBidOutcome(args) { t, b -> V2SocketEvent.BidRejected(t, b) } }
            on(V2_BID_EXPIRED) { args -> Log.d(TAG, "recv $V2_BID_EXPIRED"); onBidOutcome(args) { t, b -> V2SocketEvent.BidExpired(t, b) } }
            on(V2_TRIP_CLOSED) { args -> Log.d(TAG, "recv $V2_TRIP_CLOSED"); onTripClosed(args) }
            on(V2_NO_BIDS) { args -> Log.d(TAG, "recv $V2_NO_BIDS"); v2Data(args)?.optString("tripId")?.takeIf { it.isNotBlank() }?.let { _v2Events.tryEmit(V2SocketEvent.NoBids(it)) } }
            on(V2_BIDDING_TIMEOUT) { args -> Log.d(TAG, "recv $V2_BIDDING_TIMEOUT"); v2Data(args)?.optString("tripId")?.takeIf { it.isNotBlank() }?.let { _v2Events.tryEmit(V2SocketEvent.BiddingTimeout(it)) } }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // V2 broadcast-dispatch + bidding (server → client). Each event is wrapped in {v,event,emittedAt,data}.
    // ---------------------------------------------------------------------------------------------

    /** Extracts the envelope's `data` object (falling back to the root if the payload isn't wrapped). */
    private fun v2Data(args: Array<out Any?>): JSONObject? {
        val root = runCatching { JSONObject(args.getOrNull(0).toString()) }.getOrNull() ?: return null
        return root.optJSONObject("data") ?: root
    }

    private fun decodeOpenTrip(obj: JSONObject?): OpenTrip? {
        obj ?: return null
        return runCatching { json.decodeFromString(OpenTripDto.serializer(), obj.toString()).toDomain() }.getOrNull()
    }

    private fun decodeBid(obj: JSONObject?): Bid? {
        obj ?: return null
        return runCatching { json.decodeFromString(BidDto.serializer(), obj.toString()).toDomain() }.getOrNull()
    }

    /** `new-trip-request` → append the single trip to the open set (de-duped by id). */
    private fun onNewTripRequest(args: Array<out Any?>) {
        val trip = decodeOpenTrip(v2Data(args)?.optJSONObject("trip")) ?: return
        _openTrips.update { current -> current.filterNot { it.tripId == trip.tripId } + trip }
        Log.d(TAG, "$NEW_TRIP_REQUEST → +${trip.tripId} (now ${_openTrips.value.size})")
    }

    /** `open-trips-list` → replace the whole list (snapshot). */
    private fun onOpenTripsList(args: Array<out Any?>) {
        val arr = v2Data(args)?.optJSONArray("trips") ?: return
        val trips = (0 until arr.length()).mapNotNull { decodeOpenTrip(arr.optJSONObject(it)) }
        _openTrips.value = trips
        Log.d(TAG, "$OPEN_TRIPS_LIST → ${trips.size} trip(s)")
    }

    /** `open-trips-update` → apply the removed/added/updated delta by tripId. */
    private fun onOpenTripsUpdate(args: Array<out Any?>) {
        val data = v2Data(args) ?: return
        val removed = data.optJSONArray("removed")?.let { a -> (0 until a.length()).map { a.optString(it) } }?.toSet().orEmpty()
        val added = data.optJSONArray("added")?.let { a -> (0 until a.length()).mapNotNull { decodeOpenTrip(a.optJSONObject(it)) } }.orEmpty()
        val updated = data.optJSONArray("updated")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
        _openTrips.update { current ->
            val kept = current.filterNot { it.tripId in removed }.map { trip ->
                val u = updated.firstOrNull { it.optString("tripId") == trip.tripId }
                val fr = u?.optJSONObject("fareRange")
                if (fr != null) {
                    val newRange = runCatching { json.decodeFromString(FareRangeDto.serializer(), fr.toString()).toDomain(trip.fareRange.riderOfferedFare) }.getOrNull()
                    trip.copy(fareRange = newRange ?: trip.fareRange, riderOfferedFare = fr.optDouble("riderOfferedFare", trip.riderOfferedFare))
                } else {
                    trip
                }
            }
            val addedIds = added.map { it.tripId }.toSet()
            kept.filterNot { it.tripId in addedIds } + added
        }
        Log.d(TAG, "$OPEN_TRIPS_UPDATE → -${removed.size} +${added.size} ~${updated.size} (now ${_openTrips.value.size})")
    }

    /** `v2/trip-bids-update` → emit the full (cheapest-first) bid list for the rider. */
    private fun onBidsUpdate(args: Array<out Any?>) {
        val data = v2Data(args) ?: return
        val tripId = data.optString("tripId").takeIf { it.isNotBlank() } ?: return
        val arr = data.optJSONArray("bids")
        val bids = (0 until (arr?.length() ?: 0)).mapNotNull { decodeBid(arr?.optJSONObject(it)) }
        _v2Events.tryEmit(
            V2SocketEvent.BidsUpdate(tripId, bids, data.optDouble("riderOfferedFare", 0.0), data.optString("currency").ifBlank { "SAR" }),
        )
    }

    /** `v2/bid-accepted` / `driver-selected` → the rider's match was committed. */
    private fun onBidAccepted(args: Array<out Any?>) {
        val d = v2Data(args) ?: return
        val tripId = d.optString("tripId").takeIf { it.isNotBlank() } ?: return
        _v2Events.tryEmit(
            V2SocketEvent.BidAccepted(
                tripId = tripId,
                bidId = d.optString("bidId"),
                driverId = d.optString("driverId"),
                agreedFare = d.optDouble("agreedFare", 0.0),
                currency = d.optString("currency").ifBlank { "SAR" },
            ),
        )
    }

    /** `v2/bid-won` → the driver won; switch to the assigned/navigate flow. */
    private fun onBidWon(args: Array<out Any?>) {
        val d = v2Data(args) ?: return
        val tripId = d.optString("tripId").takeIf { it.isNotBlank() } ?: return
        _openTrips.update { it.filterNot { t -> t.tripId == tripId } }
        _v2Events.tryEmit(V2SocketEvent.BidWon(tripId, d.optString("bidId"), d.optDouble("agreedFare", 0.0)))
    }

    private fun onBidLost(args: Array<out Any?>) {
        val d = v2Data(args) ?: return
        val tripId = d.optString("tripId").takeIf { it.isNotBlank() } ?: return
        _openTrips.update { it.filterNot { t -> t.tripId == tripId } }
        _v2Events.tryEmit(V2SocketEvent.BidLost(tripId, d.optString("bidId"), d.optString("reason").takeIf { it.isNotBlank() }))
    }

    private fun onTripClosed(args: Array<out Any?>) {
        val d = v2Data(args) ?: return
        val tripId = d.optString("tripId").takeIf { it.isNotBlank() } ?: return
        _openTrips.update { it.filterNot { t -> t.tripId == tripId } }
        _v2Events.tryEmit(V2SocketEvent.TripClosed(tripId, d.optString("reason").takeIf { it.isNotBlank() }))
    }

    private fun onBidOutcome(args: Array<out Any?>, build: (tripId: String, bidId: String) -> V2SocketEvent) {
        val d = v2Data(args) ?: return
        val tripId = d.optString("tripId").takeIf { it.isNotBlank() } ?: return
        _v2Events.tryEmit(build(tripId, d.optString("bidId")))
    }

    /** Parses a `driver-location-updates` push ({driverId, lat, lon}) into the live driver location. */
    private fun onDriverLocationUpdate(args: Array<out Any?>) {
        val json = runCatching { JSONObject(args.getOrNull(0).toString()) }.getOrNull() ?: return
        val lat = json.optDouble("lat", Double.NaN)
        val lon = json.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return
        Log.d(TAG, "$DRIVER_LOCATION_UPDATE → ($lat, $lon)")
        _driverLocation.value = LatLngPoint(lat, lon)
    }

    override fun findDrivers(latitude: Double, longitude: Double) {
        val userId = userIdProvider.userId ?: run {
            Log.d(TAG, "findDrivers: skipped (no userId)")
            return
        }
        // Payload mirrors ride-android's fetchRealTimeCabs: {userID, data:{latitude, longitude}}.
        val payload = JSONObject()
            .put("userID", userId)
            .put(
                "data",
                JSONObject()
                    .put("latitude", latitude.toString())
                    .put("longitude", longitude.toString()),
            )
        Log.d(TAG, "emit $FIND_DRIVERS: $payload")
        runCatching { socket?.emit(FIND_DRIVERS, payload) }
            .onFailure { Log.w(TAG, "emit $FIND_DRIVERS failed: ${it.message}") }
    }

    /** Parses a `find-drivers` response into nearby-driver markers and publishes them on [nearbyDrivers]. */
    private fun onFindDrivers(args: Array<out Any?>) {
        val data = args.getOrNull(0) ?: return
        val json = runCatching { JSONObject(data.toString()) }.getOrNull() ?: run {
            Log.w(TAG, "$FIND_DRIVERS: unparseable payload")
            return
        }
        val driversJson = json.optJSONArray("drivers")
        val drivers = buildList {
            for (i in 0 until (driversJson?.length() ?: 0)) {
                val d = driversJson?.optJSONObject(i) ?: continue
                val lat = d.optDouble("latitude", Double.NaN)
                val lng = d.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue
                add(NearbyDriver(id = d.optString("id"), latitude = lat, longitude = lng))
            }
        }
        Log.d(TAG, "$FIND_DRIVERS → ${drivers.size} driver(s)")
        _nearbyDrivers.value = drivers
    }

    /** Registers the session room so the server can push user-scoped events. */
    private fun subscribeUser() {
        val userId = userIdProvider.userId
        if (userId == null) {
            Log.w(TAG, "subscribeUser: skipped (no userId)")
            return
        }
        val payload = JSONObject().put("userID", userId)
        Log.d(TAG, "emit $SUBSCRIBE_USER: $payload")
        runCatching { socket?.emit(SUBSCRIBE_USER, payload) }
            .onFailure { Log.w(TAG, "emit $SUBSCRIBE_USER failed: ${it.message}") }
    }

    /** Parses a `trip-detail` push, acks it (if it carries an ackId), and maps known actions to events. */
    private fun onTripDetail(args: Array<out Any?>) {
        val data = args.getOrNull(0)
        if (data == null) {
            Log.w(TAG, "$TRIP_DETAIL: empty payload")
            return
        }
        val json = runCatching { JSONObject(data.toString()) }.getOrNull()
        if (json == null) {
            Log.w(TAG, "$TRIP_DETAIL: unparseable payload")
            return
        }
        // Acknowledge receipt so the server stops re-delivering (matches ride-android's emit-ack).
        json.optString("ackId").takeIf { it.isNotBlank() }?.let { ackId ->
            Log.d(TAG, "emit $CLIENT_ACK: ackId=$ackId")
            runCatching { socket?.emit(CLIENT_ACK, JSONObject().put("ackId", ackId)) }
        }
        val action = json.optString("action")
        Log.d(TAG, "$TRIP_DETAIL action=$action")
        when (action) {
            ACTION_NO_DRIVERS -> {
                Log.d(TAG, "$TRIP_DETAIL → NoCaptainFound")
                _tripEvents.tryEmit(TripSocketEvent.NoCaptainFound)
            }
            ACTION_TRIP_EXPIRED -> {
                Log.d(TAG, "$TRIP_DETAIL → TripExpired")
                _tripEvents.tryEmit(TripSocketEvent.TripExpired)
            }
            ACTION_DRIVER_ACCEPTED -> {
                val trip = parseAcceptedTrip(json)
                if (trip != null) {
                    Log.d(TAG, "$TRIP_DETAIL → DriverAccepted(pin=${trip.pin}, eta=${trip.etaMinutes})")
                    // Seed the live driver location from the accepted trip.
                    if (trip.driverLatitude != null && trip.driverLongitude != null) {
                        _driverLocation.value = LatLngPoint(trip.driverLatitude, trip.driverLongitude)
                    }
                    _tripEvents.tryEmit(TripSocketEvent.DriverAccepted(trip))
                } else {
                    Log.w(TAG, "$TRIP_DETAIL driver_accepted: could not parse trip")
                }
            }
            ACTION_DRIVER_REACHED -> {
                val trip = parseAcceptedTrip(json)
                if (trip != null) {
                    Log.d(TAG, "$TRIP_DETAIL → DriverArrived(pin=${trip.pin})")
                    if (trip.driverLatitude != null && trip.driverLongitude != null) {
                        _driverLocation.value = LatLngPoint(trip.driverLatitude, trip.driverLongitude)
                    }
                    _tripEvents.tryEmit(TripSocketEvent.DriverArrived(trip))
                } else {
                    Log.w(TAG, "$TRIP_DETAIL driver_reached: could not parse trip")
                }
            }
            ACTION_TRIP_STARTED -> {
                val trip = parseAcceptedTrip(json)
                if (trip != null) {
                    Log.d(TAG, "$TRIP_DETAIL → TripStarted(pin=${trip.pin})")
                    _tripEvents.tryEmit(TripSocketEvent.TripStarted(trip))
                } else {
                    Log.w(TAG, "$TRIP_DETAIL trip_started: could not parse trip")
                }
            }
            ACTION_TRIP_COMPLETED -> {
                val trip = parseAcceptedTrip(json)
                if (trip != null) {
                    Log.d(TAG, "$TRIP_DETAIL → TripCompleted(amount=${trip.originalFare})")
                    _tripEvents.tryEmit(TripSocketEvent.TripCompleted(trip))
                } else {
                    Log.w(TAG, "$TRIP_DETAIL trip_completed: could not parse trip")
                }
            }
            ACTION_TRIP_REQUEST -> {
                val request = parseRideRequest(json)
                if (request != null) {
                    // Don't log the rider's name (PII) — just the trip id and earning.
                    Log.d(TAG, "$TRIP_DETAIL → TripRequest(trip=${request.tripId}, earn=${request.estimateEarning})")
                    _tripEvents.tryEmit(TripSocketEvent.TripRequest(request))
                } else {
                    Log.w(TAG, "$TRIP_DETAIL trip_request: could not parse request")
                }
            }
            ACTION_DRIVER_CANCELLED, ACTION_DRIVER_CANCELLED_BEFORE_ARRIVED -> {
                Log.d(TAG, "$TRIP_DETAIL → DriverCancelled")
                _tripEvents.tryEmit(TripSocketEvent.DriverCancelled)
            }
            ACTION_DES_CHANGED -> {
                val data = json.optJSONObject("data")
                // Prefer the updated destination (lat != 0), else fall back to the original (mirrors ride-android).
                val dest = parsePoint(data?.optJSONObject("destinationNew"))?.takeIf { it.latitude != 0.0 }
                    ?: parsePoint(data?.optJSONObject("destination"))
                if (dest != null) {
                    Log.d(TAG, "$TRIP_DETAIL → DestinationChanged(addr=${dest.address})")
                    _tripEvents.tryEmit(TripSocketEvent.DestinationChanged(data?.optString("id").orEmpty(), dest))
                } else {
                    Log.w(TAG, "$TRIP_DETAIL rider_updated_destination: no destination in payload")
                }
            }
            else -> Log.d(TAG, "$TRIP_DETAIL action ignored: $action")
        }
    }

    /** Maps a `trip_request` trip-detail payload to a captain-facing [RideRequest]. */
    private fun parseRideRequest(json: JSONObject): RideRequest? {
        val data = json.optJSONObject("data") ?: return null
        val rider = data.optJSONObject("riderInfo")
        val pickup = parsePoint(data.optJSONObject("source")) ?: return null
        // Prefer the updated destination when present (lat != 0), else the original.
        val destination = parsePoint(data.optJSONObject("destinationNew"))?.takeIf { it.latitude != 0.0 }
            ?: parsePoint(data.optJSONObject("destination")) ?: return null
        val driverAmount = data.optDouble("driverAmount").takeIf { !it.isNaN() }
        val baseAmount = data.optDouble("estimatedBaseAmount").takeIf { !it.isNaN() }
        return RideRequest(
            tripId = data.optString("id"),
            riderId = rider?.optString("id").orEmpty().ifBlank { data.optString("riderId") },
            riderName = rider?.optString("name").orEmpty(),
            riderImageUrl = rider?.optString("profileImage")?.takeIf { it.isNotBlank() },
            riderMobile = rider?.optString("mobile")?.takeIf { it.isNotBlank() },
            riderRating = rider?.optDouble("rating")?.takeIf { rider.has("rating") && !it.isNaN() },
            estimateEarning = driverAmount ?: baseAmount ?: 0.0,
            paymentMethod = data.optInt("paymentMethod", data.optInt("tripType", PAYMENT_METHOD_CASH)),
            pickup = pickup,
            destination = destination,
            destDistanceKm = data.optDouble("tripDistance").takeIf { !it.isNaN() } ?: 0.0,
            destTimeMinutes = data.optDouble("estimatedTripTime").takeIf { !it.isNaN() } ?: 0.0,
        )
    }

    /** Parses a `{address, latitude, longitude}` point object into a [PlaceLocation], or null if no coords. */
    private fun parsePoint(obj: JSONObject?): PlaceLocation? {
        obj ?: return null
        val lat = obj.optDouble("latitude", Double.NaN)
        val lng = obj.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return null
        return PlaceLocation(name = "", address = obj.optString("address"), latitude = lat, longitude = lng)
    }

    /** Maps a `driver_accepted` trip-detail payload to [AcceptedTrip] (PIN, ETA, cab + driver). */
    private fun parseAcceptedTrip(json: JSONObject): AcceptedTrip? {
        val data = json.optJSONObject("data") ?: return null
        val cab = data.optJSONObject("cabInfo")
        val driver = data.optJSONObject("driverInfo")
        return AcceptedTrip(
            tripId = data.optString("id"),
            pin = data.optString("tripOtp"),
            etaMinutes = cab?.optInt("estimatedTimeArrival")?.takeIf { cab.has("estimatedTimeArrival") },
            cabName = cab?.optString("name").orEmpty(),
            cabDescription = cab?.optString("description").orEmpty(),
            seats = cab?.optInt("noOfSeats") ?: 0,
            plateNo = driver?.optString("carPlateNo").orEmpty(),
            driverId = driver?.optString("id").orEmpty().ifBlank { data.optString("driverId") },
            driverName = driver?.optString("name").orEmpty(),
            driverRating = driver?.optDouble("rating")?.takeIf { driver.has("rating") && !it.isNaN() },
            driverImageUrl = driver?.optString("profileImage")?.takeIf { it.isNotBlank() },
            driverMobile = driver?.optString("mobile")?.takeIf { it.isNotBlank() },
            driverLatitude = driver?.optDouble("latitude")?.takeIf { driver.has("latitude") && !it.isNaN() },
            driverLongitude = driver?.optDouble("longitude")?.takeIf { driver.has("longitude") && !it.isNaN() },
            cancellationFee = cab?.optDouble("cancellationCharge")?.takeIf { !it.isNaN() } ?: 0.0,
            cabId = cab?.optString("id").orEmpty(),
            originalFare = data.optDouble("riderAmount").takeIf { !it.isNaN() } ?: 0.0,
            loyaltyPoints = data.optInt("loyaltyPoints", 0),
        )
    }

    override fun updateCaptainLocation(latitude: Double, longitude: Double) {
        val socket = this.socket?.takeIf { it.connected() }
        if (socket == null) {
            Log.d(TAG, "updateCaptainLocation: skipped (socket not connected)")
            return
        }
        val userId = userIdProvider.userId
        if (userId == null) {
            Log.d(TAG, "updateCaptainLocation: skipped (no userId)")
            return
        }
        // Payload mirrors ride-android: {userID, data:{driverId, latitude, longitude}}.
        val payload = JSONObject()
            .put("userID", userId)
            .put(
                "data",
                JSONObject()
                    .put("driverId", userId)
                    .put("latitude", latitude)
                    .put("longitude", longitude),
            )
        Log.d(TAG, "emit $UPDATE_CAPTAIN_LOCATION: $payload")
        runCatching { socket.emit(UPDATE_CAPTAIN_LOCATION, payload) }
            .onFailure { Log.w(TAG, "emit $UPDATE_CAPTAIN_LOCATION failed: ${it.message}") }
    }

    // ---------------------------------------------------------------------------------------------
    // Chat (shared socket): send-message / receive-message / *-ack / *-delivered / *-read / typing.
    // ---------------------------------------------------------------------------------------------

    override fun sendChatMessage(receiverId: String, conversationId: String?, content: String, mediaIdentifier: String) {
        val userId = userIdProvider.userId ?: run {
            Log.d(TAG, "sendChatMessage: skipped (no userId)")
            return
        }
        // Payload mirrors ride-android's publishSendMessage: {userID, data:{receiverId, conversationId,
        // chatType, messageType, messageContent, mediaIdentifier, metadata}}.
        val payload = JSONObject()
            .put("userID", userId)
            .put(
                "data",
                JSONObject()
                    .put("receiverId", receiverId)
                    .put("groupId", "")
                    .put("conversationId", conversationId.orEmpty())
                    .put("chatType", CHAT_TYPE_SINGLE)
                    .put("messageType", MESSAGE_TYPE_TEXT)
                    .put("messageContent", content)
                    .put("mediaIdentifier", mediaIdentifier)
                    .put("metadata", JSONObject()),
            )
        Log.d(TAG, "emit $SEND_MESSAGE: $payload")
        runCatching { socket?.emit(SEND_MESSAGE, payload) }
            .onFailure { Log.w(TAG, "emit $SEND_MESSAGE failed: ${it.message}") }
    }

    override fun markChatMessageRead(messageId: String, senderId: String, receiverId: String) {
        val userId = userIdProvider.userId ?: return
        // Payload mirrors ride-android's publishMessageRead: {userID, data:{messageId, senderId, receiverId}}.
        val payload = JSONObject()
            .put("userID", userId)
            .put(
                "data",
                JSONObject()
                    .put("messageId", messageId)
                    .put("senderId", senderId)
                    .put("receiverId", receiverId),
            )
        Log.d(TAG, "emit $MESSAGE_READ: $payload")
        runCatching { socket?.emit(MESSAGE_READ, payload) }
            .onFailure { Log.w(TAG, "emit $MESSAGE_READ failed: ${it.message}") }
    }

    override fun sendChatTyping(receiverId: String, conversationId: String?, typing: Boolean) {
        val userId = userIdProvider.userId ?: return
        // Payload mirrors ride-android's publishTypingEvent: {userID, data:{conversationId, receiverId, action}}.
        val payload = JSONObject()
            .put("userID", userId)
            .put(
                "data",
                JSONObject()
                    .put("conversationId", conversationId.orEmpty())
                    .put("receiverId", receiverId)
                    .put("action", if (typing) TYPING_START else TYPING_STOP),
            )
        Log.d(TAG, "emit $TYPING_EVENT: $payload")
        runCatching { socket?.emit(TYPING_EVENT, payload) }
            .onFailure { Log.w(TAG, "emit $TYPING_EVENT failed: ${it.message}") }
    }

    /** Parses a `receive-message` push into a [ChatMessage] and, like ride-android, auto-acks it as read. */
    private fun onIncomingMessage(args: Array<out Any?>) {
        val json = runCatching { JSONObject(args.getOrNull(0).toString()) }.getOrNull() ?: return
        val message = parseChatMessage(json) ?: return
        // Ignore our own echo (server uses send-message-ack for that).
        if (message.isMine) return
        _chatEvents.tryEmit(ChatSocketEvent.Received(message))
        // Tell the sender it was read → they see the double green tick (matches ride-android).
        markChatMessageRead(message.messageId, message.senderId, message.receiverId)
    }

    /** Parses `send-message-ack` — reconciles our optimistic message by [mediaIdentifier]. */
    private fun onSendMessageAck(args: Array<out Any?>) {
        val json = runCatching { JSONObject(args.getOrNull(0).toString()) }.getOrNull() ?: return
        val mediaIdentifier = json.optString("mediaIdentifier")
        val messageId = json.optString("messageId")
        if (mediaIdentifier.isBlank() || messageId.isBlank()) return
        val statusInt = json.optJSONObject("receiver")?.optInt("status") ?: json.optInt("status", STATUS_SENT)
        Log.d(TAG, "$SEND_MESSAGE_ACK → media=$mediaIdentifier id=$messageId status=$statusInt")
        _chatEvents.tryEmit(ChatSocketEvent.Ack(mediaIdentifier, messageId, chatStatus(statusInt)))
    }

    /** Parses `message-delivered` / `message-read` (both carry `messageId`) into a status event. */
    private fun onMessageStatus(args: Array<out Any?>, delivered: Boolean) {
        val json = runCatching { JSONObject(args.getOrNull(0).toString()) }.getOrNull() ?: return
        val messageId = json.optString("messageId").takeIf { it.isNotBlank() } ?: return
        if (delivered) {
            _chatEvents.tryEmit(ChatSocketEvent.Delivered(messageId))
        } else {
            _chatEvents.tryEmit(ChatSocketEvent.Read(messageId))
        }
    }

    /** Parses a `typing-event` ({senderId, receiverId, action}) into a typing indicator. */
    private fun onTypingEvent(args: Array<out Any?>) {
        val json = runCatching { JSONObject(args.getOrNull(0).toString()) }.getOrNull() ?: return
        val senderId = json.optString("senderId")
        val typing = json.optString("action") == TYPING_START
        _chatEvents.tryEmit(ChatSocketEvent.Typing(senderId, typing))
    }

    /** Maps a `receive-message` / `send-message-ack` JSON object to a domain [ChatMessage]. */
    private fun parseChatMessage(json: JSONObject): ChatMessage? {
        val senderId = json.optString("senderId")
        val statusInt = json.optInt("status", STATUS_SENT)
        val metadata = json.optJSONObject("metadata")
        return ChatMessage(
            messageId = json.optString("messageId"),
            mediaIdentifier = json.optString("mediaIdentifier"),
            content = json.optString("messageContent"),
            type = chatType(json.optInt("messageType", MESSAGE_TYPE_TEXT)),
            senderId = senderId,
            receiverId = json.optString("receiverId"),
            conversationId = json.optString("conversationId").takeIf { it.isNotBlank() },
            timestampMillis = json.optString("timestamp").toLongOrNull() ?: System.currentTimeMillis(),
            status = chatStatus(statusInt),
            mediaUrl = metadata?.optString("url")?.takeIf { it.isNotBlank() },
            isMine = senderId.isNotBlank() && senderId == userIdProvider.userId,
        )
    }

    private fun chatStatus(status: Int): ChatMessageStatus = when (status) {
        STATUS_READ -> ChatMessageStatus.Read
        STATUS_DELIVERED -> ChatMessageStatus.Delivered
        STATUS_SENT -> ChatMessageStatus.Sent
        else -> ChatMessageStatus.Sending
    }

    private fun chatType(type: Int): ChatMessageType = when (type) {
        MESSAGE_TYPE_IMAGE -> ChatMessageType.Image
        MESSAGE_TYPE_AUDIO -> ChatMessageType.Audio
        MESSAGE_TYPE_TEXT -> ChatMessageType.Text
        else -> ChatMessageType.Other
    }

    @Synchronized
    override fun disconnect() {
        Log.d(TAG, "disconnect() requested")
        socket?.disconnect()
        _connectionState.value = SocketConnectionState.Disconnected
    }

    override fun isConnected(): Boolean = socket?.connected() == true

    private companion object {
        const val TAG = "SocketService"
        // Channel + action names from ride-android's SocketTask.
        const val SUBSCRIBE_USER = "subscribe-user"
        const val FIND_DRIVERS = "find-drivers"
        const val TRIP_DETAIL = "trip-detail"
        const val CLIENT_ACK = "emit-ack"
        const val ACTION_NO_DRIVERS = "no_drivers"
        const val ACTION_TRIP_EXPIRED = "trip_expired"
        const val ACTION_DRIVER_ACCEPTED = "driver_accepted"
        const val ACTION_DRIVER_REACHED = "driver_reached"
        const val ACTION_TRIP_STARTED = "trip_started"
        const val ACTION_TRIP_COMPLETED = "trip_completed"
        const val ACTION_TRIP_REQUEST = "trip_request"
        const val ACTION_DRIVER_CANCELLED = "driver_cancelled"
        const val ACTION_DRIVER_CANCELLED_BEFORE_ARRIVED = "driver_cancelled_before_arrived"
        const val ACTION_DES_CHANGED = "rider_updated_destination"
        const val PAYMENT_METHOD_CASH = 2
        const val UPDATE_CAPTAIN_LOCATION = "update-captain-location"
        const val DRIVER_LOCATION_UPDATE = "driver-location-updates"
        // Chat channels (ride-android's SocketTask).
        const val SEND_MESSAGE = "send-message"
        const val RECEIVE_MESSAGE = "receive-message"
        const val SEND_MESSAGE_ACK = "send-message-ack"
        const val MESSAGE_DELIVERED = "message-delivered"
        const val MESSAGE_READ = "message-read"
        const val TYPING_EVENT = "typing-event"
        const val TYPING_START = "start"
        const val TYPING_STOP = "stop"
        // Chat wire enums (ride-android's Constants).
        const val CHAT_TYPE_SINGLE = 1
        const val MESSAGE_TYPE_TEXT = 1
        const val MESSAGE_TYPE_IMAGE = 2
        const val MESSAGE_TYPE_AUDIO = 3
        // Message status: 1 = sent, 3 = delivered, 4 = read (0 = waiting/sending).
        const val STATUS_SENT = 1
        const val STATUS_DELIVERED = 3
        const val STATUS_READ = 4
        // V2 broadcast-dispatch + bidding event names (V2_MOBILE_INTEGRATION_GUIDE.md §5–6).
        const val NEW_TRIP_REQUEST = "new-trip-request"
        const val OPEN_TRIPS_LIST = "open-trips-list"
        const val OPEN_TRIPS_UPDATE = "open-trips-update"
        const val V2_BIDS_UPDATE = "v2/trip-bids-update"
        const val V2_BID_ACCEPTED = "v2/bid-accepted"
        const val V2_DRIVER_SELECTED = "driver-selected"
        const val V2_BID_WON = "v2/bid-won"
        const val V2_BID_LOST = "v2/bid-lost"
        const val V2_BID_REJECTED = "v2/bid-rejected"
        const val V2_BID_EXPIRED = "v2/bid-expired"
        const val V2_TRIP_CLOSED = "v2/trip-closed"
        const val V2_NO_BIDS = "v2/no-bids"
        const val V2_BIDDING_TIMEOUT = "v2/bidding-timeout"
    }
}
