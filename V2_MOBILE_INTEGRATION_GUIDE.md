# V2 Mobile Integration Guide — Broadcast Dispatch + inDrive-Style Bidding

> **Audience:** mobile (driver + rider) developers.
> **Status:** matches the implemented + E2E-tested code on branch `ride-demo-v2`.
> **Transport:** WebSocket (Socket.IO) for real-time + REST (api-gateway `/v2/trips`) as a fallback for every action.
> **Base URLs (demo):** REST `http://<host>:3010`  •  WebSocket `http://<host>:8100`
> **Auth:** every REST call sends the login JWT in the **`sessionid`** header (NOT `Authorization`). Sockets identify the user with `subscribe-user`.

All sample values below are real shapes from the running system — you can copy them as fixtures.

---

## 0. TL;DR — the whole flow in 8 steps

1. Rider logs in, **`POST /v2/trips`** with a proposed fare → trip opens (`status 15 AWAITING_BIDS`) and the server returns the **fare range**.
2. Server **broadcasts `new-trip-request`** to every connected driver; drivers also see it in their list.
3. Each in-range driver sees the trip with its **fare range** and **bids** — either *accept the fare* or *counter* with their own price (**`v2/place-bid`**).
4. Rider receives a **live list of bids** (**`v2/trip-bids-update`**), sorted cheapest-first.
5. Rider **picks one** (**`v2/select-bid`** / `PATCH .../accept`).
6. Winner gets **`v2/bid-won`**, losers get **`v2/bid-lost`**, all drivers get **`open-trips-update {removed}`**, rider gets **`v2/bid-accepted`**.
7. Payment is **held at selection** for the agreed fare; trip becomes `ACCEPTED_BY_DRIVER (2)`.
8. From here it's a **normal V1 trip** — reuse your existing driver-reached / OTP-start / complete / pay code.

---

## 1. What changed: V1 ("before") vs V2 ("now")

| Area | V1 (before) | V2 (now) |
|---|---|---|
| Trip delivery to driver | ONE trip at a time, pushed on heartbeat (`trip-detail`) | **ALL in-range open trips at once** (a list); a new trip is **broadcast to every driver** |
| New trip | added to a pool, drivers pulled one-by-one | `new-trip-request` **broadcast** to all connected drivers instantly |
| Open-set changes | none pushed | `open-trips-update` delta re-broadcast on accept/expire/cancel/raise |
| Heartbeat | pulls next single trip | pulls the **full in-range list** (`open-trips-list` snapshot) |
| Pricing | platform-fixed fare | **rider proposes a fare**; driver sees a **fare range** and **bids** (accept or counter) |
| Match | first driver to accept wins | **rider picks** a driver from a live list of competing bids |
| Eligibility gate | checked before a trip is offered | **checked at bid time** — an ineligible driver still *sees* trips but is blocked when bidding |
| Trip status | 1–14 | adds `AWAITING_BIDS = 15`, `DRIVER_SELECTED = 16` |
| Final fare | platform meter | **winning bid amount** + tax/fees |

**Backward compatible:** V1 events/REST are unchanged. V2 is gated by a per-trip `dispatchMode` (`AUTO`=1 legacy, `BID`=2 new) and the server flag `SETTING_BROADCAST_DISPATCH`. After a bid is accepted, the trip re-enters the **same V1 post-acceptance lifecycle** (driver-reached → OTP start → complete → pay) — reuse your existing V1 code for everything after assignment.

---

## 2. Enums & the message envelope

```
bidType:     1 = ACCEPT_FARE (take the rider's offered fare as-is)
             2 = COUNTER     (offer your own price)

BidStatus:   1 = PENDING, 2 = ACCEPTED, 3 = REJECTED, 4 = WITHDRAWN,
             5 = EXPIRED, 6 = CANCELLED, 7 = LOST_RACE

TripStatus (additions): 15 = AWAITING_BIDS, 16 = DRIVER_SELECTED
(after selection the trip becomes 2 = ACCEPTED_BY_DRIVER and follows the V1 lifecycle)

dispatchMode: 1 = AUTO (legacy), 2 = BID (this guide)
paymentMethod: 1 = card, 2 = wallet
addressType: 1 = pickup, 2 = destination
```

