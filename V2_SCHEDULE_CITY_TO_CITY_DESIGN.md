# V2 Scheduled City-to-City Rides — Design Document

> **Status:** Design / Documentation only — _no code is changed by this document._ Implementation begins after review.
> **Date:** 2026-06-18
> **Branch context:** `ride-demo-v2`
> **Scope:** `rides-service`, `api-gateway`, `socket-gateway`, `master-service` (cities), `driver-services` (cab-type-category)
> **Models the inDrive "City to City" product** on top of the existing V2 broadcast/bidding engine.
> **Read first:** `docs/V2_MOBILE_INTEGRATION_GUIDE.md`, `docs/V2_BIDDING_BROADCAST_DESIGN.md`.

---

## 0. TL;DR

A rider schedules an **intercity** trip for a future date/time, picks origin city + destination city (+ pickup/dropoff points + seats), and **proposes a fare**. The request is stored as a future trip and, from creation, is published as an **open scheduled request** that drivers traveling that route can **bid** on using the existing V2 bidding engine (`trip_bids`, `place-bid`, `select-bid`). The rider selects a bid; we **hold payment at selection**. A scheduler (`@Cron`) sends reminders and, near departure, **activates** the trip into the normal assigned-trip lifecycle (driver-reached → OTP → start → complete).

This reuses, almost entirely, machinery that already exists:
- `TripType.SCHEDULED = 2` and the scheduled-trip columns/flow (already in the entity, partially wired, cron **disabled**).
- The V2 bidding engine (`trip_bids`, `BidStatus`, `BidType`, `TripDispatchMode.BID`, `AWAITING_BIDS=15`, `DRIVER_SELECTED=16`).
- The `@Cron` infra + Redis distributed-lock pattern.

**New work** is concentrated in: an intercity fare model, origin/destination **city** linkage, **seats**, a small set of new statuses/flags for the scheduled lifecycle, and a scheduler that bridges "open scheduled request" → "near-departure activation".

> **Dependency (do NOT redesign here):** This design depends on the shared `category_type` ENUM gaining a `SCHEDULE` member and the `order` column on the category table, both owned by a **sibling agent** (Courier/Category design). We reference `category_type = SCHEDULE` to classify the offering; we do **not** define or alter that ENUM/column. See §11.

---

## 1. Current-State Analysis (verified, file:line)

### 1.1 Scheduled trips already half-exist (and are currently disabled)

The codebase already has a first-generation "book for later" feature for **same-city** scheduled rides. It is the foundation we extend.

| Concern | Evidence | State |
|---|---|---|
| Trip type discriminator | `rides-service/src/modules/trips/trips.enum.ts:1-4` — `TripType { IMMEDIATELY=1, SCHEDULED=2 }` | exists |
| Scheduled timestamp column | `rides-service/src/modules/trips/entities/trips.entity.ts:369-374` — `riderScheduledAt: Date` | exists |
| Rider-notified column | `trips.entity.ts:376-381` — `riderNotifiedAt: Date` (set ~30 min before departure) | exists |
| Expiry / assignment columns | `trips.entity.ts:362-367 driverAssignedAt`, `:390-402 tripExpiredAt` | exists |
| Create-time branch for SCHEDULED | `trips.service.ts` `create()` scheduled branch increments `customer.upcomingRides` and does **not** add the trip to `open_rides` (immediate trips only enter the pool) | exists |
| Reminder cron fn | `trips.service.ts:5390-5464` — `notifyRidersForScheduledTrips()` (finds `SCHEDULED` + `PENDING` trips with `riderScheduledAt` within `SETTING_NOTIFY_TIME_SCHEDULED_TRIP`, sets `riderNotifiedAt`, notifies rider) | implemented |
| Activation cron fn | `trips.service.ts:5466-5540` — `processScheduledTrips()` (finds trips due within 5 min, calls `checkIfScheduledTripCanBeProcessed`) | implemented |
| Activation guard | `trips.service.ts:5549-5638` — `checkIfScheduledTripCanBeProcessed()` (EXPIRES the trip if the rider has a running trip, or if the rider is a driver with `driverModeSwitch` on; otherwise proceeds to process) | implemented |
| Confirm / decline | api-gateway `trips.controller.ts` `/confirm-schedule/:tripId`, `/decline-schedule/:tripId`; rides `acceptScheduledTrip()` / `declineScheduledTrip()` | exists |
| **Scheduler is OFF** | `rides-service/src/cron/schedule-trip/schedule-trip.service.ts:11-16` — `@Cron(EVERY_5_MINUTES)` registered but **both calls are commented out** | **DISABLED** |

**Key takeaways:**
1. Scheduled trips today are **same-city** and ride the **AUTO** dispatch model (they activate into the V1 pool near departure); there is **no city-to-city** and **no bidding** for them.
2. The activation cron exists but is **commented out** (`schedule-trip.service.ts:14-15`) — we will re-enable it (extended) rather than build a scheduler from scratch.
3. Scheduled trips are **never added to `open_rides` at creation** today — they only enter dispatch near departure. Our design **changes this for City-to-City** (open immediately for bids; see §4.3 design decision).

### 1.2 The V2 bidding/broadcast engine we reuse

