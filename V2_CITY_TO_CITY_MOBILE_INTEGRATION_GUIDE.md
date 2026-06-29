# V2 City-to-City Mobile Integration Guide — Scheduled Intercity + inDrive-Style Bidding

> **Audience:** mobile (driver + rider) developers building the **City-to-City** product.
> **Status:** matches the implemented + E2E-tested code on branch `ride-demo-v2` (Flow C verified live, 17/17 create→match + 5/5 activation-cron assertions).
> **Transport:** WebSocket (Socket.IO) for real-time + REST (api-gateway `/v2/schedule` and `/v2/trips`) for every action.
> **Base URLs (demo):** REST `http://<host>:3010`  •  WebSocket `http://<host>:8100`
> **Auth:** every REST call sends the login JWT in the **`sessionid`** header (NOT `Authorization`). Sockets identify the user with `subscribe-user`.
>
> **↑ Entry point:** start at `docs/V2_MOBILE_MASTER_GUIDE.md` (all three modes + the shared house response/error contract). **Read the immediate-bidding guide first:** `docs/V2_MOBILE_INTEGRATION_GUIDE.md`. City-to-City **reuses that guide's entire bidding engine verbatim** — the bid socket actions (`v2/place-bid`, `v2/withdraw-bid`, `v2/select-bid`, `v2/reject-bid`), the bid list (`v2/trip-bids-update`), the outcome events (`v2/bid-won`, `v2/bid-lost`, `v2/bid-accepted`), the PII blocks, and the post-match V1 lifecycle (driver-reached → OTP start → complete → pay). This document documents only what is **C2C-specific** and references the immediate guide for everything shared.

All sample values below are **real shapes** from the running system (seeded `c2c_cities` / `intercity_routes` / `cab_type` rows, and captured payloads from `docs/V2_E2E_TEST_REPORT.md`). You can copy them as fixtures.

---

## 0. TL;DR — the whole City-to-City flow in 10 steps

1. Rider lists cities (**`GET /v2/schedule/cities`**) and gets a fare band (**`GET /v2/schedule/quote`**) for an origin city + destination city + cab + seats.
2. Rider **`POST /v2/schedule/city-to-city`** with origin/destination cities, pickup + dropoff points, a **future `scheduledDepartureAt`**, **seats**, and a **proposed fare** → trip opens for bids **immediately** (`status 15 AWAITING_BIDS`, `scheduledState 1 SCHEDULED_OPEN`).
3. Server **broadcasts `new-trip-request`** to connected drivers; route-traveling drivers also pull it via **`GET /v2/schedule/open-in-route`**.
4. Each in-range driver bids (**`v2/place-bid`**) — accept the rider's fare or counter. **C2C bids have a LONG TTL (86400s / 1 day, not 45s)** so a bid placed days before departure stays alive.
5. Rider receives a **live list of bids** (**`v2/trip-bids-update`**), sorted cheapest-first.
6. Rider **picks one** (**`v2/select-bid`**) → trip becomes `DRIVER_SELECTED (16)` / `scheduledState 2 SCHEDULED_MATCHED`. **Payment is held now** (could be days before departure).
7. Winner gets **`v2/bid-won`**, losers get **`v2/bid-lost`**, rider gets **`v2/bid-accepted`** — identical to immediate bidding.
8. The trip then **waits**. Both apps can poll **`GET /v2/schedule/my-upcoming`**.
9. **~30 min before departure** the scheduler's reminder cron flips the trip to `scheduledState 3 REMINDED` and notifies rider + driver.
10. **~15 min before departure** the activation cron re-validates the rider, re-holds stale payment, generates the trip OTP, flips the trip to `ACCEPTED_BY_DRIVER (2)` / `scheduledState 4 ACTIVATED`, and **hands off to the normal V1 assigned-trip lifecycle** (driver-reached → OTP start → complete → pay). Reuse your existing V1 code from here.

---

## 1. What's different from immediate bidding

City-to-City is `(tripType = SCHEDULED 2, dispatchMode = BID 2)`. Immediate bidding is `(tripType = IMMEDIATELY 1, dispatchMode = BID 2)`. The bidding mechanics are the same; the scheduling wrapper is new.

| Area | Immediate bidding (V2 guide) | City-to-City (this guide) |
|---|---|---|
| Origin / destination | free pickup/dropoff coords only | **origin city + destination city** (`originCityId` / `destinationCityId`) PLUS pickup/dropoff points |
| Trip discriminator | `tripType=IMMEDIATELY(1)`, `dispatchMode=BID(2)` | `tripType=SCHEDULED(2)`, `dispatchMode=BID(2)` |
| Departure | now | **future `scheduledDepartureAt`** (stored as `riderScheduledAt`), must satisfy lead-time window |
| Seats | n/a | **`seatsRequested`** (1…`SETTING_C2C_MAX_SEATS`) — feeds the fare via `perSeatFare` |
| Cab | any point-to-point cab | a **"City to City" category** cab (`C2C Sedan` / `C2C SUV`, `categoryType=3`) |
| Fare basis | per-km/min meter (`cab_charges`) | **per-route `intercity_routes`** table (`baseFare + perSeatFare·seats + costPerKm·routeDistanceKm`) with centroid-haversine fallback |
| Bid TTL | `SETTING_BID_TTL_SECONDS` = **45s** | `SETTING_C2C_BID_TTL_SECONDS` = **86400s (1 day)** |
| Open-trip lifetime | `SETTING_TRIP_REQUEST_TIME` ~2 min | until **`biddingClosesAt`** (= departure − `SETTING_C2C_BIDDING_CLOSE_BEFORE_MIN`); the 2-min reaper is excluded for scheduled rows |
| Bidding close guard | trip-request expiry | hard **`BIDDING_CLOSED`** once `now > biddingClosesAt` |
| Sub-state machine | none (just `TripStatus`) | **`scheduledState`** column (`SCHEDULED_OPEN → SCHEDULED_MATCHED → REMINDED → ACTIVATED`, or `…_EXPIRED` / `…_CANCELLED`) |
| Payment | held at selection, trip runs immediately | **held at selection** (possibly days early), **re-validated / re-held at activation** |
| Activation | match → live trip immediately | match → **waits** → reminder cron → **activation cron** near departure → V1 lifecycle |
| Crons | bid-expiry sweep only | bid-expiry (C2C TTL) **+ reminder cron (every 5 min) + activation cron (every minute)** |
| REST surface | `/v2/trips/*` | new **`/v2/schedule/*`** for create/quote/cities/discovery/upcoming/cancel; bidding still on `/v2/trips/:tripId/bids*` |