**Every server→client V2 socket event uses this envelope:**
```jsonc
{
  "v": 2,                          // protocol version discriminator
  "event": "open-trips-update",    // echoes the event name
  "emittedAt": 1718409600000,      // server epoch ms
  "data": { /* event-specific body, documented per event below */ }
}
```

**Client→server socket actions** send a flat payload + an optional Socket.IO **ack callback**. The ack receives `{ ok, data }` on success or `{ ok:false, error:{ code, message, details? } }` on failure.

---

## 3. Authentication (both apps)

V2 reuses the existing login. Three calls:

**3.1 Send OTP** — `POST /sendotp`
```jsonc
// request
{ "mobileNo": "966533670676" }
// response
{ "statusCode": 200, "data": { "tId": "2f4cafb8-...-uuid", "SmsApiResponse": { "...": "..." } } }
```

**3.2 Verify OTP** — `POST /verifyotp`  (needs the `tId` from step 3.1)
```jsonc
// request
{ "mobileNo": "966533670676", "otp": "676", "tId": "2f4cafb8-...-uuid" }
// response
{
  "statusCode": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",   // <- use as the `sessionid` header
    "details": { "userId": "966110000002", "firstName": "Hazik", "mobileNo": "966533670676", "prefferedLanguage": "en", "...": "..." }
  }
}
```

**3.3 Use the token** — every authenticated REST call sends:
```
sessionid: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

> Gotchas: `sendotp` is **rate-limited (~15s per number)**. There's also a **~30s cooldown** between trip creations per rider. The socket does NOT take the JWT — it identifies the user via `subscribe-user { userID }` (the `userId` from 3.2).

---

## 4. Socket connection & presence (both apps)

```js
import { io } from 'socket.io-client';
const socket = io('http://<host>:8100', { transports: ['websocket'], reconnection: true });

socket.on('connect', () => {
  socket.emit('subscribe-user', { userID: '966110000002' }); // userId from login
});