Fully implemented and E2E-tested on `ride-demo-v2` (see `docs/V2_BIDDING_BROADCAST_DESIGN.md`). Relevant anchors:
- `TripDispatchMode { AUTO=1, BID=2 }` — `trips.enum.ts:41-44`.
- `TripStatus.AWAITING_BIDS=15`, `DRIVER_SELECTED=16` — `trips.enum.ts:33-34`.
- `trip_bids` entity, `BidStatus`/`BidType`, `placeBid()` / `select-bid` selection cascade, payment **hold at selection**, single-winner locks (`trip-action-taken-${tripId}`, `driver-assignment-lock-${driverId}`).
- `expireBids()` swept by `@Cron(EVERY_10_SECONDS)` — `rides-service/src/cron/process-bid-expiry/process-bid-expiry.service.ts`.
- Open-set broadcast helpers (`new-trip-request`, `open-trips-update`) over NestJS **TCP** to socket-gateway.

### 1.3 Cron + Redis infra we reuse

- `ScheduleModule.forRoot()` registered (`rides-service/src/app.module.ts`). Three Nest `@Cron` services already exist (schedule-trip [disabled], process-pending-trip-drivers [EVERY_MINUTE → `expirePendingTrips()`], process-bid-expiry [EVERY_10_SECONDS → `expireBids()`]).
- **Distributed cron lock pattern** (cluster-safe): `expirePendingTrips()` uses `redisHandler.setNxEx('expirePendingTrips-cronjob', 60*4, 1)` and bails if the key already exists. We reuse this exact pattern for the scheduled-trip cron.
- Redis access: `rides-service/src/helpers/redis-handler-with-pool.ts` (`setNxEx`, `getRedisKey`, `set`, `del`), injected into `TripsService`. Config lives in Redis `SETTING_*` keys (e.g. `SETTING_TRIP_REQUEST_TIME`, `SETTING_NOTIFY_TIME_SCHEDULED_TRIP`, `SETTING_BID_*`).

### 1.4 City / geography model — partial, no intercity

| Model | File | Has | Lacks |
|---|---|---|---|
| `CitiesEntity` (table `geo-cities`) | `master-service/src/modules/locations/entities/city.entity.ts` | `cityName`, `status`, `country` FK | no centroid/geofence; not referenced by trips |
| `CityEntity` (table `cities`) | `driver-services/src/modules/cab-charges/entities/city.entity.ts` | `name`, `entryNo`, `country` FK; OneToMany `CabChargesEntity` | per-city, not per-route |
| `CabChargesEntity` (table `cab_charges`) | `driver-services/src/modules/cab-charges/entities/cab-charges.entity.ts` | per `cabId`+`country`+`city`+`day`: `passengerBaseFare`, `passengerCostPerMin`, `passengerCostPerKm` | **no intercity / route pricing** |
| `HighDemandZoneEntity` (table `demand_zone`) | `rides-service/src/modules/high-demand-zone/...` | lat/lon surge zones | not routing |
| `TripAddressEntity` (table `trip_addresses`) | `rides-service/src/modules/trip_address/trip_address.entity.ts` | `addressType (1 pickup / 2 destination)`, `address`, lat/lon | **no origin/destination city id** |

**Conclusion:** There are two unrelated city tables and **no intercity/route pricing** of any kind. Trips store pickup/dropoff as free coordinates, not city ids. We must add (a) a canonical city reference usable by rides-service, and (b) an intercity fare basis.

### 1.5 Category model & the SCHEDULE dependency

- `CabTypeCategoryEntity` (table `cab_type_category`) — `driver-services/src/modules/cab-type-category/entities/cab-type-category.entity.ts`: has `name`, `image`, **`order: tinyint`** (`:17-18`), `status`, `isDeleted`. Cab types reference it via `cabType.categoryId`.
- There is **no `category_type` ENUM today** — the sibling agent owns adding it (with a `SCHEDULE` member) plus the `order` column semantics. **We depend on `category_type = SCHEDULE`; we do not design it.** See §11.

---

## 2. Product Requirements (inDrive "City to City" mapped to V2)

| # | Requirement | Mapped to |
|---|---|---|
| C1 | Offering classified as `category_type = SCHEDULE` (sibling-owned). | §11 dependency |
| C2 | Rider picks **origin city + destination city**, **pickup point + dropoff point**, **scheduled departure date/time**, **seats**, and a **proposed fare**. | §3 data model, §6 flow |
| C3 | Future trip is stored; it becomes an **open scheduled request immediately** so route-traveling drivers can bid right away. | §4.3 decision, §5 |
| C4 | Drivers bid (accept fare / counter), rider selects, **payment held at selection**; near departure the trip **activates** into the assigned-trip lifecycle. | §5 (reuse bidding), §6 |
| C5 | Reminders, activation, expiry, and cancellation windows for the scheduled lifecycle. | §5.3, §5.4, §7 |

---

## 3. Data Model

All additive and backward-compatible. Integer-backed enums; `float` money; nullable columns so V1/V2 immediate rows are unaffected.

### 3.1 `trips` (`trips.entity.ts`) — additions