Everything after `v2/bid-accepted` / `v2/bid-won` (once activated) is the **same V1 lifecycle** as immediate bidding — reuse your existing assigned-trip code.

---

## 2. Enums & the scheduled sub-state machine

C2C reuses all immediate-bidding enums (`bidType`, `BidStatus`, `dispatchMode`, `paymentMethod`, `addressType`, the `{ v, event, emittedAt, data }` socket server→client **event** envelope, and the house `{ statusCode, data }` / `{ statusCode, message, data:{ code, … } }` REST + socket-ack shape) — see the immediate guide §2. C2C adds one new enum and reuses `TripType`.

```
TripType:          1 = IMMEDIATELY, 2 = SCHEDULED   (C2C is SCHEDULED)
dispatchMode:      1 = AUTO, 2 = BID                (C2C is BID)

ScheduledTripState (NEW — C2C sub-state, orthogonal to TripStatus):
  1 = SCHEDULED_OPEN       future trip created, open for bids
  2 = SCHEDULED_MATCHED    rider selected a bid; payment held; driver committed
  3 = REMINDED             pre-departure reminder sent to rider + driver
  4 = ACTIVATED            handed to the V1 assigned-trip lifecycle near departure
  5 = SCHEDULED_EXPIRED    window passed with no match, OR activation guard failed
  6 = SCHEDULED_CANCELLED  rider cancelled before activation

reused bid enums (see immediate guide §2):
  bidType:   1 = ACCEPT_FARE, 2 = COUNTER
  BidStatus: 1 = PENDING, 2 = ACCEPTED, 3 = REJECTED, 4 = WITHDRAWN, 5 = EXPIRED, 6 = CANCELLED, 7 = LOST_RACE
```

### 2.1 How `scheduledState` maps onto `TripStatus`

`scheduledState` is a **parallel, nullable** column set only on C2C rows. The coarse `TripStatus` still drives the rest of the system; `scheduledState` drives the scheduler. There are **no new `TripStatus` integers** — C2C maps onto existing ones:

| `scheduledState` | paired `TripStatus` | What it means to the apps |
|---|---|---|
| `SCHEDULED_OPEN (1)` | `AWAITING_BIDS (15)` | open for bids; rider watches bids, driver may bid |
| `SCHEDULED_MATCHED (2)` | `DRIVER_SELECTED (16)` | a bid was selected, payment held, trip waiting for departure |
| `REMINDED (3)` | `DRIVER_SELECTED (16)` | reminder sent (~30 min out); still waiting |
| `ACTIVATED (4)` | `ACCEPTED_BY_DRIVER (2)` | trip is now live — switch to the V1 assigned-trip flow (OTP set) |
| `SCHEDULED_EXPIRED (5)` | `EXPIRED (10)` (had bids) or `NO_DRIVER (9)` (no bids) | bidding window closed with no match, or activation guard failed |
| `SCHEDULED_CANCELLED (6)` | `CANCELLED_BY_RIDER (6)` | rider cancelled before activation |

> **Render rule:** for a C2C trip, branch your UI on `scheduledState` first (it tells you the scheduling phase), and use `TripStatus` for the live-trip phase once `ACTIVATED`.

---

## 3. Authentication & socket presence

Identical to the immediate guide §3–§4. In short:
- `POST /sendotp` → `POST /verifyotp` → use the returned `token` as the **`sessionid`** header on every REST call.
- Socket: `socket.emit('subscribe-user', { userID })` on every (re)connect; the server resolves driver/rider from this binding, not from any id in a payload.
- A driver must be online + approved (mode on, WASL, cabId, iban) to **win** a bid; the eligibility gate is checked at bid time and returns `DRIVER_INELIGIBLE` otherwise — same as immediate bidding.
- **C2C-specific eligibility:** the driver's cab must be a **"City to City" category** cab type (see §4.1). A non-C2C cab bidding on a C2C trip is rejected with `CAB_TYPE_MISMATCH`.

---

## 4. Seeded reference data (real, from the running DB)

### 4.1 "City to City" category & cab types (`cab_type_category`, `cab_type`)

```
category: "City to City"   id = 5c3edu1e-0000-4000-8000-00000000c2c0   categoryType = 3 (SCHEDULE)   order = 5

cab types under it:
  C2C Sedan  id = c2c5eda0-0000-4000-8000-000000000001   seats 4   nameAr "سيدان بين المدن"
  C2C SUV    id = c2c5eda0-0000-4000-8000-000000000002   seats 6   nameAr "دفع رباعي بين المدن"
```
The `cabId` on a C2C trip must be one of these. (For reference, all categories: Derrbi Taxi=1 DEFAULT, Delivery Service=2 COURIER, **City to City=3 SCHEDULE**, Car Rental=4, Cargo Service=5.)

### 4.2 Cities (`c2c_cities`) — 6 seeded, with centroids

| id | name | nameAr | centroidLat | centroidLng |
|---|---|---|---|---|
| `c17a0000-0000-4000-8000-000000000001` | Riyadh | الرياض | 24.7136 | 46.6753 |
| `c17a0000-0000-4000-8000-000000000002` | Jeddah | جدة | 21.4858 | 39.1925 |
| `c17a0000-0000-4000-8000-000000000003` | Dammam | الدمام | 26.4207 | 50.0888 |
| `c17a0000-0000-4000-8000-000000000004` | Mecca | مكة المكرمة | 21.3891 | 39.8579 |
| `c17a0000-0000-4000-8000-000000000005` | Medina | المدينة المنورة | 24.5247 | 39.5692 |
| `c17a0000-0000-4000-8000-000000000006` | Abha | أبها | 18.2164 | 42.5053 |

### 4.3 Priced routes (`intercity_routes`) — 6 seeded directed rows

`recommendedFare = baseFare + perSeatFare·seats + costPerKm·routeDistanceKm`. `cabTypeId = NULL` means the row applies to **all** C2C cab types. All currency `SAR`, `status = 1`.