// keep your location fresh (drivers AND riders) — V1 events, unchanged:
socket.emit('update-captain-location', { userID, lat, lon });   // driver
socket.emit('update-customer-location', { userID, lat, lon });  // rider
```

- On `subscribe-user`, a **driver** is auto-joined to the broadcast room `drivers`, so it starts receiving `new-trip-request` / `open-trips-update`.
- The socket `heartbeat` (built-in ping, ~25s) drives the periodic full-list refresh for drivers.
- A driver is **broadcast-visible** as soon as connected, but can only **win a bid** if its server `driver-infos-<userId>` state is online+approved (mode on, WASL, cabId, iban) — same as V1 going-online. If not, bidding returns `DRIVER_INELIGIBLE` (see §5.3).

---

## 5. DRIVER app

### 5.1 Receiving open trips — three inbound events

| Event | When | Merge rule |
|---|---|---|
| `new-trip-request` | a new trip is created | **append** the single `data.trip` |
| `open-trips-list` | every heartbeat / after `v2/sync-open-trips` | **replace** the whole list with `data.trips` |
| `open-trips-update` | a trip enters/leaves/changes the open set | apply the **delta** (`removed`/`added`/`updated`) by `tripId` |

**`new-trip-request` (server → driver)** — full sample:
```jsonc
{
  "v": 2, "event": "new-trip-request", "emittedAt": 1718409600000,
  "data": {
    "trip": {
      "tripId": "9c1e7b2a-...-uuid",
      "cabId": "db8db63e-1501-44c2-ac1b-c4bc213e92dc",
      "dispatchMode": "BID",
      "pickup":  { "latitude": 24.7136, "longitude": 46.6753 },
      "dropoff": { "latitude": 24.6877, "longitude": 46.7219 },
      "tripDistanceKm": 8.4,
      "pickupDistanceKm": null,        // null on broadcast — compute from YOUR gps + pickup
      "etaToPickupSec": null,          // null on broadcast — compute locally
      "fareRange": { "currency": "SAR", "recommended": 61.26, "min": 49.01, "max": 122.52, "riderOfferedFare": 60 },
      "riderOfferedFare": 60,
      "requestExpiresAt": "2026-06-15T09:34:11.000Z",
      "createdAt": "2026-06-15T09:32:11.000Z"
    }
  }
}
```

**`open-trips-list` (server → driver)** — snapshot, full sample. Here distance/ETA **are** server-filled (your location is known from the heartbeat/sync):
```jsonc
{
  "v": 2, "event": "open-trips-list", "emittedAt": 1718409605000,
  "data": {
    "mode": "snapshot",
    "driverId": "966220000022",
    "count": 1,
    "serverTime": "2026-06-15T19:34:35.609Z",
    "trips": [
      {
        "tripId": "1bf9ea24-...-uuid",
        "cabId": "db8db63e-1501-44c2-ac1b-c4bc213e92dc",
        "pickup":  { "latitude": 31.4595, "longitude": 74.2765 },
        "dropoff": { "latitude": 31.5200, "longitude": 74.3500 },
        "tripDistanceKm": 12.51,
        "pickupDistanceKm": 0.02,
        "etaToPickupSec": 2,
        "fareRange": { "currency": "SAR", "recommended": 59.16, "min": 47.33, "max": 118.32, "riderOfferedFare": 60 },
        "riderOfferedFare": 60,
        "requestExpiresAt": "2026-06-15T19:36:03.000Z",
        "createdAt": "2026-06-15T19:34:03.421Z"
      }
    ]
  }
}
```

**`open-trips-update` (server → driver)** — delta sample:
```jsonc
{
  "v": 2, "event": "open-trips-update", "emittedAt": 1718409635000,
  "data": {
    "mode": "delta",
    "removed": ["1bf9ea24-...-uuid"],                 // drop these tripIds from the list
    "added":   [ /* full trip item(s), same shape as above */ ],
    "updated": [ { "tripId": "d3b5f7a9-...", "fareRange": { "currency":"SAR","recommended":59.16,"min":47.33,"max":118.32,"riderOfferedFare":75 } } ],
    "reason": "accepted"   // accepted | expired | closed | raise-offer
  }
}
```

### 5.2 Pull the full list on demand — `v2/sync-open-trips` (driver → server)
```jsonc
// emit
socket.emit('v2/sync-open-trips',
  { cabId: "db8db63e-...", location: { latitude: 31.4593435, longitude: 74.2763599 } },
  (ack) => { /* ack below */ }
);
// ack (success)
{ "ok": true, "data": { "trips": [ /* same items as open-trips-list */ ], "now": "2026-06-15T19:34:35.609Z" } }
```
REST fallback: `GET /v2/trips/open-in-range?cabId=db8db63e-...&lat=31.4593&long=74.2763` (header `sessionid`).

### 5.3 Place a bid — `v2/place-bid` (driver → server)
```jsonc
// ACCEPT the rider's fare as-is:
socket.emit('v2/place-bid', { tripId: "9c1e7b2a-...", bidType: 1, cabId: "db8db63e-..." }, ack);

// COUNTER with your own price (must be within [minFare, maxFare]):
socket.emit('v2/place-bid', {
  tripId: "9c1e7b2a-...", bidType: 2, bidFare: 55,
  etaToPickupSec: 300, message: "5 min away", cabId: "db8db63e-..."
}, ack);

