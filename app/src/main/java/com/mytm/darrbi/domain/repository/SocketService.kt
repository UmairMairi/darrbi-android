package com.mytm.darrbi.domain.repository

import com.mytm.darrbi.domain.model.AcceptedTrip
import com.mytm.darrbi.domain.model.Bid
import com.mytm.darrbi.domain.model.ChatMessage
import com.mytm.darrbi.domain.model.ChatMessageStatus
import com.mytm.darrbi.domain.model.LatLngPoint
import com.mytm.darrbi.domain.model.OpenTrip
import com.mytm.darrbi.domain.model.PlaceLocation
import com.mytm.darrbi.domain.model.RideRequest
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Live state of the real-time socket connection. */
enum class SocketConnectionState { Disconnected, Connecting, Connected }

/** A nearby driver (captain) from the `find-drivers` socket response — used to show captain markers. */
data class NearbyDriver(
    val id: String,
    val latitude: Double,
    val longitude: Double,
)

/** Real-time trip events pushed by the server on the `trip-detail` channel (subset we currently handle). */
sealed interface TripSocketEvent {
    /** No available captain was found for the pending request (`action == "no_drivers"`). */
    data object NoCaptainFound : TripSocketEvent

    /** The pending request expired before any captain accepted (`action == "trip_expired"`). */
    data object TripExpired : TripSocketEvent

    /** A captain accepted the trip (`action == "driver_accepted"`). */
    data class DriverAccepted(val trip: AcceptedTrip) : TripSocketEvent

    /** The captain reached the pickup point (`action == "driver_reached"`). */
    data class DriverArrived(val trip: AcceptedTrip) : TripSocketEvent

    /** The trip started — captain is driving the rider to the destination (`action == "trip_started"`). */
    data class TripStarted(val trip: AcceptedTrip) : TripSocketEvent

    /** The trip finished — show the rate-your-captain screen (`action == "trip_completed"`). */
    data class TripCompleted(val trip: AcceptedTrip) : TripSocketEvent

    /** The captain cancelled the trip (`action == "driver_cancelled"`) — rider returns to ride selection. */
    data object DriverCancelled : TripSocketEvent

    /** CAPTAIN side: an incoming ride request to accept/decline (`action == "trip_request"`). */
    data class TripRequest(val request: RideRequest) : TripSocketEvent

    /** CAPTAIN side: the rider changed the drop-off location mid-trip (`action == "rider_updated_destination"`). */
    data class DestinationChanged(val tripId: String, val newDestination: PlaceLocation) : TripSocketEvent
}

/**
 * Real-time chat events on the shared socket (mirrors ride-android's `SocketTask` chat channels):
 * `receive-message`, `send-message-ack`, `message-delivered`, `message-read`, `typing-event`.
 */
sealed interface ChatSocketEvent {
    /** A message from the other party (`receive-message`). */
    data class Received(val message: ChatMessage) : ChatSocketEvent

    /** Server acknowledged our sent message (`send-message-ack`): assigns the real id + status. */
    data class Ack(val mediaIdentifier: String, val messageId: String, val status: ChatMessageStatus) : ChatSocketEvent

    /** Our message reached the recipient's device (`message-delivered`) → double grey tick. */
    data class Delivered(val messageId: String) : ChatSocketEvent

    /** The recipient read our message (`message-read`) → double green tick. */
    data class Read(val messageId: String) : ChatSocketEvent

    /** The other party started/stopped typing (`typing-event`). */
    data class Typing(val senderId: String, val typing: Boolean) : ChatSocketEvent
}

/**
 * V2 broadcast-dispatch + bidding events pushed by the server (all wrapped in the `{v,event,emittedAt,data}`
 * envelope). The open-trips list is exposed as state; bid outcomes/updates flow through [SocketService.v2Events].
 */
sealed interface V2SocketEvent {
    /** RIDER: the live competing-bids list for a trip changed (cheapest-first; full replacement). */
    data class BidsUpdate(val tripId: String, val bids: List<Bid>, val riderOfferedFare: Double, val currency: String) : V2SocketEvent