| id | route | baseFare | perSeatFare | costPerKm | routeDistanceKm |
|---|---|---|---|---|---|
| `e0a7e000-0000-4000-8000-000000000001` | Riyadh → Jeddah | 80 | 20 | 0.45 | 870 |
| `e0a7e000-0000-4000-8000-000000000002` | Jeddah → Riyadh | 80 | 20 | 0.45 | 870 |
| `e0a7e000-0000-4000-8000-000000000003` | Riyadh → Dammam | 60 | 15 | 0.45 | 400 |
| `e0a7e000-0000-4000-8000-000000000004` | Dammam → Riyadh | 60 | 15 | 0.45 | 400 |
| `e0a7e000-0000-4000-8000-000000000005` | Jeddah → Mecca | 30 | 10 | 0.50 | 80 |
| `e0a7e000-0000-4000-8000-000000000006` | Mecca → Jeddah | 30 | 10 | 0.50 | 80 |

> **Routes are directed** and the seeded set is partial — only the 3 pairs above (both directions) are priced. A city pair with no row falls back to the centroid-distance formula (§5.2); if even that can't compute, you get `INTERCITY_ROUTE_NOT_PRICED`.

### 4.4 Config keys (`SETTING_C2C_*`, Redis — live values)

| key | live value | meaning |
|---|---|---|
| `SETTING_C2C_ENABLED` | `1` | master switch for C2C create; `0` → `C2C_DISABLED` |
| `SETTING_C2C_MIN_LEAD_MINUTES` | `60` | departure must be ≥ now + 60 min, else `DEPARTURE_TOO_SOON` |
| `SETTING_C2C_MAX_LEAD_DAYS` | `30` | departure must be ≤ now + 30 days, else `DEPARTURE_TOO_FAR` |
| `SETTING_C2C_MAX_SEATS` | `6` | seats must be 1…6, else `INVALID_SEATS` |
| `SETTING_C2C_BIDDING_CLOSE_BEFORE_MIN` | `60` | `biddingClosesAt = departure − 60 min` |
| `SETTING_C2C_REMIND_BEFORE_MIN` | `30` | reminder cron fires ~30 min before departure |
| `SETTING_C2C_ACTIVATE_BEFORE_MIN` | `15` | activation cron fires ~15 min before departure |
| `SETTING_C2C_FREE_CANCEL_BEFORE_MIN` | `60` | free cancel up to 60 min before departure; inside it a fee applies |
| `SETTING_C2C_BID_TTL_SECONDS` | `86400` | a C2C bid stays alive for 1 day (vs 45s immediate) |
| `SETTING_C2C_BID_MIN_FACTOR` | `0.8` | `minFare = recommended × 0.80` |
| `SETTING_C2C_BID_MAX_FACTOR` | `2.0` | `maxFare = recommended × 2.00` |

---

## 5. Cities & quote (rider, before creating)

### 5.1 `GET /v2/schedule/cities` — list selectable cities

```jsonc
// GET /v2/schedule/cities    headers: { sessionid }
// response (house envelope; alphabetical by name; status=true only)
{
  "statusCode": 200,
  "data": [
    { "id": "c17a0000-0000-4000-8000-000000000006", "name": "Abha",   "nameAr": "أبها",            "centroidLat": 18.2164, "centroidLng": 42.5053, "countryId": null },
    { "id": "c17a0000-0000-4000-8000-000000000003", "name": "Dammam", "nameAr": "الدمام",          "centroidLat": 26.4207, "centroidLng": 50.0888, "countryId": null },
    { "id": "c17a0000-0000-4000-8000-000000000002", "name": "Jeddah", "nameAr": "جدة",             "centroidLat": 21.4858, "centroidLng": 39.1925, "countryId": null },
    { "id": "c17a0000-0000-4000-8000-000000000004", "name": "Mecca",  "nameAr": "مكة المكرمة",      "centroidLat": 21.3891, "centroidLng": 39.8579, "countryId": null },
    { "id": "c17a0000-0000-4000-8000-000000000005", "name": "Medina", "nameAr": "المدينة المنورة",  "centroidLat": 24.5247, "centroidLng": 39.5692, "countryId": null },
    { "id": "c17a0000-0000-4000-8000-000000000001", "name": "Riyadh", "nameAr": "الرياض",          "centroidLat": 24.7136, "centroidLng": 46.6753, "countryId": null }
  ]
}
```
Use this to populate both the origin and destination pickers. Both rider and driver apps may call it.

### 5.2 `GET /v2/schedule/quote` — fare band for a pair + cab + seats

Query params: `originCityId`, `destinationCityId`, `cabId` (C2C cab), `seats` (default 1).

```
GET /v2/schedule/quote?originCityId=c17a0000-...-000000000001
                      &destinationCityId=c17a0000-...-000000000002
                      &cabId=c2c5eda0-0000-4000-8000-000000000001
                      &seats=2
headers: { sessionid }
```
```jsonc
// response — Riyadh→Jeddah, 2 seats (REAL captured values; priced from intercity_routes)
{
  "statusCode": 200,
  "data": {
    "originCityId": "c17a0000-0000-4000-8000-000000000001",
    "destinationCityId": "c17a0000-0000-4000-8000-000000000002",
    "seats": 2,
    "routeDistanceKm": 870,
    "recommendedFare": 511.5,   // 80 + 20*2 + 0.45*870
    "minFare": 409.2,           // 511.5 * 0.80
    "maxFare": 1023,            // 511.5 * 2.00
    "currency": "SAR",
    "pricedBy": "route"         // "route" = matched an intercity_routes row; "formula" = centroid fallback
  }
}
```

**Pricing logic & the route/centroid fallback:**
1. Route-table lookup, preferring a `cabTypeId = cabId` row, else the `cabTypeId = NULL` (all-cab-types) row → `pricedBy: "route"`. `routeDistanceKm` is the table's cached value (e.g. 870) or, if null, the centroid haversine.
2. **Fallback** (no route row matches the pair): `routeDistanceKm = haversine(originCentroid, destCentroid)`, `recommended = costPerKm × routeDistanceKm` using the cab's intra-city `cab_charges.costPerKm` (default coefficient 1.5 if none) → `pricedBy: "formula"`.
3. If even the fallback yields ≤ 0 (e.g. missing centroids), returns `INTERCITY_ROUTE_NOT_PRICED`.