We **reuse** the existing scheduled columns (`riderScheduledAt`, `riderNotifiedAt`, `tripExpiredAt`, `driverAssignedAt`) and the existing V2 bidding columns (`dispatchMode`, `riderOfferedFare`, `recommendedFare`, `minFare`, `maxFare`, `currency`, `selectedBidId`, `biddingEnabled`, `biddingStartedAt`). Net-new columns for City-to-City:

```ts
// V2 SCHEDULED CITY-TO-CITY (additive; null for all existing rows)
@Column({ length: 36, nullable: true, comment: 'V2 C2C: origin city id (canonical city ref)' })
originCityId: string;

@Column({ length: 36, nullable: true, comment: 'V2 C2C: destination city id' })
destinationCityId: string;

@Column({ type: 'tinyint', nullable: true, default: 1, comment: 'V2 C2C: seats requested by rider' })
seatsRequested: number;

@Column({ type: 'tinyint', nullable: true, comment: 'V2 C2C: seats the assigned driver committed' })
seatsConfirmed: number;

@Column({ type: 'datetime', nullable: true, comment: 'V2 C2C: scheduled DEPARTURE date/time (== riderScheduledAt for C2C)' })
scheduledDepartureAt: Date;   // alias of riderScheduledAt for clarity; may simply reuse riderScheduledAt (OD-6)

@Column({ type: 'enum', enum: ScheduledTripState, nullable: true,
  comment: 'V2 C2C: fine-grained scheduled lifecycle sub-state (see §3.4)' })
scheduledState: ScheduledTripState;

@Column({ type: 'datetime', nullable: true, comment: 'V2 C2C: when bidding window auto-closes (before departure)' })
biddingClosesAt: Date;

@Column({ type: 'datetime', nullable: true, comment: 'V2 C2C: when the request was activated into dispatch' })
activatedAt: Date;
```

**Reuse map (do NOT duplicate):** scheduled departure = existing `riderScheduledAt` (we expose it as `scheduledDepartureAt` in API; if we want a distinct column we add the alias above, otherwise reuse `riderScheduledAt`); reminder timestamp = existing `riderNotifiedAt`; mode = existing `dispatchMode = BID`; offered/recommended/min/max fare + winning bid + bidding flags = existing V2 columns; assigned driver/amounts = existing `driverId`/`riderAmount`/`driverAmount`; expiry = existing `tripExpiredAt`. New indexes: `@Index(['scheduledState'])`, `@Index(['scheduledDepartureAt'])`, `@Index(['originCityId','destinationCityId'])`.

> **Trip type:** City-to-City sets `tripType = SCHEDULED (2)` **and** `dispatchMode = BID (2)`. The `(tripType=SCHEDULED, dispatchMode=BID)` pair is the discriminator for City-to-City vs. the legacy same-city scheduled (`tripType=SCHEDULED, dispatchMode=AUTO`) and vs. immediate bidding (`tripType=IMMEDIATELY, dispatchMode=BID`).

### 3.2 New canonical `cities` reference for rides-service

rides-service has no city table. Rather than cross-service join, add a **lightweight read-model city table owned by rides-service** (or reuse master-service `geo-cities` via the existing master TCP client — pick one in implementation; see OD-1). The trip stores `originCityId`/`destinationCityId` as opaque ids resolvable to a display name + centroid.

Minimum city shape needed by C2C (whichever table is canonical must provide):
```ts
{ id: string; name: string; nameAr?: string; centroidLat: number; centroidLng: number; countryId: string; status: boolean }
```
`centroidLat/Lng` are required for the intercity distance/fare basis (§4) and for the route-match broadcast (§5.1). If we reuse master-service `geo-cities`, it must be **extended with centroid columns** (it currently has only `cityName`, `status`, `country`).

### 3.3 New `intercity_routes` table (fare basis) — `rides-service/src/modules/intercity/entities/intercity_route.entity.ts`

There is no route/intercity pricing today. Add a per-route, per-cab-type fare basis used to compute the **recommended** fare and the `[min,max]` bid band for a city pair.

```ts
@Entity('intercity_routes')
@Unique(['originCityId', 'destinationCityId', 'cabTypeId'])
@Index(['originCityId', 'destinationCityId'])
@Index(['status'])
export class IntercityRoute extends AbstractEntity {
  @Column({ length: 36 }) originCityId: string;
  @Column({ length: 36 }) destinationCityId: string;
  @Column({ length: 36, nullable: true, comment: 'null = applies to all cab types of SCHEDULE category' }) cabTypeId: string;
  @Column({ type: 'float', comment: 'flat base fare for the route' }) baseFare: number;
  @Column({ type: 'float', default: 0, comment: 'per-seat fare (multiplies seatsRequested)' }) perSeatFare: number;
  @Column({ type: 'float', default: 0, comment: 'per-km top-up over straight-line/route km' }) costPerKm: number;
  @Column({ type: 'float', nullable: true, comment: 'cached straight-line/route distance km (origin↔dest centroids)' }) routeDistanceKm: number;
  @Column({ length: 3, default: 'SAR' }) currency: string;
  @Column({ type: 'boolean', default: true }) status: boolean;
}
```

**Why a table, not `cab_charges`:** `cab_charges` is per-**single**-city, per-day, intra-city pricing (`driver-services`). Intercity is per **city-pair**. Keeping it separate avoids overloading the intra-city pricing meaning and lets ops price routes independently. (OD-2: alternative is a pure formula from centroid distance with no table — simpler launch, less control. Recommended to ship the table but allow fallback to formula when no row matches.)