// ack (success)
{ "ok": true, "data": {
    "bidId": "96dfa402-...-uuid", "tripId": "9c1e7b2a-...", "driverId": "966220000022",
    "bidType": 2, "bidFare": 55, "currency": "SAR", "status": 1,
    "etaToPickupSec": 300, "message": "5 min away",
    "expiredAt": "2026-06-15T19:32:37.000Z", "createdAt": "2026-06-15T19:31:52.000Z"
} }

// ack (failure)
{ "ok": false, "error": { "code": "BID_BELOW_FLOOR", "details": { "minFare": 49.01, "submitted": 30 } } }
{ "ok": false, "error": { "code": "DRIVER_INELIGIBLE", "failedConditions": ["driverModeSwitch","isWASLApproved"] } }
```
REST fallback: `POST /v2/trips/:tripId/bids` with body `{ bidType, bidFare?, etaToPickupSec?, message?, cabId? }`.

> A driver can hold **one active bid per trip**; re-emitting updates it. A bid auto-expires after `SETTING_BID_TTL_SECONDS` (45s) → you'll get `v2/bid-expired` and may re-bid.

### 5.4 Withdraw a bid — `v2/withdraw-bid` (driver → server)
```jsonc
socket.emit('v2/withdraw-bid', { tripId: "9c1e7b2a-...", bidId: "96dfa402-..." }, ack);
// ack: { "ok": true, "data": { "bidId": "96dfa402-...", "status": 4 } }   // 4 = WITHDRAWN
```
REST: `DELETE /v2/trips/:tripId/bids/:bidId`.

### 5.5 Outcome events the driver receives
| Event | `data` sample | Meaning / action |
|---|---|---|
| `v2/bid-ack` | `{ driverId, bid:{...} }` | server stored your bid (mirror of the ack) |
| `v2/bid-won` | `{ tripId, bidId, agreedFare:55, currency:"SAR" }` | **you won** → proceed to pickup; switch to V1 `trip-detail`/OTP flow |
| `v2/bid-lost` | `{ tripId, bidId, reason:"ANOTHER_DRIVER_SELECTED" }` | rider chose someone else / you lost the driver race |
| `v2/bid-rejected` | `{ tripId, bidId, reason:"RIDER_DISMISSED" }` | rider dismissed your bid |
| `v2/bid-expired` | `{ tripId, bidId }` | your 45s bid TTL elapsed (you may re-bid) |
| `v2/trip-closed` | `{ tripId, reason:"RIDER_CANCELLED", yourBidId }` | trip ended before match (`RIDER_CANCELLED`/`EXPIRED`/`NO_DRIVER`) |

`reason` for `v2/bid-lost` ∈ `ANOTHER_DRIVER_SELECTED | LOST_DRIVER_RACE | DRIVER_INELIGIBLE`.

---

## 6. RIDER app

### 6.1 Create a BID trip — `POST /v2/trips`
```jsonc
// headers: { "sessionid": "<rider token>", "Content-Type": "application/json" }
// request body (your normal create-trip body PLUS riderOfferedFare):
{
  "cabId": "db8db63e-1501-44c2-ac1b-c4bc213e92dc",
  "paymentMethod": 2,
  "tripType": 1,
  "riderOfferedFare": 60,
  "addresses": [
    { "addressType": 1, "latitude": 31.4595, "longitude": 74.2765, "address": "Pickup, Lahore" },
    { "addressType": 2, "latitude": 31.5200, "longitude": 74.3500, "address": "Dropoff, Lahore" }
  ]
}
// response (201)
{ "statusCode": 201, "data": {
    "id": "1bf9ea24-...-uuid", "message": "Trip added successfully",
    "status": 15, "riderOfferedFare": 60,
    "recommendedFare": 59.16, "minFare": 47.33, "maxFare": 118.32, "currency": "SAR",
    "tripRequestTimeLimit": "2026-06-15 19:36:03"
} }
// error: offered fare outside the band
{ "statusCode": 400, "message": "Offered fare 10 is below the minimum 47.33 SAR" }
```
Show the rider `recommendedFare` / `minFare` / `maxFare` so they pick a sensible offer. The offer must be `min ≤ riderOfferedFare ≤ max`.

### 6.2 Watch the competing bids — `v2/trip-bids-update` (server → rider)
```jsonc
{
  "v": 2, "event": "v2/trip-bids-update", "emittedAt": 1718409612000,
  "data": {
    "tripId": "1bf9ea24-...", "tripStatus": 15,
    "riderOfferedFare": 60, "currency": "SAR",
    "changeType": "BID_ADDED",            // BID_ADDED | BID_UPDATED | BID_REMOVED
    "changedBidId": "96dfa402-...",
    "count": 2,
    "bids": [                              // sorted cheapest-first; REPLACE your list
      { "bidId": "b-1", "driverId": "966220000022", "bidType": 1, "bidFare": 60, "currency": "SAR", "status": 1, "etaToPickupSec": 240, "pickupDistanceKm": 1.6, "message": null },
      { "bidId": "b-2", "driverId": "966220000031", "bidType": 2, "bidFare": 70, "currency": "SAR", "status": 1, "etaToPickupSec": 180, "pickupDistanceKm": 0.9, "message": "On my way" }
    ]
  }
}
```
Other rider events: `v2/no-bids` `{ tripId, ... }` (early nudge to raise the offer) and `v2/bidding-timeout` `{ tripId, tripStatus:10 }` (window elapsed with no selection).

REST fallback: `GET /v2/trips/:tripId/bids`
```jsonc
{ "ok": true, "data": { "tripId":"1bf9ea24-...", "tripStatus":15, "riderOfferedFare":60, "currency":"SAR", "bids":[ /* as above */ ] } }
```

### 6.3 Select / reject a bid, raise offer, cancel
```jsonc
// SELECT a driver (commit the match):
socket.emit('v2/select-bid', { tripId: "1bf9ea24-...", bidId: "b-1" }, ack);
// ack: { "ok": true, "data": { "tripId":"1bf9ea24-...", "bidId":"b-1", "driverId":"966220000022", "agreedFare":60, "tripStatus":2 } }
// ack failure: { "ok": false, "error": { "code": "DRIVER_RACE_LOST" } }
//             { "ok": false, "error": { "code": "PAYMENT_HOLD_FAILED" } }