`min = recommended × SETTING_C2C_BID_MIN_FACTOR (0.80)`, `max = recommended × SETTING_C2C_BID_MAX_FACTOR (2.00)`.

**Negatives** (real HTTP 4xx; `data.code` carries the machine code, top-level `message` the human string):
```jsonc
// origin == destination — HTTP 400
{ "statusCode": 400, "message": "SAME_ORIGIN_DESTINATION", "data": { "code": "SAME_ORIGIN_DESTINATION" } }
// unknown city id, or city status=false, or a missing id — HTTP 400
{ "statusCode": 400, "message": "INVALID_CITY", "data": { "code": "INVALID_CITY" } }
```

Always show the rider `recommendedFare` / `minFare` / `maxFare` so they offer a sensible fare; the create call re-validates the offer against the band.

---

## 6. Create a City-to-City request (rider)

### 6.1 `POST /v2/schedule/city-to-city`

```jsonc
// headers: { "sessionid": "<rider token>", "Content-Type": "application/json" }
// request body
{
  "cabId": "c2c5eda0-0000-4000-8000-000000000001",   // a "City to City" cab type (C2C Sedan)
  "originCityId": "c17a0000-0000-4000-8000-000000000001",       // Riyadh
  "destinationCityId": "c17a0000-0000-4000-8000-000000000002",  // Jeddah
  "scheduledDepartureAt": "2026-06-21T22:00:00.000Z",  // future; within [now+60min, now+30d]
  "seats": 2,
  "riderOfferedFare": 511.5,         // must satisfy minFare <= offer <= maxFare (from the quote)
  "paymentMethod": 2,                // 1 = card, 2 = wallet
  "cardId": null,                    // required only for card
  "addresses": [
    { "addressType": 1, "latitude": 24.7136, "longitude": 46.6753, "address": "King Fahd Rd, Riyadh" },  // pickup point
    { "addressType": 2, "latitude": 21.4858, "longitude": 39.1925, "address": "Corniche, Jeddah" }       // dropoff point
  ]
}
```
```jsonc
// response (house envelope, HTTP 201) — REAL shape; trip opens for bids immediately
{
  "statusCode": 201,
  "data": {
    "id": "9a3c1f20-...-uuid",
    "tripStatus": 15,                 // AWAITING_BIDS
    "scheduledState": 1,              // SCHEDULED_OPEN
    "originCityId": "c17a0000-0000-4000-8000-000000000001",
    "destinationCityId": "c17a0000-0000-4000-8000-000000000002",
    "seatsRequested": 2,
    "scheduledDepartureAt": "2026-06-21T22:00:00.000Z",
    "biddingClosesAt": "2026-06-21T21:00:00.000Z",   // departure − 60 min (SETTING_C2C_BIDDING_CLOSE_BEFORE_MIN)
    "riderOfferedFare": 511.5,
    "recommendedFare": 511.5,
    "minFare": 409.2,
    "maxFare": 1023,
    "currency": "SAR"
  }
}
```

The server, on success, also inserts the pickup/dropoff into `trip_addresses`, inserts an `open_rides` row carrying the route + departure (so route discovery needs no join), and **broadcasts `new-trip-request`** to drivers (§7). The trip's `requestExpiresAt` is set to `biddingClosesAt` (NOT 2 minutes), and the standard open-trip reaper skips scheduled rows, so the request stays open for the full multi-day window.

### 6.2 Create negatives & the house error envelope shape

> **House envelope (matches the rest of `/v2`):** business/validation failures on create come back as a **real HTTP 4xx** with the house `{ statusCode:<4xx>, message:"<human string>", data:{ code, …details } }` body — the previous "HTTP 201 wrapping an inner `{statusCode:400}`" quirk is gone across ALL `/v2` create paths (bidding, courier, C2C). The machine code and any details are **flattened into `data`** (`data.code`, `data.minFare`, …); the top-level `message` is the human-readable string. Branch on `resp.data.code` (the HTTP status is reliable too — e.g. `DEPARTURE_TOO_SOON` → HTTP 400, verified live in Flow C). All C2C create codes below are **HTTP 400**.

```jsonc
{ "statusCode": 400, "message": "C2C_DISABLED",              "data": { "code": "C2C_DISABLED" } }              // SETTING_C2C_ENABLED != 1
{ "statusCode": 400, "message": "SAME_ORIGIN_DESTINATION",   "data": { "code": "SAME_ORIGIN_DESTINATION" } }   // origin == destination
{ "statusCode": 400, "message": "INVALID_CITY",              "data": { "code": "INVALID_CITY" } }              // unknown / disabled city
{ "statusCode": 400, "message": "DEPARTURE_TOO_SOON",        "data": { "code": "DEPARTURE_TOO_SOON" } }        // < now + 60 min
{ "statusCode": 400, "message": "DEPARTURE_TOO_FAR",         "data": { "code": "DEPARTURE_TOO_FAR" } }         // > now + 30 days
{ "statusCode": 400, "message": "INVALID_SEATS",            "data": { "code": "INVALID_SEATS" } }              // seats < 1 or > 6
{ "statusCode": 400, "message": "INTERCITY_ROUTE_NOT_PRICED", "data": { "code": "INTERCITY_ROUTE_NOT_PRICED" } } // no route row + no centroid fallback
{ "statusCode": 400, "message": "OFFER_OUT_OF_BAND",
    "data": { "code": "OFFER_OUT_OF_BAND", "minFare": 409.2, "submitted": 100 } }                              // offer below band (data.maxFare when above)
{ "statusCode": 400, "message": "pickup+dropoff addresses required",
    "data": { "code": "VALIDATION_ERROR" } }                                                                  // malformed body (missing cab/addresses/date)
```

> **Verified (Flow C, `V2_E2E_TEST_REPORT.md` §4.2):** create with a future +3h departure and an in-band offer returns `status=15`, `scheduledState=1`, `tripType=2`, `dispatchMode=2`, `seatsRequested=2`; a +1 min departure returns `DEPARTURE_TOO_SOON`.

---

## 7. Driver discovery of scheduled requests

A driver finds open C2C requests two ways: the **broadcast** (push) and the **route query** (pull / REST fallback).

### 7.1 Broadcast — `new-trip-request` (server → driver)