### 3.4 New enum `ScheduledTripState` — `rides-service/src/modules/trips/trips.enum.ts` (append)

`TripStatus` already carries the **coarse** lifecycle (PENDING, AWAITING_BIDS, DRIVER_SELECTED, ACCEPTED_BY_DRIVER, …). City-to-City needs a **scheduled sub-state** that is orthogonal to "is a driver assigned yet" — because a scheduled trip can be `DRIVER_SELECTED` (a driver won the bid) yet still be days away from departure and not in the active-trip lifecycle. We model that with a dedicated sub-state column (§3.1) rather than overloading `TripStatus`.

```ts
// APPEND-ONLY; orthogonal to TripStatus
export enum ScheduledTripState {
  SCHEDULED_OPEN      = 1,  // future trip created, open for bids (TripStatus.AWAITING_BIDS=15)
  SCHEDULED_MATCHED   = 2,  // rider selected a bid; payment held; driver committed (TripStatus.DRIVER_SELECTED=16)
  REMINDED            = 3,  // pre-departure reminder sent to rider + driver (riderNotifiedAt set)
  ACTIVATED           = 4,  // near departure; handed to assigned-trip lifecycle (TripStatus.ACCEPTED_BY_DRIVER=2)
  SCHEDULED_EXPIRED   = 5,  // window passed with no match, or activation guard failed (TripStatus.EXPIRED=10 / NO_DRIVER=9)
  SCHEDULED_CANCELLED = 6,  // rider/driver cancelled before activation (TripStatus.CANCELLED_BY_RIDER=6 / BOOKING_CANCELLED=12)
}
```

> No `TripStatus` integers are renumbered. `ScheduledTripState` is a parallel, nullable column that is only set for `tripType=SCHEDULED` rows. The coarse `TripStatus` continues to drive the rest of the system; `scheduledState` drives the scheduler's decisions.

### 3.5 Reuse of `trip_bids` and `open_rides`