// REJECT one bid (keep collecting others):
socket.emit('v2/reject-bid', { tripId: "1bf9ea24-...", bidId: "b-2" }, ack);

// RAISE your offer to attract more/faster bids:
socket.emit('v2/...', ...); // REST: PATCH /v2/trips/:tripId/raise-offer  body { "newOfferedFare": 75 }

// CANCEL the open request:
socket.emit('v2/cancel-open-trip', { tripId: "1bf9ea24-...", declinedReason: "changed my mind" }, ack);
// ack: { "ok": true, "data": { "tripId":"1bf9ea24-...", "tripStatus":6 } }
```
REST equivalents: `PATCH /v2/trips/:tripId/bids/:bidId/accept`, `.../reject`, `PATCH /v2/trips/:tripId/raise-offer`, `PATCH /v2/trips/:tripId/cancel`.

### 6.4 Match confirmed — `v2/bid-accepted` (server → rider)
```jsonc
{
  "v": 2, "event": "v2/bid-accepted", "emittedAt": 1718409640000,
  "data": {
    "tripId": "1bf9ea24-...", "bidId": "b-1", "driverId": "966220000022",
    "agreedFare": 60, "currency": "SAR"
  }
}
```
An alias `driver-selected` carries the same data for clients that prefer it. After this, the trip is `ACCEPTED_BY_DRIVER (2)` and the rider's **total** is `agreedFare + tax + fees` (e.g. agreed 65 → `riderAmount 81.90`, `taxAmount 13.65`, `transactionFee 3.25`). Switch to your existing V1 assigned-trip screens (driver location, OTP, completion).

---

## 7. REST API reference (`/v2/trips`) — quick table

All require header `sessionid: <token>`. `:tripId` / `:bidId` are path params.

| # | Method | Path | Actor | Body | Returns |
|---|---|---|---|---|---|
| 1 | POST | `/v2/trips` | Rider | create body + `riderOfferedFare` | `{ id, status:15, recommendedFare, minFare, maxFare }` |
| 2 | GET | `/v2/trips/open-in-range?cabId=&lat=&long=` | Driver | — | `{ ok, data:{ trips:[...] } }` |
| 3 | POST | `/v2/trips/:tripId/bids` | Driver | `{ bidType, bidFare?, etaToPickupSec?, message?, cabId? }` | `{ ok, data:{ bidId, status:1, ... } }` |
| 4 | DELETE | `/v2/trips/:tripId/bids/:bidId` | Driver | — | `{ ok, data:{ bidId, status:4 } }` |
| 5 | GET | `/v2/trips/:tripId/bids` | Rider | — | `{ ok, data:{ bids:[...] } }` |
| 6 | PATCH | `/v2/trips/:tripId/bids/:bidId/accept` | Rider | `{ sessionId? }` | `{ ok, data:{ driverId, agreedFare, tripStatus:2 } }` |
| 7 | PATCH | `/v2/trips/:tripId/bids/:bidId/reject` | Rider | — | `{ ok, data:{ bidId, status:3 } }` |
| 8 | PATCH | `/v2/trips/:tripId/raise-offer` | Rider | `{ newOfferedFare }` | `{ ok, data:{ riderOfferedFare } }` |
| 9 | PATCH | `/v2/trips/:tripId/cancel` | Rider | `{ declinedReason? }` | `{ ok, data:{ tripStatus:6 } }` |

The socket actions in §5–§6 map 1:1 to these patterns, so you can use either transport (socket recommended for real-time).

---

## 8. Error code catalog (action acks & REST `{ok:false}`)

| code | applies to | meaning |
|---|---|---|
| `DRIVER_INELIGIBLE` | place-bid, sync | driver fails the online/approval gate (carries `failedConditions[]`) |
| `DRIVER_OUT_OF_RANGE` | place-bid | pickup beyond the search radius |
| `CAB_TYPE_MISMATCH` | place-bid | driver's cab ≠ trip's cab |
| `BID_BELOW_FLOOR` / `BID_ABOVE_CEILING` | place-bid | bidFare outside `[minFare, maxFare]` (carries `details`) |
| `TRIP_NOT_OPEN` | place-bid, select | trip is no longer accepting bids/selection |
| `TRIP_NOT_FOUND` | all | unknown/purged trip |
| `BID_NOT_FOUND` | withdraw/select/reject | unknown bid |
| `BID_NOT_OWNED` | withdraw | bid isn't this driver's |
| `BID_NO_LONGER_ACTIVE` | select | bid expired/withdrawn before selection |
| `BID_ALREADY_TERMINAL` | place/withdraw | bid already accepted/lost (idempotent) |
| `DRIVER_RACE_LOST` | select | that driver got bound by another rider first |
| `NOT_TRIP_OWNER` | rider actions | rider doesn't own the trip |
| `PAYMENT_HOLD_FAILED` | select | could not hold the agreed fare (e.g. insufficient wallet) |
| `TRIP_ALREADY_ASSIGNED` / `TRIP_ALREADY_TERMINAL` | cancel | too late to cancel |
| `VALIDATION_ERROR` | all | malformed payload |
| `INTERNAL_ERROR` | all | unexpected failure |

---

## 9. Full worked example (real values from the running system)

```
# 1) Rider login
POST /sendotp   { "mobileNo":"966533670676" }                       -> { data:{ tId:"2f4cafb8..." } }
POST /verifyotp { "mobileNo":"966533670676","otp":"676","tId":"2f4cafb8..." } -> token (use as sessionid)