When a C2C request is created, the server broadcasts the same `new-trip-request` event used for immediate trips (§5.1 of the immediate guide). The item carries the standard open-trip fields **plus the dropoff** and the **long `requestExpiresAt`** (= `biddingClosesAt`). The `dispatchMode` is `"BID"`.

```jsonc
{
  "v": 2, "event": "new-trip-request", "emittedAt": 1718409600000,
  "data": {
    "trip": {
      "tripId": "9a3c1f20-...-uuid",
      "cabId": "c2c5eda0-0000-4000-8000-000000000001",
      "dispatchMode": "BID",
      "pickup":  { "latitude": 24.7136, "longitude": 46.6753 },   // origin pickup point
      "dropoff": { "latitude": 21.4858, "longitude": 39.1925 },   // destination dropoff point
      "tripDistanceKm": null,
      "pickupDistanceKm": null,       // null on broadcast — compute from YOUR gps + pickup
      "etaToPickupSec": null,
      "fareRange": { "currency": "SAR", "recommended": 511.5, "min": 409.2, "max": 1023, "riderOfferedFare": 511.5 },
      "riderOfferedFare": 511.5,
      "requestExpiresAt": "2026-06-21T21:00:00.000Z",   // = biddingClosesAt (NOT ~2 min)
      "createdAt": "2026-06-18T19:00:00.000Z",
      "rider": {                      // PII so the driver can decide before bidding
        "riderId": "966220000024",
        "name": "Fatima",
        "arabicName": "فاطمة",
        "profileImage": "",
        "rating": 0,
        "totalReviews": 0
      }
    }
  }
}
```
> **Route/seats/departure on the broadcast:** the broadcast item is the generic open-trip shape and a far-future `requestExpiresAt` is the strongest C2C signal on it. For the **authoritative** route, departure, and seats, drivers should treat **`GET /v2/schedule/open-in-route`** (§7.2) as the source of truth for C2C — it returns the cities, `scheduledDepartureAt`, and `seatsRequested` explicitly. Drivers self-filter the broadcast by their planned **route** and **departure window** in addition to the usual range/cab filter.

### 7.2 `GET /v2/schedule/open-in-route` — open C2C requests on a route (driver)

Optional query params: `originCityId`, `destinationCityId` (omit either to widen). Returns open (`AWAITING_BIDS`) scheduled requests ordered by departure.

```
GET /v2/schedule/open-in-route?originCityId=c17a0000-...-000000000001&destinationCityId=c17a0000-...-000000000002
headers: { sessionid }
```
```jsonc
{
  "statusCode": 200,
  "data": {
    "trips": [
      {
        "tripId": "9a3c1f20-...-uuid",
        "cabId": "c2c5eda0-0000-4000-8000-000000000001",
        "originCityId": "c17a0000-0000-4000-8000-000000000001",
        "destinationCityId": "c17a0000-0000-4000-8000-000000000002",
        "scheduledDepartureAt": "2026-06-21T22:00:00.000Z",
        "seatsRequested": 2,
        "riderOfferedFare": 511.5,
        "recommendedFare": 511.5,
        "pickup":  { "latitude": 24.7136, "longitude": 46.6753 },
        "dropoff": { "latitude": 21.4858, "longitude": 39.1925 },
        "riderId": "966220000024"
      }
    ]
  }
}
```
> **Verified (Flow C §4.3):** the driver discovered the request via **both** the broadcast `new-trip-request` and `GET /v2/schedule/open-in-route`.

---

## 8. Bid → select (reused engine, with C2C timing)

The bid lifecycle is **identical** to the immediate guide §5.3–§5.5 and §6.2–§6.4 — same `v2/place-bid` / `v2/select-bid` / `v2/trip-bids-update` / `v2/bid-won` / `v2/bid-accepted`, same PII blocks, same single-winner cascade. The **only** C2C differences are timing-related:

### 8.1 Place a bid — long TTL

```jsonc
// driver -> server (same as immediate); bidFare must be within [minFare, maxFare]
socket.emit('v2/place-bid', { tripId: "9a3c1f20-...", bidType: 2, bidFare: 480, cabId: "c2c5eda0-0000-4000-8000-000000000001" }, ack);
// REST fallback: POST /v2/trips/:tripId/bids  { bidType, bidFare?, etaToPickupSec?, message?, cabId? }

// ack (success) — note expiredAt is ~1 DAY out, not ~45s
{ "statusCode": 200, "data": {
    "bidId": "b1c2...-uuid", "tripId": "9a3c1f20-...", "driverId": "966220000015",
    "bidType": 2, "bidFare": 480, "currency": "SAR", "status": 1,
    "createdAt": "2026-06-18T19:05:00.000Z",
    "expiredAt": "2026-06-19T19:05:00.000Z"     // +86400s (SETTING_C2C_BID_TTL_SECONDS), NOT +45s
} }
```
> **Verified (Flow C §4.3):** a placed C2C bid's `expiredAt` was **86400s (1 day) in the future** — the C2C-specific long TTL. The bid-expiry cron uses the C2C TTL for scheduled bids, so bids placed days before departure do **not** vanish after 45s.

### 8.2 The `BIDDING_CLOSED` guard

Once `now > biddingClosesAt` (departure − 60 min), both `v2/place-bid` and `v2/select-bid` acks are rejected (HTTP 400 on the REST fallback):
```jsonc
{ "statusCode": 400, "message": "BIDDING_CLOSED", "data": { "code": "BIDDING_CLOSED" } }
```
The reminder cron also closes any still-unmatched open request whose `biddingClosesAt` has passed (→ `EXPIRED`/`NO_DRIVER`, `SCHEDULED_EXPIRED`, rider gets `v2/bidding-timeout`).

### 8.3 Rider watches & selects (reused)

- Rider watches `v2/trip-bids-update` (server → rider), cheapest-first, each bid embedding the `driver` PII block — exactly as immediate (§6.2). REST fallback `GET /v2/trips/:tripId/bids`.
- Rider selects via `v2/select-bid` (REST `PATCH /v2/trips/:tripId/bids/:bidId/accept`). On success the single-winner cascade runs and the trip becomes `DRIVER_SELECTED (16)` / `scheduledState 2 SCHEDULED_MATCHED`; **payment is held now**; `open_rides` removed; `open-trips-update {removed}` broadcast.