- **`trip_bids`** is reused unchanged (the bidding engine doesn't care that a trip is scheduled). Optional additive column `seatsOffered: tinyint` on `trip_bids` if a driver may commit fewer seats than requested (OD-3); default = trip's `seatsRequested`.
- **`open_rides`** is reused as the discovery index. C2C rows are inserted at creation (see §5.1). The optional denormalized columns from the bidding doc (`offeredFare`, `dropoff_latitude/longitude`, `requestExpiresAt`) carry the scheduled values; add nullable `scheduledDepartureAt`, `originCityId`, `destinationCityId` to `open_rides` so route-match filtering needs no join.

---

## 4. Intercity Fare Model

No new pricing **engine** — a route-aware variant of the existing fare-range derivation (mirrors `V2_BIDDING_BROADCAST_DESIGN.md §5.5`).

```
routeDistanceKm = intercity_routes.routeDistanceKm
                  ?? haversine(originCity.centroid, destCity.centroid)   // fallback if no cached value

recommended = baseFare
            + perSeatFare * seatsRequested
            + costPerKm   * routeDistanceKm
            // (× fareMultiplier if a scheduled surge applies; default 1.0 for C2C)

min = recommended * SETTING_C2C_BID_MIN_FACTOR   // default 0.80 (reuse SETTING_BID_MIN_FACTOR if no C2C-specific key)
max = recommended * SETTING_C2C_BID_MAX_FACTOR   // default 2.00
```

- The rider's `riderOfferedFare` must satisfy `min ≤ riderOfferedFare ≤ max` (same gate as immediate bidding; reuse the create-time validation).
- If no `intercity_routes` row matches `(originCity, destCity, cabType)`, fall back to: the all-cab-types route row → else a pure centroid-distance formula using existing intra-city `cab_charges.costPerKm` as a coefficient (OD-2). Surface a clear error `INTERCITY_ROUTE_NOT_PRICED` only if even the fallback can't compute (no centroids).
- **Seats** affect the *recommendation* via `perSeatFare`; the agreed fare is still the **winning bid amount** (driver and rider negotiate the all-in price). Final `riderAmount`/`driverAmount` come from the winning bid + the existing fee pipeline, exactly as immediate bidding.

---

## 5. Scheduling Behavior & Activation Mechanism

### 4.3 / 5.0 Core design decision — open immediately, activate near departure

**Decision (matches inDrive "City to City" and requirement C3):** a scheduled C2C request is published to drivers as an **open scheduled request immediately at creation** (drivers traveling that route can browse and bid days in advance), and is **activated into the active-trip lifecycle near departure**. This is a deliberate change from the legacy same-city scheduled trips, which only enter dispatch at departure.

Rationale: intercity drivers plan trips in advance; the value of City-to-City is letting them pre-commit to a route. Bidding therefore happens **now**, payment is **held at selection** (could be days before departure), and the trip simply **waits** in `SCHEDULED_MATCHED` until the activation window. This reuses the entire bidding engine with zero new bidding logic.

### 5.1 Creation → open scheduled request

`POST /v2/schedule/city-to-city` → rides `V2_CREATE_SCHEDULED_TRIP`:
1. Validate cities, `scheduledDepartureAt` is in the future and within `[SETTING_C2C_MIN_LEAD_MINUTES, SETTING_C2C_MAX_LEAD_DAYS]`, seats ≥ 1 ≤ `SETTING_C2C_MAX_SEATS`.
2. Compute `recommended/min/max` (§4); validate `riderOfferedFare` in band.
3. Insert `trips` row: `tripType=SCHEDULED`, `dispatchMode=BID`, `status=AWAITING_BIDS(15)`, `scheduledState=SCHEDULED_OPEN`, `riderScheduledAt/scheduledDepartureAt`, `originCityId`/`destinationCityId`, `seatsRequested`, fare columns, `biddingClosesAt = scheduledDepartureAt − SETTING_C2C_BIDDING_CLOSE_BEFORE_MIN` (e.g. 60 min). Increment `customer.upcomingRides` (reuse existing scheduled-create behavior).
4. Insert `trip_addresses` (pickup `addressType=1`, dropoff `addressType=2`).
5. Insert `open_rides` row with C2C denormalized fields (city ids, departure, fare, dropoff coords). Set `requestExpiresAt = biddingClosesAt` (NOT 2 min — C2C bid windows are long).
6. Broadcast `new-trip-request` (reuse `broadcastNewTrip()`), carrying C2C fields (cities, departure, seats, fareRange). Drivers self-filter by **route** (origin/destination city) and departure window, in addition to the existing range/cab filter.

> The standard 2-minute `SETTING_TRIP_REQUEST_TIME` open-trip expiry **must not** apply to C2C. `expirePendingTrips()` already deletes `open_rides` older than `tripRequestTimeLimit`; it must be modified to **exclude scheduled rows** (`tripType=SCHEDULED`) so they aren't reaped after 2 minutes. C2C open-trip lifetime is governed by `biddingClosesAt`/the scheduled cron instead. **This is a required guard.**

### 5.2 Bidding → selection (reuse engine verbatim)

- Drivers bid via existing `v2/place-bid` (`V2_PLACE_BID`); eligibility gate enforced at bid time (incl. cab-type = SCHEDULE category cab). Bids live in `trip_bids`.
- **Per-bid TTL for C2C:** the 45s `SETTING_BID_TTL_SECONDS` is wrong for a multi-day window. Introduce `SETTING_C2C_BID_TTL_SECONDS` (e.g. 86400 = 1 day, or "no expiry until `biddingClosesAt`"). `expireBids()` must read the C2C TTL for scheduled trips (OD-4) — otherwise driver bids vanish in 45s.
- Rider sees the live competing-bid list (`v2/trip-bids-update`), selects via `v2/select-bid` (`V2_RIDER_ACCEPT_BID`). Selection runs the existing single-winner cascade: winner `trip_bids.status=ACCEPTED`, siblings `REJECTED_BY_RIDER`, `trip.selectedBidId` set, `trip.riderAmount/driverAmount = agreedFare`, `TripStatus → DRIVER_SELECTED(16)`, `scheduledState → SCHEDULED_MATCHED`, `open_rides` removed, `open-trips-update{removed}` broadcast.
- **Payment hold at selection** (requirement C4): reuse the existing select-bid payment-hold. For C2C the hold may sit for days — handle expiry of card pre-auths (TAP holds can expire) by **re-authorizing at activation** if the original hold is stale (OD-5). Wallet holds are unaffected.

> Decision: **hold at selection, re-verify/re-hold at activation.** Selection commits the match and reserves funds immediately (inDrive-like commitment), and the activation step re-validates the hold so a days-old expired pre-auth doesn't strand the trip at departure.

### 5.3 Reminder & activation scheduler (re-enable + extend the disabled cron)

Re-enable `ScheduleTripService` (`schedule-trip.service.ts`) — currently both calls are commented out. Run on a tighter cadence than 5 min near departure (keep `@Cron(EVERY_5_MINUTES)` for reminders; add a `@Cron(EVERY_MINUTE)` activation sweep, or one `EVERY_MINUTE` job that does both). Wrap each job body in the **distributed Redis lock** (`setNxEx('c2c-scheduler-cronjob', 60*4, 1)`) exactly like `expirePendingTrips()`.

**Job A — `notifyScheduledC2CParticipants()`** (extends existing `notifyRidersForScheduledTrips()`):
- Find `tripType=SCHEDULED, dispatchMode=BID, scheduledState=SCHEDULED_MATCHED, riderNotifiedAt=null` with `scheduledDepartureAt` within `SETTING_C2C_REMIND_BEFORE_MIN` (e.g. 30 min).
- Set `riderNotifiedAt`, `scheduledState=REMINDED`; push reminder to **both** rider and the assigned driver (notify-trip-detail + push). For C2C the existing rider-only confirm/decline can be retained as an optional rider re-confirm.
- For **unmatched** open requests (`SCHEDULED_OPEN`) whose `biddingClosesAt` has passed: close bidding → `TripStatus=NO_DRIVER(9)` or `EXPIRED(10)`, `scheduledState=SCHEDULED_EXPIRED`, cascade bids to `EXPIRED/CANCELLED`, remove `open_rides`, notify rider (`v2/bidding-timeout`).

**Job B — `activateScheduledC2CTrips()`** (extends existing `processScheduledTrips()` + `checkIfScheduledTripCanBeProcessed()`):
- Find `scheduledState=REMINDED (or MATCHED), dispatchMode=BID` with `scheduledDepartureAt` within `SETTING_C2C_ACTIVATE_BEFORE_MIN` (e.g. 15 min).
- Run the existing activation guards (`checkIfScheduledTripCanBeProcessed`): rider has no conflicting running trip; if a guard fails → `EXPIRED`, `scheduledState=SCHEDULED_EXPIRED`, refund/release the held payment, notify both parties.
- On success: re-validate/re-hold payment if stale (§5.2), set `TripStatus=ACCEPTED_BY_DRIVER(2)`, `scheduledState=ACTIVATED`, `activatedAt`, generate the trip OTP, then hand off to the **existing V1 assigned-trip lifecycle** (`notifyTripDetail('driver_accepted')`, `trip-detail`, OTP start, complete, pay) — identical to what `bid-accepted` does for immediate trips, just deferred to departure.

### 5.4 Expiry & cancellation windows

| Situation | Rule | Status / state |
|---|---|---|
| No bids selected by `biddingClosesAt` | Job A closes it | `NO_DRIVER(9)`/`EXPIRED(10)`, `SCHEDULED_EXPIRED` |
| Matched but rider conflict at activation | Activation guard expires | `EXPIRED(10)`, `SCHEDULED_EXPIRED`, release hold |
| Rider cancels before `biddingClosesAt` | free cancel | `CANCELLED_BY_RIDER(6)`, `SCHEDULED_CANCELLED`, bids cascade, hold released |
| Rider cancels after match, before `SETTING_C2C_FREE_CANCEL_BEFORE_MIN` | free cancel | as above |
| Rider cancels inside the no-free-cancel window (e.g. < 60 min to departure) | cancellation fee (reuse fee policy) | `CANCELLED_BY_RIDER(6)`, `SCHEDULED_CANCELLED`, partial charge |
| Driver (winner) cancels before activation | re-open for bids if time permits, else expire | back to `AWAITING_BIDS(15)`/`SCHEDULED_OPEN` and re-broadcast, OR `EXPIRED` |
| After activation | normal V1 active-trip cancel rules apply | V1 |

`SETTING_*` knobs (Redis, seeded — no DDL): `SETTING_C2C_MIN_LEAD_MINUTES`, `SETTING_C2C_MAX_LEAD_DAYS`, `SETTING_C2C_MAX_SEATS`, `SETTING_C2C_BIDDING_CLOSE_BEFORE_MIN`, `SETTING_C2C_REMIND_BEFORE_MIN`, `SETTING_C2C_ACTIVATE_BEFORE_MIN`, `SETTING_C2C_FREE_CANCEL_BEFORE_MIN`, `SETTING_C2C_BID_TTL_SECONDS`, `SETTING_C2C_BID_MIN_FACTOR`, `SETTING_C2C_BID_MAX_FACTOR`, `SETTING_C2C_ENABLED`.

---

## 6. End-to-End Flow

```
SCHEDULE  Rider POST /v2/schedule/city-to-city
          { originCityId, destinationCityId, addresses:[pickup,dropoff],
            scheduledDepartureAt, seats, riderOfferedFare, cabId(SCHEDULE category) }
          → validate band + lead time → insert trip (SCHEDULED+BID, AWAITING_BIDS, SCHEDULED_OPEN)
          → open_rides row (city ids, departure, fare) → broadcastNewTrip(new-trip-request)
              │  drivers self-filter by ROUTE (origin/dest city) + departure window + range/cab
              ▼
BID       Drivers v2/place-bid (accept / counter)  → trip_bids (C2C TTL, not 45s)
          Rider watches v2/trip-bids-update (live competing list)
              ▼
SELECT    Rider v2/select-bid → single-winner cascade → DRIVER_SELECTED(16)/SCHEDULED_MATCHED
          → PAYMENT HELD at selection → open_rides removed → open-trips-update{removed}
          → v2/bid-won (driver) + v2/bid-accepted (rider)        [trip now waits for departure]
              ▼
REMINDER  Cron Job A (~30 min before departure): set riderNotifiedAt, REMINDED, notify rider+driver
              ▼
ACTIVATE  Cron Job B (~15 min before departure): activation guards pass
          → re-hold payment if stale → OTP generated → ACCEPTED_BY_DRIVER(2)/ACTIVATED
          → hand off to existing V1 assigned-trip lifecycle
              ▼
RUN       driver-reached → OTP start (STARTED) → … → COMPLETED → pay/invoice   (all V1, unchanged)

EXPIRE/CANCEL paths per §5.4.
```

---

## 7. New Enums / Statuses / Error Codes

**Enums (new):** `ScheduledTripState` (§3.4). **Reused (no change):** `TripType.SCHEDULED`, `TripDispatchMode.BID`, `TripStatus.{AWAITING_BIDS,DRIVER_SELECTED,…}`, `BidStatus`, `BidType`.

**No new `TripStatus` integers** — C2C maps onto existing 15/16/2/8/6/9/10/12.

**New error codes** (extend the V2 catalog in the mobile guide §8.8):

| code | applies to | meaning |
|---|---|---|
| `C2C_DISABLED` | create | `SETTING_C2C_ENABLED != 1` |
| `INTERCITY_ROUTE_NOT_PRICED` | create | no route row and no centroid fallback to price the pair |
| `INVALID_CITY` | create | origin/destination city id unknown or `status=false` |
| `SAME_ORIGIN_DESTINATION` | create | origin == destination city |
| `DEPARTURE_TOO_SOON` | create | `scheduledDepartureAt` < now + `SETTING_C2C_MIN_LEAD_MINUTES` |
| `DEPARTURE_TOO_FAR` | create | beyond `SETTING_C2C_MAX_LEAD_DAYS` |
| `INVALID_SEATS` | create | seats < 1 or > `SETTING_C2C_MAX_SEATS` |
| `OFFER_OUT_OF_BAND` | create | `riderOfferedFare` ∉ `[min,max]` (reuse `BID_BELOW_FLOOR`/`BID_ABOVE_CEILING` shape) |
| `BIDDING_CLOSED` | place-bid/select | now > `biddingClosesAt` |
| `SCHEDULE_CANCEL_WINDOW_CLOSED` | cancel | inside no-free-cancel window → fee applies (informational) |
| `ACTIVATION_GUARD_FAILED` | internal/activation | rider conflict at activation (trip expired) |

Reused error codes from the bidding engine apply unchanged (`DRIVER_INELIGIBLE`, `CAB_TYPE_MISMATCH`, `PAYMENT_HOLD_FAILED`, `TRIP_NOT_OPEN`, etc.).

---

## 8. API Surface (additive; reuse bidding REST/socket where possible)

New api-gateway controller `@Controller('v2/schedule')` → rides TCP. Bidding interactions reuse the existing `/v2/trips/:tripId/bids*` and `v2/*` socket events unchanged.

| HTTP | Route | TCP | Actor | Purpose |
|---|---|---|---|---|
| POST | `/v2/schedule/city-to-city` | `V2_CREATE_SCHEDULED_TRIP` | Rider | create open scheduled C2C request |
| GET | `/v2/schedule/quote` | `V2_C2C_QUOTE` | Rider | fare band for `(origin,dest,cab,seats)` before creating |
| GET | `/v2/schedule/cities` | (master) | Rider/Driver | list selectable cities |
| GET | `/v2/schedule/open-in-route` | `V2_C2C_OPEN_IN_ROUTE` | Driver | open C2C requests on a route/window (REST fallback for broadcast) |
| GET | `/v2/schedule/my-upcoming` | `V2_C2C_MY_UPCOMING` | Rider/Driver | matched future trips |
| PATCH | `/v2/schedule/:tripId/cancel` | `V2_C2C_CANCEL` | Rider | cancel (window-aware) |
| (reuse) | `/v2/trips/:tripId/bids*`, `/raise-offer`, `v2/*` sockets | — | both | bidding lifecycle |

`broadcastNewTrip`/`open-trips-update` payloads gain optional C2C fields: `originCity`, `destinationCity`, `scheduledDepartureAt`, `seatsRequested`, `tripType:"SCHEDULED"`. Drivers add a **route+window display filter** on top of the existing range/cab filter.

---

## 9. Dependencies & Cross-Cutting

- **Category (sibling-owned):** `category_type = SCHEDULE` + `order` column on the category table. We classify the C2C cab offering under it and filter the driver-facing catalog by it; we do not define it. The SCHEDULE-category cab type id is what `cabId` references on the C2C trip and what `intercity_routes.cabTypeId`/the bid eligibility cab-type check use.
- **Payment:** hold-at-selection (reuse), re-hold-at-activation (new handling for stale pre-auths).
- **Cron:** re-enable the disabled `ScheduleTripService`; reuse the `setNxEx` distributed-lock pattern.
- **`expirePendingTrips()` guard (required):** exclude `tripType=SCHEDULED` rows from the 2-minute `open_rides` reaper.
- **`expireBids()` guard (required):** use the long C2C bid TTL for scheduled trips.

---

## 10. Open Decisions

- **OD-1** Canonical city source: extend master-service `geo-cities` (add centroids) and read via TCP, vs. a rides-local city read-model. _Lean: rides-local read-model synced from master, to avoid hot-path cross-service calls._
- **OD-2** Intercity pricing: dedicated `intercity_routes` table vs. pure centroid-distance formula. _Lean: ship the table + formula fallback._
- **OD-3** Per-bid `seatsOffered` on `trip_bids` (driver commits ≤ requested seats). _Lean: out of v1; driver takes all requested seats._
- **OD-4** C2C per-bid TTL value / "no expiry until biddingClosesAt". _Lean: bids live until `biddingClosesAt`._
- **OD-5** Stale-hold handling at activation (card pre-auth expiry). _Lean: re-authorize at activation; fail gracefully with `PAYMENT_HOLD_FAILED` and notify rider to re-confirm._
- **OD-6** Reuse `riderScheduledAt` vs. add distinct `scheduledDepartureAt`. _Lean: reuse `riderScheduledAt`, expose as `scheduledDepartureAt` in the API._

---

## 11. Note on the Shared Category-Table Change (NOT designed here)

A sibling agent owns the canonical `category_type` ENUM and the `order` column on the category table (`cab_type_category`, which today has `name`, `image`, `order: tinyint`, `status` — `driver-services/.../cab-type-category.entity.ts:17`). This design **depends on** `category_type = SCHEDULE` existing to classify the City-to-City offering and to filter the cab catalog. We do **not** add, rename, or renumber that ENUM or column. All references in this doc to "the SCHEDULE category" assume their design lands first.

---

## 12. Implementation Plan (ordered, minimal-diff) + Checklist

> Gated by `SETTING_C2C_ENABLED=0` until verified. Additive only; V1 + immediate-V2 untouched.

**Phase 0 — depend & guard (no behavior change)**
1. Confirm sibling `category_type = SCHEDULE` landed; identify the SCHEDULE cab type id(s).
2. `trips.service.ts` `expirePendingTrips()` — exclude `tripType=SCHEDULED` from the `open_rides` 2-min reaper. *(required guard)*
3. `expireBids()` — branch C2C bids onto `SETTING_C2C_BID_TTL_SECONDS`. *(required guard)*

**Phase 1 — data model**
4. `trips.enum.ts` — add `ScheduledTripState` (append-only).
5. `trips.entity.ts` — add C2C columns (§3.1) + indexes.
6. New `rides-service/src/modules/intercity/entities/intercity_route.entity.ts` (§3.3) + module/repo.
7. City reference per OD-1 (rides-local read-model or master-service centroid extension).
8. `open-ride.entity.ts` — add nullable `originCityId`, `destinationCityId`, `scheduledDepartureAt` (+ reuse bidding denorm columns).
9. Single additive migration: `ALTER trips`, `ALTER open_rides`, `CREATE intercity_routes`, indexes; seed `SETTING_C2C_*` Redis keys.

**Phase 2 — fare + create**
10. New `intercity` (or `c2c`) service: `quoteC2C()` (§4) with route-table + formula fallback.
11. rides `V2_CREATE_SCHEDULED_TRIP` handler: validate (lead time/seats/band/cities), insert trip+addresses+`open_rides`, `broadcastNewTrip` with C2C fields. Reuse `riderOfferedFare` band validation.
12. api-gateway `@Controller('v2/schedule')`: `city-to-city`, `quote`, `cities`, `open-in-route`, `my-upcoming`, `cancel` + DTOs + TCP wiring.

**Phase 3 — bidding reuse**
13. Ensure `place-bid`/`select-bid` accept scheduled trips (cab-type eligibility = SCHEDULE category; `BIDDING_CLOSED` guard at `biddingClosesAt`).
14. Confirm selection cascade sets `scheduledState=SCHEDULED_MATCHED` and holds payment.
15. Add C2C fields to `new-trip-request`/`open-trips-update` payloads (socket-gateway pass-through; no logic change).

**Phase 4 — scheduler**
16. Re-enable + extend `ScheduleTripService`: Job A `notifyScheduledC2CParticipants()` (reminder + close-unmatched), Job B `activateScheduledC2CTrips()` (guards + re-hold + OTP + hand-off). Wrap in `setNxEx` lock.
17. Cancellation window logic (`V2_C2C_CANCEL`) + fee policy reuse.

**Phase 5 — verify**
18. E2E: create → multi-driver bid → select (hold) → reminder fires → activation hands off to V1 → complete. Plus expiry (no bids), rider-conflict-at-activation, free vs. fee cancel, stale-hold re-auth.
19. Socket monitoring for `new-trip-request`/`open-trips-update`/`bid-*` on scheduled trips.

**Checklist**
- [ ] Sibling `category_type=SCHEDULE` + `order` confirmed (dependency).
- [ ] `expirePendingTrips()` excludes SCHEDULED rows. **(critical)**
- [ ] `expireBids()` uses C2C TTL for SCHEDULED bids. **(critical)**
- [ ] `ScheduledTripState` enum + trip columns + indexes (migration).
- [ ] `intercity_routes` + city centroid source.
- [ ] Fare quote (table + formula fallback).
- [ ] Create endpoint + validations + open_rides + broadcast with C2C fields.
- [ ] Bidding reuse verified (TTL, `BIDDING_CLOSED`, eligibility).
- [ ] Payment hold at selection; re-hold at activation.
- [ ] Scheduler re-enabled (reminder + activation) under distributed lock.
- [ ] Cancellation windows + fees.
- [ ] New error codes wired.
- [ ] `SETTING_C2C_ENABLED` flag-gated rollout + clean rollback.

**Files to touch (summary):**
- `rides-service`: `trips.enum.ts`, `trips/entities/trips.entity.ts`, `trip_address`/`open-ride.entity.ts`, new `modules/intercity/*`, `trips.service.ts` (`expirePendingTrips`, `expireBids`, create handler, scheduler fns), `cron/schedule-trip/schedule-trip.service.ts`, `trips.controller.ts` (new TCP patterns), migration script.
- `api-gateway`: new `modules/schedule/*` controller + DTOs + TCP client patterns.
- `socket-gateway`: pass-through of C2C fields in open-trip payloads (no logic change).
- `master-service` (if OD-1 = extend): `geo-cities` centroid columns.
- **Not touched:** the sibling-owned `cab_type_category` `category_type`/`order` change; all V1 and immediate-V2 bidding code.