# 2) Rider creates a BID trip (offer 30 was rejected as < min; 60 accepted)
POST /v2/trips  (sessionid: rider)  { cabId, paymentMethod:2, riderOfferedFare:60, addresses:[pickup,dropoff] }
   -> 201 { id:"1bf9ea24...", status:15, recommendedFare:59.16, minFare:47.33, maxFare:118.32 }

# 3) Driver (966220000022) connects socket, syncs, and bids
socket.emit('subscribe-user', { userID:"966220000022" })
socket.emit('v2/sync-open-trips', { cabId, location }, ack)
   -> ack { ok:true, data:{ trips:[ { tripId:"1bf9ea24...", pickupDistanceKm:0.02, etaToPickupSec:2, fareRange:{min:47.33,recommended:59.16,max:118.32} } ] } }
POST /v2/trips/1bf9ea24.../bids  (sessionid: driver) { bidType:2, bidFare:55 }
   -> { ok:true, data:{ bidId:"96dfa402...", status:1, expiredAt:"...+45s" } }

# 4) Rider sees + selects the bid
GET   /v2/trips/1bf9ea24.../bids                    -> { ok:true, data:{ bids:[ {bidId:"96dfa402...", bidFare:55} ] } }
PATCH /v2/trips/1bf9ea24.../bids/96dfa402.../accept -> { ok:true, data:{ driverId:"966220000022", agreedFare:55, tripStatus:2 } }