    /** RIDER: the match was committed (`v2/bid-accepted` / `driver-selected`). */
    data class BidAccepted(val tripId: String, val bidId: String, val driverId: String, val agreedFare: Double, val currency: String) : V2SocketEvent

    /** DRIVER: you won the trip (`v2/bid-won`) → switch to the assigned/navigate flow. */
    data class BidWon(val tripId: String, val bidId: String, val agreedFare: Double) : V2SocketEvent

    /** DRIVER: you lost (`v2/bid-lost`) — `reason` = ANOTHER_DRIVER_SELECTED | LOST_DRIVER_RACE | DRIVER_INELIGIBLE. */
    data class BidLost(val tripId: String, val bidId: String, val reason: String?) : V2SocketEvent

    /** DRIVER: the rider dismissed your bid (`v2/bid-rejected`). */
    data class BidRejected(val tripId: String, val bidId: String) : V2SocketEvent

    /** DRIVER: your 45s bid TTL elapsed (`v2/bid-expired`) — you may re-bid. */
    data class BidExpired(val tripId: String, val bidId: String) : V2SocketEvent

    /** DRIVER: the trip ended before a match (`v2/trip-closed`) — RIDER_CANCELLED | EXPIRED | NO_DRIVER. */
    data class TripClosed(val tripId: String, val reason: String?) : V2SocketEvent

    /** RIDER: no bids yet (`v2/no-bids`) — nudge to raise the offer. */
    data class NoBids(val tripId: String) : V2SocketEvent

    /** RIDER: the bidding window elapsed with no selection (`v2/bidding-timeout`). */
    data class BiddingTimeout(val tripId: String) : V2SocketEvent
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

    /** Nearby drivers from the latest `find-drivers` response (captain markers on the rider map). */
    val nearbyDrivers: StateFlow<List<NearbyDriver>>

    /** Assigned driver's live location while on the way (`driver-location-updates`); null until known. */
    val driverLocation: StateFlow<LatLngPoint?>

    /** In-trip chat events (received messages, ack/delivered/read status, typing) on the shared socket. */
    val chatEvents: SharedFlow<ChatSocketEvent>

    /** DRIVER (V2): the live broadcast set of open (awaiting-bids) trips, from list/new/update events. */
    val openTrips: StateFlow<List<OpenTrip>>

    /** V2 bidding events (live bids, bid outcomes) for both rider and driver. */
    val v2Events: SharedFlow<V2SocketEvent>

    /** Opens the connection (idempotent) and, on connect, registers the user via `subscribe-user`. */
    fun connect()

    /**
     * Emits the captain's current location to the server (`update-captain-location`), matching
     * ride-android's `updateDriverLocation`. No-op if the socket isn't connected.
     */
    fun updateCaptainLocation(latitude: Double, longitude: Double)

    /**
     * Requests nearby drivers around [latitude]/[longitude] via the `find-drivers` socket event
     * (matches ride-android's `fetchRealTimeCabs`); results arrive on [nearbyDrivers].
     */
    fun findDrivers(latitude: Double, longitude: Double)

    /**
     * Sends a text chat message (`send-message`, messageType = text) to [receiverId]. [mediaIdentifier]
     * is echoed back in `send-message-ack` so the optimistic message can be reconciled.
     */
    fun sendChatMessage(receiverId: String, conversationId: String?, content: String, mediaIdentifier: String)

    /** Acks a received message as read (`message-read`) so the sender sees the double green tick. */
    fun markChatMessageRead(messageId: String, senderId: String, receiverId: String)

    /** Publishes a typing start/stop indicator (`typing-event`) to [receiverId]. */
    fun sendChatTyping(receiverId: String, conversationId: String?, typing: Boolean)

    /** Closes the connection and stops auto-reconnection. */
    fun disconnect()

    fun isConnected(): Boolean
}