```jsonc
// select-bid ack (C2C) — tripStatus 16, scheduledState 2
{ "statusCode": 200, "data": { "tripId": "9a3c1f20-...", "bidId": "b1c2...", "driverId": "966220000015", "agreedFare": 480, "tripStatus": 16, "scheduledState": 2 } }
```
- Winner receives `v2/bid-won` (with the `rider` PII block), losers `v2/bid-lost`, rider `v2/bid-accepted` (with the chosen `driver` PII block) — same payloads as immediate (§5.5, §6.4). **`PAYMENT_HOLD_FAILED` is recoverable** here too: the trip stays open / bid stays PENDING; top up and re-select the same bid.

> **Verified (Flow C §4.4):** select → `tripStatus 16`, `scheduledState 2 SCHEDULED_MATCHED`, driver assigned, payment held (`riderAmount 645.12` for an agreed fare incl. tax/fees), and the trip appears in `my-upcoming`.

> **Key difference vs immediate:** selection does **not** start the trip. The trip now **waits** in `SCHEDULED_MATCHED` until the activation cron near departure (§10). The driver is **not** marked busy at selection — they're committed to the route but can keep bidding on other trips until activation.

---

## 9. Lifecycle while waiting — `GET /v2/schedule/my-upcoming`

Both rider and driver poll this for their matched/open future C2C trips (rider sees trips they own; driver sees trips assigned to them). Returns rows in `SCHEDULED_OPEN`, `SCHEDULED_MATCHED`, `REMINDED`, or `ACTIVATED`, ordered by departure.

```
GET /v2/schedule/my-upcoming    headers: { sessionid }
```
```jsonc
{
  "statusCode": 200,
  "data": [
    {
      "tripId": "9a3c1f20-...-uuid",
      "tripNo": 100231,
      "status": 16,                 // DRIVER_SELECTED
      "scheduledState": 2,          // SCHEDULED_MATCHED
      "originCityId": "c17a0000-0000-4000-8000-000000000001",
      "destinationCityId": "c17a0000-0000-4000-8000-000000000002",
      "seatsRequested": 2,
      "seatsConfirmed": 2,
      "scheduledDepartureAt": "2026-06-21T22:00:00.000Z",
      "biddingClosesAt": "2026-06-21T21:00:00.000Z",
      "riderOfferedFare": 511.5,
      "driverId": "966220000015",
      "currency": "SAR"
    }
  ]
}
```
Poll this on the upcoming-trips screen; the `scheduledState` tells you the phase (open / matched / reminded / activated). After `ACTIVATED`, switch to your V1 assigned-trip screens (the trip also surfaces in your normal active-trip APIs/events).

---

## 10. Reminder & activation (what the apps observe, and when)

Two server crons drive the scheduled → live transition. The apps do not call anything here — they **observe** notifications and state changes.

### 10.1 Reminder cron (Job A) — every 5 minutes

~`SETTING_C2C_REMIND_BEFORE_MIN` (30) min before departure, for each `SCHEDULED_MATCHED` trip not yet reminded:
- sets `riderNotifiedAt` and flips `scheduledState → 3 REMINDED`;
- pushes a `scheduled_trip_reminder` notification + `notify-trip-detail` to **both** the rider and the assigned driver.

The same job also **closes unmatched open requests** past `biddingClosesAt`: trip → `EXPIRED (10)` (if it had bids) or `NO_DRIVER (9)`, `scheduledState → 5 SCHEDULED_EXPIRED`, pending bids cancelled, `open_rides` cleared, rider gets `v2/bidding-timeout`.

**Apps observe:** a reminder push/notify-trip-detail (~30 min out); `my-upcoming` now shows `scheduledState 3`. For unmatched riders: `v2/bidding-timeout` and the trip drops out of `my-upcoming`.

### 10.2 Activation cron (Job B) — every minute

~`SETTING_C2C_ACTIVATE_BEFORE_MIN` (15) min before departure, for each `SCHEDULED_MATCHED`/`REMINDED` trip with a driver:
1. **Rider-conflict guard:** if the rider has another running trip (not completed/cancelled/expired and not in `NO_DRIVER`/`AWAITING_BIDS`), the match is **expired**: trip → `EXPIRED (10)`, `scheduledState → 5 SCHEDULED_EXPIRED`, **held payment released/voided**, both parties notified (`scheduled_trip_activation_failed`).
2. **On success:** re-hold/re-validate stale payment, generate the trip OTP (`tripOtp`), flip trip → `ACCEPTED_BY_DRIVER (2)`, `scheduledState → 4 ACTIVATED`, set `activatedAt`, mark the driver busy, and emit the standard V1 `driver_accepted` `notify-trip-detail` + push — **handing off to the existing V1 assigned-trip lifecycle**.

**Apps observe at activation:** the rider and driver receive the normal V1 `trip-detail` / `notify-trip-detail` (`driver_accepted`) with the OTP; the trip is now `ACCEPTED_BY_DRIVER (2)`, `scheduledState 4`. **Switch both apps to your existing V1 assigned-trip screens** (driver location, OTP start, completion, pay). From here, C2C is indistinguishable from a normal assigned trip.

> **Verified (Flow C §4.5, 5/5):** the cron is enabled and runs; activation flips a due trip to `scheduledState 4 ACTIVATED` + `status 2 ACCEPTED_BY_DRIVER` with a generated `tripOtp` (e.g. 9814). The full machine `SCHEDULED_OPEN → SCHEDULED_MATCHED → ACTIVATED (→ V1 lifecycle)` is verified end-to-end including the cron, and the rider-conflict guard's `SCHEDULED_EXPIRED` failure path was also observed working.

> **Cadence note (`V2_E2E_TEST_REPORT.md` §5.5):** the activation lock TTL is now ~50s (< the 1-min cron), so Job B runs each minute. Activation always completes well within the 15-min window, so do not rely on second-level timing — treat activation as "happens in the last ~15 min before departure".

### 10.3 The full state machine (observed live)