# 5) Result in DB / events
trip: status=2 (ACCEPTED_BY_DRIVER), driverId=966220000022, tripBaseAmount=55, riderAmount=55+tax, tripOtp set, open_rides removed
driver socket: v2/bid-won { agreedFare:55 }   |   rider socket: v2/bid-accepted { driverId, agreedFare:55 }
# -> continue on the existing V1 trip lifecycle
```

---

## 10. Timing / config (server-tunable via Redis `SETTING_*`)
- `SETTING_BROADCAST_DISPATCH` = 1 enables V2 broadcast/list.
- `SETTING_BIDDING_ENABLED` = 1 allows BID-mode trip creation.
- `SETTING_BID_TTL_SECONDS` (45) — a bid auto-expires after this.
- `SETTING_TRIP_REQUEST_TIME` (minutes) — how long a trip stays open for bids.
- `SETTING_BID_MIN_FACTOR` (0.80) / `SETTING_BID_MAX_FACTOR` (2.00) — fare-range bounds around the recommended meter fare (`min = recommended×0.80`, `max = recommended×2.00`).
- `SETTING_TRIP_DRIVER_SEARCH_RADIUS` (km) — broadcast/list range.
- `SETTING_BROADCAST_MAX_OPEN_TRIPS` (30) — max list size per pull.

---

## 11. Integration checklist & gotchas
- [ ] REST auth header is **`sessionid`** (the verifyotp `token`), not `Authorization`.
- [ ] Socket identity is set with **`subscribe-user { userID }`** — do it on every (re)connect.
- [ ] Driver list = `open-trips-list` (replace) + `new-trip-request` (append) + `open-trips-update` (delta); de-dupe by `tripId`.
- [ ] On `new-trip-request`, compute `pickupDistanceKm`/`etaToPickupSec` locally (server sends null there).
- [ ] Validate the rider's offer client-side against `minFare`/`maxFare` before `POST /v2/trips`.
- [ ] Bids expire in 45s — show a countdown; handle `v2/bid-expired` and allow re-bid.
- [ ] Handle `DRIVER_INELIGIBLE.failedConditions` to tell the driver exactly what to fix (go online, WASL, etc.).
- [ ] After `v2/bid-won` / `v2/bid-accepted`, switch BOTH apps to the existing V1 assigned-trip flow.
- [ ] Respect rate limits: `sendotp` ~15s/number, trip-create ~30s/rider.
- [ ] Treat snapshots (`open-trips-list`, `v2/trip-bids-update`) as authoritative; deltas as incremental.