```
CREATE   POST /v2/schedule/city-to-city
         → AWAITING_BIDS(15) / SCHEDULED_OPEN(1)   [open for bids immediately; new-trip-request broadcast]
            │ drivers bid (v2/place-bid, 1-day TTL); rider watches v2/trip-bids-update
            ▼
SELECT   v2/select-bid
         → DRIVER_SELECTED(16) / SCHEDULED_MATCHED(2)   [payment HELD; v2/bid-won + v2/bid-accepted]   [trip WAITS]
            ▼
REMIND   Job A (~30 min before departure)
         → DRIVER_SELECTED(16) / REMINDED(3)   [rider + driver notified]
            ▼
ACTIVATE Job B (~15 min before departure, rider-conflict guard passes)
         → ACCEPTED_BY_DRIVER(2) / ACTIVATED(4)   [OTP generated; driver_accepted notify-trip-detail]
            ▼
RUN      driver-reached → OTP start (STARTED) → … → COMPLETED → pay/invoice   (all V1, unchanged)

EXPIRE   no match by biddingClosesAt → EXPIRED(10)/NO_DRIVER(9) / SCHEDULED_EXPIRED(5)  (v2/bidding-timeout)
         rider-conflict at activation → EXPIRED(10) / SCHEDULED_EXPIRED(5)  (hold released)
CANCEL   rider cancel before activation → CANCELLED_BY_RIDER(6) / SCHEDULED_CANCELLED(6)  (see §11)
```

---

## 11. Cancellation windows

### 11.1 `PATCH /v2/schedule/:tripId/cancel` (rider)

```jsonc
// PATCH /v2/schedule/9a3c1f20-.../cancel    headers: { sessionid }
// body (optional)
{ "reason": "plans changed" }
// response
{ "statusCode": 200, "data": {
    "tripId": "9a3c1f20-...",
    "tripStatus": 6,             // CANCELLED_BY_RIDER
    "scheduledState": 6,         // SCHEDULED_CANCELLED
    "feeApplied": false          // true if inside the no-free-cancel window
} }
```

**Window logic:**
- **Before match** (`SCHEDULED_OPEN`): free cancel; pending bids cascade to CANCELLED, `open_rides` cleared.
- **After match** (`SCHEDULED_MATCHED` / `REMINDED`), **more than** `SETTING_C2C_FREE_CANCEL_BEFORE_MIN` (60) min before departure: free cancel; the held payment is **released** (wallet) / **voided** (card); the assigned driver gets a `trip-closed` (reason `RIDER_CANCELLED`). `feeApplied: false`.
- **After match, inside the 60-min window:** cancellation **fee** applies (`feeApplied: true`); same release/void + driver notification.

**Negatives** (real HTTP status; `data.code` carries the machine code):
```jsonc
{ "statusCode": 404, "message": "TRIP_NOT_FOUND",      "data": { "code": "TRIP_NOT_FOUND" } }      // not a scheduled trip / unknown — HTTP 404
{ "statusCode": 400, "message": "NOT_TRIP_OWNER",      "data": { "code": "NOT_TRIP_OWNER" } }      // rider doesn't own it — HTTP 400
{ "statusCode": 400, "message": "TRIP_NOT_CANCELLABLE", "data": { "code": "TRIP_NOT_CANCELLABLE" } } // already ACTIVATED / EXPIRED / CANCELLED — HTTP 400
```
> Once `scheduledState` is `ACTIVATED (4)` the C2C cancel endpoint returns `TRIP_NOT_CANCELLABLE` — use the normal V1 active-trip cancel rules instead.

---

## 12. Full REST reference (C2C) — quick table

All require header `sessionid: <token>`. Bidding interactions reuse the immediate `/v2/trips/:tripId/bids*` routes (§7 of the immediate guide) unchanged.

| # | Method | Path | Actor | Body / Query | Returns |
|---|---|---|---|---|---|
| 1 | GET | `/v2/schedule/cities` | Rider/Driver | — | `{ statusCode:200, data:[ { id, name, nameAr, centroidLat, centroidLng } ] }` |
| 2 | GET | `/v2/schedule/quote` | Rider | `?originCityId&destinationCityId&cabId&seats` | `{ statusCode:200, data:{ recommendedFare, minFare, maxFare, routeDistanceKm, pricedBy } }` |
| 3 | POST | `/v2/schedule/city-to-city` | Rider | create body (§6.1) | `{ statusCode:201, data:{ id, tripStatus:15, scheduledState:1, biddingClosesAt, … } }` |
| 4 | GET | `/v2/schedule/open-in-route` | Driver | `?originCityId&destinationCityId` | `{ statusCode:200, data:{ trips:[ { tripId, originCityId, destinationCityId, scheduledDepartureAt, seatsRequested, … } ] } }` |
| 5 | GET | `/v2/schedule/my-upcoming` | Rider/Driver | — | `{ statusCode:200, data:[ { tripId, status, scheduledState, scheduledDepartureAt, … } ] }` |
| 6 | PATCH | `/v2/schedule/:tripId/cancel` | Rider | `{ reason? }` | `{ statusCode:200, data:{ tripStatus:6, scheduledState:6, feeApplied } }` |
| — | (reuse) | `/v2/trips/:tripId/bids`, `/bids/:bidId`, `/bids/:bidId/accept`, `/bids/:bidId/reject`, `/raise-offer` | both | see immediate guide §7 | bidding lifecycle |

---

## 13. Socket reference (C2C)

C2C emits the **same** socket events as immediate bidding; only the timing/values differ. No C2C-only socket events exist — all bidding events are shared.

| Event | Direction | C2C specifics |
|---|---|---|
| `new-trip-request` | server → driver | broadcast on create; `requestExpiresAt = biddingClosesAt` (far future); carries pickup+dropoff, fareRange, `rider` PII |
| `open-trips-list` / `open-trips-update` | server → driver | scheduled rows appear/leave the open set; `{removed}` on select/expire/cancel |
| `v2/sync-open-trips` | driver → server | pulls the open list (incl. scheduled rows in range) — REST equivalent `GET /v2/schedule/open-in-route` for route-scoped C2C |
| `v2/place-bid` | driver → server | bid TTL is `expiredAt = now + 86400s`; rejected with `BIDDING_CLOSED` after `biddingClosesAt` |
| `v2/withdraw-bid` | driver → server | unchanged |
| `v2/trip-bids-update` | server → rider | unchanged (cheapest-first, embedded `driver` PII) |
| `v2/select-bid` | rider → server | ack carries `tripStatus:16, scheduledState:2`; holds payment; rejected with `BIDDING_CLOSED` after close |
| `v2/bid-won` / `v2/bid-lost` / `v2/bid-accepted` | server → driver/rider | unchanged PII blocks; **trip then waits** (does not run immediately) |
| `v2/bidding-timeout` | server → rider | emitted when the reminder cron closes an unmatched request past `biddingClosesAt` |
| `trip-closed` | server → driver | reason `RIDER_CANCELLED` when the rider cancels a matched trip |
| `notify-trip-detail` (`scheduled_trip_reminder`) | server → rider+driver | reminder cron (~30 min out) |
| `notify-trip-detail` (`scheduled_trip_activation_failed`) | server → rider+driver | activation guard expired the match (rider conflict / stale payment) |
| `notify-trip-detail` (`driver_accepted`) + V1 `trip-detail` | server → rider+driver | **activation hand-off** (~15 min out) → switch to V1 assigned-trip flow |

---

## 14. Error-code catalog (C2C)

C2C-specific codes (in addition to the immediate bidding catalog in the immediate guide §8, which all apply unchanged — `DRIVER_INELIGIBLE`, `CAB_TYPE_MISMATCH`, `BID_BELOW_FLOOR`/`BID_ABOVE_CEILING`, `TRIP_NOT_OPEN`, `PAYMENT_HOLD_FAILED`, `DRIVER_RACE_LOST`, `NOT_TRIP_OWNER`, `VALIDATION_ERROR`, `INTERNAL_ERROR`, …):

| code | applies to | meaning |
|---|---|---|
| `C2C_DISABLED` | create | `SETTING_C2C_ENABLED != 1` |
| `SAME_ORIGIN_DESTINATION` | quote, create | origin city == destination city |
| `INVALID_CITY` | quote, create | origin/destination city id unknown, missing, or `status=false` |
| `DEPARTURE_TOO_SOON` | create | `scheduledDepartureAt` < now + `SETTING_C2C_MIN_LEAD_MINUTES` (60) |
| `DEPARTURE_TOO_FAR` | create | beyond now + `SETTING_C2C_MAX_LEAD_DAYS` (30) |
| `INVALID_SEATS` | create | seats < 1 or > `SETTING_C2C_MAX_SEATS` (6) |
| `INTERCITY_ROUTE_NOT_PRICED` | quote, create | no route row and the centroid fallback can't price the pair |
| `OFFER_OUT_OF_BAND` | create | `riderOfferedFare` ∉ `[minFare, maxFare]` (flattens `data.minFare`/`data.maxFare` + `data.submitted`) |
| `BIDDING_CLOSED` | place-bid, select-bid | `now > biddingClosesAt` (departure − 60 min) |
| `TRIP_NOT_CANCELLABLE` | cancel | trip already `ACTIVATED` / `SCHEDULED_EXPIRED` / `SCHEDULED_CANCELLED` |
| `TRIP_NOT_FOUND` | cancel | not a scheduled trip / unknown trip |
| `CREATE_FAILED` | create | underlying trip-create failed after validation passed |

> Read the machine code from `resp.data.code` and the human string from the top-level `message`; the HTTP `statusCode` is reliable too (C2C create codes are HTTP 400, `TRIP_NOT_FOUND` is 404 — see §6.2 / §11.1).

---

## 15. Integration checklist & gotchas

- [ ] REST auth header is **`sessionid`** (the verifyotp `token`), not `Authorization`. Socket identity = `subscribe-user { userID }` on every (re)connect.
- [ ] Use a **"City to City" category** cab (`C2C Sedan` `c2c5eda0-…0001` / `C2C SUV` `…0002`) as `cabId`; a non-C2C cab is rejected at bid time with `CAB_TYPE_MISMATCH`.
- [ ] Call `GET /v2/schedule/quote` first and validate the rider's offer against `minFare`/`maxFare` client-side before create (server re-validates → `OFFER_OUT_OF_BAND`).
- [ ] `scheduledDepartureAt` must be in `[now + 60 min, now + 30 days]` (`DEPARTURE_TOO_SOON` / `DEPARTURE_TOO_FAR`). Send ISO-8601 UTC.
- [ ] `seats` must be 1…6 (`INVALID_SEATS`). Seats feed the fare via `perSeatFare`.
- [ ] Routes are **directed** and only some pairs are priced; expect `pricedBy: "formula"` (centroid fallback) or `INTERCITY_ROUTE_NOT_PRICED` for unseeded pairs.
- [ ] Branch C2C UI on **`scheduledState`** (1 open → 2 matched → 3 reminded → 4 activated; 5 expired / 6 cancelled), and on `TripStatus` once `ACTIVATED`.
- [ ] **Bids live 1 day (86400s), not 45s.** Don't show a 45s countdown for C2C; honor `expiredAt`. Stop allowing bids after `biddingClosesAt` (`BIDDING_CLOSED`).
- [ ] Selection **holds payment** (possibly days early) but does **not** start the trip — the trip waits. Handle `PAYMENT_HOLD_FAILED` as recoverable (top up, re-select same bid).
- [ ] Poll `GET /v2/schedule/my-upcoming` on the upcoming screen for both rider and driver.
- [ ] At **reminder** (~30 min out) expect a `scheduled_trip_reminder` push + `scheduledState 3`. At **activation** (~15 min out) expect a `driver_accepted` `notify-trip-detail` with the OTP and `status 2 ACCEPTED_BY_DRIVER` — **switch both apps to the existing V1 assigned-trip flow** from here.
- [ ] Activation can **fail** if the rider has a conflicting running trip → `scheduled_trip_activation_failed`, trip `EXPIRED`/`SCHEDULED_EXPIRED`, hold released. Handle this gracefully on both apps.
- [ ] Cancellation: free before `biddingClosesAt` and >60 min before departure; **fee** inside 60 min (`feeApplied:true`); `TRIP_NOT_CANCELLABLE` once activated.
- [ ] Treat `GET /v2/schedule/open-in-route` as the authoritative C2C route/seats/departure source; the broadcast item is the generic open-trip shape (its far-future `requestExpiresAt` is the C2C tell).
- [ ] Everything after activation (driver-reached → OTP start → complete → pay) is **unchanged V1** — reuse your existing code.
```
