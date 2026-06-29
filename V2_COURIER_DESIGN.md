# V2 Courier (Parcel Delivery) — Design Document

> **Status:** Design / Documentation only — _no code is changed by this document._ Implementation begins after review.
> **Date:** 2026-06-18
> **Author:** Senior Backend Engineer / Architect
> **Scope:** **`cab_type_category`** + `cab_type` (driver-services), `rides-service` (bidding/trips), `api-gateway`, `socket-gateway` (payload pass-through only)
> **Branch context:** `ride-demo-v2`
> **Reuses:** `docs/V2_BIDDING_BROADCAST_DESIGN.md`, `docs/V2_MOBILE_INTEGRATION_GUIDE.md` (READ those first — Courier plugs into the SAME bidding engine; it does NOT reinvent dispatch, broadcast, fare-range, bid, or select flows).

---

## 0. TL;DR

Courier is **not a new dispatch engine**. It is the existing V2 inDrive-style bidding flow (`dispatchMode = BID`) with three additive layers bolted on:

1. **A `categoryType` discriminator + `order` column on `cab_type_category`** (the real user-facing "category" table — NOT master-service's permission `category`). `COURIER`, `SCHEDULE`, `RENT_A_CAR`, `DEFAULT`. Every category created picks one; downstream flow branches on it. **This is the canonical column owned by THIS doc; the SCHEDULE sibling design reuses the same column/enum — it is deliberately generic.**
2. **Courier parcel metadata on the trip** (sender/receiver phone, parcel type, weight, notes) carried through create → broadcast → bid → driver view.
3. **Per-cab-type weight/size limits** (`maxWeightKg`, `maxLengthCm`…) so a rider can't book a 100 kg parcel on a bike; validated at create-trip with a precise error. Fare range is nudged by parcel type/weight on top of the existing meter formula.

Everything after `select-bid` is unchanged — the trip becomes `ACCEPTED_BY_DRIVER (2)` and runs the V1 lifecycle (reached → OTP → complete → pay).

---

## 1. Current-State Analysis (verified, with file:line)

### 1.1 The two "category" tables — IMPORTANT disambiguation

There are **two** entities named "category". Only one is the user's category.

| Table | Entity | Purpose | Verdict |
|---|---|---|---|
| `category` (master-service) | `master-service/src/modules/category/category.entity.ts:7` | Admin **permission module** grouping (`moduleType` ENUM `TRANSPORTATION/CHAT/EWALLET`). Live rows: "Trips", "Riders", "Dashboard", "Accounting"… | **NOT the user's category. Do NOT touch.** |
| **`cab_type_category`** (driver-services) | `driver-services/src/modules/cab-type-category/entities/cab-type-category.entity.ts:7` | The **user-facing service category** a cab type belongs to. Live rows: **"Delivery Service", "Cargo Service", "Car Rental", "Derrbi Taxi"**. `cab_type.categoryId` FKs here. | **THIS is the category. All changes land here.** |

Confirmed against the live DB (`rideschema`):
- `cab_type_category` columns today: `id, createdAt, updatedAt, name, image, order (tinyint NOT NULL default 1), status (tinyint), isDeleted`. **It already has an `order` column** (so requirement 1b is partially satisfied at the DB layer — but `order` is **not** in the create/update DTOs, see §9.2).
- `cab_type.categoryId varchar(36)` (`MUL`) points to `cab_type_category.id`. Live distinct values map 1:1 to the four categories above.
- `cab_type` already has its own `order tinyint`, `noOfSeats tinyint`, and full passenger/share/carpool fare columns (`cab-type.entity.ts:12-139`). **No weight/size columns exist.**

### 1.2 Category read/write flow

- Driver/admin read: `GET_ALL_CAB_TYPE_CATEGORIES` → `CabTypeCategoryService.findAll()` (`cab-type-category.service.ts:56-70`) returns all rows `order: ASC`. Reaches the app via api-gateway `master.controller.ts` `GET /master/cab-type-category/all` and captain `GET /captains/cab-type-category/all`.
- Create: `CabTypeCategoryService.create()` (`cab-type-category.service.ts:27-54`) — **auto-increments `order`** from the max; spreads the DTO. Today the create DTO (`driver-services/.../dto/create-cab-type-category.dto.ts:9-23` and api-gateway `admin/dto/create-cab-type-category.dto.ts`) exposes only `name, image, status, order?`. **No category-type field.**
- Cab types read: `CabTypeService.findAll()` (`driver-services/.../cab-type.service.ts:84-247`) — returns cabs ordered by `order`, and when a location is supplied computes a per-cab fare estimate (`passengerBaseFare + costPerKm*distance + costPerMin*time + fees`).

### 1.3 V2 bidding engine (the flow Courier reuses verbatim)

- Create (BID): api-gateway `bidding.controller.ts` `POST /v2/trips` → `bidding.service.ts:32` adds `dispatchMode: 2` → TCP `V2_CREATE_TRIP` → rides `bidding.controller.ts:27` → `TripsService.createBidTrip()` → `create()` (`trips.service.ts:3175`).
- BID fare-range derivation: `trips.service.ts:3270-3300`. `recommendedFare = tripBaseAmount` (meter), `minFare = base*SETTING_BID_MIN_FACTOR (0.8)`, `maxFare = base*SETTING_BID_MAX_FACTOR (2.0)`, validates `min ≤ riderOfferedFare ≤ max`.
- Meter formula: `estimateFareAmount()` (`trips.service.ts:~6745`): `(baseFare + costPerKm*distance + costPerMin*time) * fareMultiplier`. Cab charges from `getCabChargeConfig(cabId,{country,city})` (`trips.service.ts:3234`).
- Driver-facing open-trip item: `buildOpenTripListItem()` (`trips.service.ts:3889-3927`) — `{tripId,cabId,riderId,pickup,dropoff,tripDistanceKm,pickupDistanceKm,etaToPickupSec,fareRange,riderOfferedFare,requestExpiresAt,createdAt}`; rider profile attached by `enrichOpenTripsWithRider()` (`:3930`). Used by `sendOpenTripsList()` (`:3950`) and `getOpenTripsInRangeForDriver()` (`:4994`).
- Bid place + gates: `placeBid()` (`trips.service.ts:4280`) — eligibility (`DRIVER_INELIGIBLE` `:4296`), cab match (`CAB_TYPE_MISMATCH` `:4330`), fare floor/ceiling (`BID_BELOW_FLOOR` `:4351` / `BID_ABOVE_CEILING`). Select: `riderAcceptBid()` (re-checks eligibility `:4675`, cab `:4699`).
- Trip entity V2 columns already exist (`trips.entity.ts:471-521`): `dispatchMode, riderOfferedFare, recommendedFare, minFare, maxFare, currency, selectedBidId, biddingEnabled, biddingStartedAt, bids[]`.
- `open_rides` already enriched (`open-ride.entity.ts:23-43`): `offeredFare, recommendedFare, dropoff_*, tripDistance, requestExpiresAt`.
- Migration template to mirror: `rides-service/config/migrations/1750000000000-V2Bidding.ts` (additive ALTERs, integer-backed enums `ENUM('1','2'…)`, clean `down()`).

### 1.4 Create-trip body DTO

`api-gateway/src/modules/trips/dto/trips.dto.ts` `TripsCreateDTO` (~:46-87): `addresses[], cabId, promoCode?, country?, city?, userId?, applePay*, paymentMethod, cardId?, ip?`. V2 adds `riderOfferedFare` + `dispatchMode` at the service layer (`api-gateway bidding.service.ts:32`). **No parcel fields.**

---

## 2. Canonical `categoryType` enum + `order` column (owned here; SCHEDULE reuses)

> **This section is the single source of truth for the category discriminator and ordering. The SCHEDULE design (sibling agent) MUST NOT redesign these — it only adds `SCHEDULE` handling that branches on the same `categoryType`. The enum and column are intentionally generic.**

### 2.1 The enum (new file)

`driver-services/src/modules/cab-type-category/cab-type-category.enum.ts`:

```ts
/**
 * Service-category discriminator. Every cab_type_category picks exactly one.
 * Downstream create-trip / bidding / app-rendering flow branches on this.
 * GENERIC: shared by Courier, Schedule, Rent-a-car. Append-only; never renumber.
 */
export enum CategoryType {
  DEFAULT = 1,     // normal point-to-point rides (today's "Derrbi Taxi")
  COURIER = 2,     // parcel / package delivery (inDrive Courier)  -- THIS DOC
  SCHEDULE = 3,    // scheduled / city-to-city  -- sibling design, reuses this enum
  RENT_A_CAR = 4,  // hourly / daily rental (today's "Car Rental")
  CARGO = 5,       // large freight (today's "Cargo Service") — optional mapping
}
```

> Why integer-backed: matches every existing enum in the repo (`TripDispatchMode`, `BidStatus`, `AdminModuleTypes`) and TypeORM `enum` columns stored as `ENUM('1','2',…)` (see `V2Bidding` migration). Append-only keeps it forward-compatible.

### 2.2 Column on `cab_type_category` (entity addition)

`cab-type-category.entity.ts`:

```ts
@Column({
  type: 'enum', enum: CategoryType, default: CategoryType.DEFAULT,
  comment: 'Service category discriminator; flow branches on this',
})
categoryType: CategoryType;
```

- `order` already exists in the entity (`:17`) and DB. **No new `order` column needed** — but it must be exposed in DTOs (§9.2) so admins can set it on create/update rather than relying only on auto-increment / the separate `updateOrder` endpoint.

### 2.3 Generic propagation (so SCHEDULE fits)

`categoryType` must be surfaced wherever the cab-type/category list is returned to the apps so each client can branch UI/flow:
- `CabTypeCategoryService.findAll()` already returns the whole entity → `categoryType` flows automatically once the column exists.
- The cab-type list (`CabTypeService.findAll`) should **join** the parent category's `categoryType` into each cab item (so the rider app, which picks a cab, knows whether it's a COURIER cab and must collect parcel info before `POST /v2/trips`).

### 2.4 Backfill (in the data migration)

Map existing live rows:
- "Delivery Service" → `COURIER`.
- "Cargo Service" → `CARGO` (or `COURIER` if product wants one parcel bucket — **product decision**, default `CARGO`).
- "Car Rental" → `RENT_A_CAR`.
- "Derrbi Taxi" → `DEFAULT`.

---

## 3. Courier types (parcel sub-types)

inDrive Courier and comparable apps expose a small fixed parcel taxonomy. Proposed enums (new, in rides-service since the trip stores them; mirrored as a constant the app reads):

`rides-service/src/modules/trips/enum/courier.enum.ts`:

```ts
/** Parcel content type a courier trip carries. Append-only. */
export enum ParcelType {
  DOCUMENTS   = 1,   // papers, envelopes — lightest
  FOOD        = 2,   // meals, groceries — keep-upright, time-sensitive
  ELECTRONICS = 3,   // phones/laptops — handle-with-care, high value
  FRAGILE     = 4,   // glass, ceramics — extra care surcharge
  CLOTHING    = 5,   // apparel, soft goods
  MEDICINE    = 6,   // pharmacy — time + care sensitive
  GROCERIES   = 7,   // supermarket bags
  FURNITURE   = 8,   // bulky — drives cab-type to van/cargo only
  OTHER       = 9,   // free-text in parcelNote
}

/** Optional rider-declared weight bucket (UI convenience; raw kg also stored). */
export enum ParcelWeightBucket {
  UPTO_1KG    = 1,   // documents / small
  UPTO_5KG    = 2,   // bag
  UPTO_20KG   = 3,   // box
  UPTO_50KG   = 4,   // heavy box / multiple
  OVER_50KG   = 5,   // freight — cargo cab only
}
```

The app shows the bucket chips; the server stores both the chosen `parcelType` and a numeric `parcelWeightKg` (free value or bucket midpoint). Fare and cab-limit logic key off `parcelWeightKg` (authoritative) and `parcelType` (surcharge tier).

---

## 4. Courier additional-info schema (sender/receiver/parcel)

inDrive Courier collects: sender phone, receiver phone, parcel type, weight, and notes; pickup = sender, dropoff = receiver (already the two `trip_addresses`).

**Decision: store in a dedicated 1:1 child table `trip_courier_details`, not as nullable columns on `trips`.** Rationale: keeps `trips` (already ~70 columns) clean; courier data is sparse; mirrors the existing child-table pattern (`trip_addresses`, `trip_drivers`, `trip_bids`). PII (phones) is isolated for easier redaction/retention handling.

`rides-service/src/modules/trips/entities/trip_courier_details.entity.ts`:

```ts
@Entity('trip_courier_details')
@Unique(['tripId'])
@Index(['tripId'])
export class TripCourierDetails extends AbstractEntity {
  @Column({ length: 36, comment: 'FK -> trips.id' }) tripId: string;
  @OneToOne(() => TripsEntity, (t) => t.courierDetails, { createForeignKeyConstraints: false })
  @JoinColumn({ name: 'tripId', referencedColumnName: 'id' }) trip: TripsEntity;

  @Column({ length: 20, comment: 'sender (pickup) phone E.164' }) senderPhone: string;
  @Column({ length: 120, nullable: true }) senderName: string;
  @Column({ length: 20, comment: 'receiver (dropoff) phone E.164' }) receiverPhone: string;
  @Column({ length: 120, nullable: true }) receiverName: string;

  @Column({ type: 'enum', enum: ParcelType, default: ParcelType.OTHER }) parcelType: ParcelType;
  @Column({ type: 'enum', enum: ParcelWeightBucket, nullable: true }) weightBucket: ParcelWeightBucket;
  @Column({ type: 'float', nullable: true, comment: 'declared weight kg (authoritative for limits/fare)' }) parcelWeightKg: number;

  @Column({ type: 'int', nullable: true, comment: 'optional dims cm' }) lengthCm: number;
  @Column({ type: 'int', nullable: true }) widthCm: number;
  @Column({ type: 'int', nullable: true }) heightCm: number;

  @Column({ type: 'varchar', length: 500, nullable: true }) parcelNote: string;     // "fragile, handle up"
  @Column({ default: false, comment: 'receiver pays cash on delivery (future)' }) codEnabled: boolean;
  @Column({ type: 'float', nullable: true }) declaredValue: number;                 // insurance/COD (future)
}
```

Add the inverse on `TripsEntity` (`trips.entity.ts`, near `bids`):

```ts
@OneToOne(() => TripCourierDetails, (c) => c.trip, { cascade: true, eager: false })
courierDetails: TripCourierDetails;
```

### 4.1 Flow of courier info

| Stage | Carries | Where |
|---|---|---|
| **create-trip** | rider sends `courier{ senderPhone, receiverPhone, parcelType, parcelWeightKg, weightBucket?, dims?, note? }` in the `POST /v2/trips` body | new `CourierDetailsDto` (§9.1) |
| **persist** | `create()` writes `trip_courier_details` (cascade from `trips`) when the trip's category is COURIER | `trips.service.ts` create path (after `tripsRepository.create` `:3210`) |
| **broadcast / open-trip item** | driver sees parcel summary so they can decide before bidding | extend `buildOpenTripListItem()` (`:3889`) with a `courier` block (§8.1) |
| **bid** | nothing new — driver bids on the fare as usual | `placeBid()` unchanged (cab-limit guard already passed at create) |
| **select / assign** | winner gets sender/receiver PII (mirrors how `bid-won` carries rider PII) | `riderAcceptBid()` fan-out + `v2/bid-won` |

**PII rule (mirror existing bidding guide):** the **open-trip broadcast** shows only a parcel **summary** (type, weight, note) — NOT sender/receiver phone numbers. Phones are released to the **winning driver only**, in `v2/bid-won` / `v2/bid-accepted` data, exactly like rider PII is released today (Mobile Guide §5.5/§6.4).

---

## 5. Fare model adjustments (parcel type & weight)

Reuse the existing meter + bid-range engine. Courier adds a **multiplicative parcel factor** applied to the recommended fare *before* the min/max band is derived, so the whole window scales with parcel difficulty. No new pricing engine.

```
recommended = (baseFare + costPerKm*distance + costPerMin*time) * fareMultiplier   // existing meter
courierFactor = weightFactor(parcelWeightKg) * typeFactor(parcelType)              // NEW, >= 1.0
recommendedCourier = recommended * courierFactor
min = recommendedCourier * SETTING_BID_MIN_FACTOR   (0.8)
max = recommendedCourier * SETTING_BID_MAX_FACTOR   (2.0)
riderOfferedFare must satisfy  min <= offered <= max
```

Factor tables (Redis-tunable `SETTING_COURIER_*`, with code defaults):

```
weightFactor:  <=5kg -> 1.00 | <=20kg -> 1.15 | <=50kg -> 1.35 | >50kg -> 1.60
typeFactor:    DOCUMENTS 1.00 | FOOD 1.05 | CLOTHING 1.05 | GROCERIES 1.05
               ELECTRONICS 1.15 | MEDICINE 1.10 | FRAGILE 1.20 | FURNITURE 1.25 | OTHER 1.00
```

- Optionally add a **flat courier handling fee** `SETTING_COURIER_BASE_FEE` (additive, post-factor) if product wants a fixed floor for tiny/cheap parcels (e.g. a 0.2 km document run shouldn't bid to ~SAR 2).
- Implementation: a new `applyCourierFareFactor(recommended, courierDetails)` helper called in the BID branch (`trips.service.ts:3278`) **only when** the trip's category is COURIER. AUTO/normal trips untouched.
- The factor is computed **once at create** and baked into `recommendedFare/min/max` (stored on `trips` + `open_rides`), so the bid window is stable — same principle as surge being baked at create (bidding design §6.6). Final fare = winning bid + existing fee pipeline (`taxAmount/motAmount/waslFee/transactionFee`); no double counting.

---

## 6. Per-cab-type weight/size limits + validation

### 6.1 Limits config (columns on `cab_type`)

Add nullable capacity columns to `cab_type` (NULL = "no limit / not a courier cab"):

```ts
// cab-type.entity.ts additions
@Column({ type: 'float', nullable: true, comment: 'max parcel weight kg this cab can carry; NULL = unlimited/NA' })
maxWeightKg: number;
@Column({ type: 'int', nullable: true, comment: 'max parcel longest side cm' })
maxLengthCm: number;
@Column({ type: 'int', nullable: true })
maxWidthCm: number;
@Column({ type: 'int', nullable: true })
maxHeightCm: number;
@Column({ type: 'int', nullable: true, comment: 'max combined dims (L+W+H) cm; optional alt to per-axis' })
maxDimSumCm: number;
```

Suggested seed values (admin-editable):

| Cab type (courier) | maxWeightKg | typical |
|---|---|---|
| Bike / Motorbike | 15 | documents, food, small box |
| Car (sedan) | 50 | boxes, groceries |
| Van / Pickup | 300 | furniture, bulk |
| Cargo truck | 1000+ | freight |

Expose in create/update cab-type DTOs (`create-cab-type.dto.ts`, api-gateway admin DTO) as optional `@IsNumber() @IsOptional()` fields.

### 6.2 Validation at create-trip (the gate the user asked for)

In the create-trip BID path, **after** resolving cab charges and **before** opening the bid window, when the trip's category is COURIER:

```
load cab = cab_type by trip.cabId  (already loaded for charges)
if cab.maxWeightKg != null && parcelWeightKg > cab.maxWeightKg:
    throw PARCEL_EXCEEDS_CAB_WEIGHT  { cabMaxKg, parcelKg }
if any dim limit set && parcel dim > limit:
    throw PARCEL_EXCEEDS_CAB_DIMENSIONS { axis, cabMax, parcel }
```

Implementation point: a new `validateCourierCabCapacity(cab, courier)` called in `create()` around `trips.service.ts:3273` (inside the BID branch, gated on COURIER category). Surfaced to the rider as a 400 from `POST /v2/trips` (same channel as the existing "offered fare below minimum" error at `:3290`).

### 6.3 Rider-app pre-filtering (UX, not a substitute for the server gate)

When the rider app fetches cab types for the Courier category, each cab item now carries `maxWeightKg`/dims (§2.3 join). The app **greys out / disables** cabs whose `maxWeightKg < parcelWeightKg` so the rider can't even pick an under-capacity vehicle. The server gate (§6.2) remains authoritative (handles stale clients / direct API callers).

---

## 7. New enums & error codes

### 7.1 Enums (all append-only, integer-backed)

| Enum | File | Values |
|---|---|---|
| `CategoryType` | `cab-type-category.enum.ts` (new) | DEFAULT=1, COURIER=2, SCHEDULE=3, RENT_A_CAR=4, CARGO=5 |
| `ParcelType` | `trips/enum/courier.enum.ts` (new) | DOCUMENTS=1 … OTHER=9 (§3) |
| `ParcelWeightBucket` | `trips/enum/courier.enum.ts` (new) | UPTO_1KG=1 … OVER_50KG=5 |

No changes to `TripStatus`, `BidStatus`, `BidType`, `TripDispatchMode` — courier reuses them as-is.

### 7.2 Error codes (added to the bidding error catalog)

Returned as `{ ok:false, error:{ code, details? } }` (socket ack) or 400 (REST), consistent with `placeBid` errors (`trips.service.ts:4296+`).

| code | applies to | meaning / details |
|---|---|---|
| `PARCEL_EXCEEDS_CAB_WEIGHT` | create-trip (courier) | `parcelWeightKg > cab.maxWeightKg`; `details:{ cabMaxKg, parcelKg }` |
| `PARCEL_EXCEEDS_CAB_DIMENSIONS` | create-trip (courier) | a parcel dimension exceeds the cab's; `details:{ axis, cabMax, parcel }` |
| `COURIER_DETAILS_REQUIRED` | create-trip (courier) | category is COURIER but `courier{}` block missing/incomplete (needs sender+receiver phone, parcelType, weight) |
| `COURIER_NOT_SUPPORTED_FOR_CAB` | create-trip | chosen cab's category is not COURIER but courier payload sent, or vice-versa (category/cab mismatch) |
| `INVALID_PARCEL_WEIGHT` | create-trip | weight <= 0 or non-numeric |

Reused unchanged for courier bids: `DRIVER_INELIGIBLE`, `CAB_TYPE_MISMATCH`, `BID_BELOW_FLOOR`, `BID_ABOVE_CEILING`, `TRIP_NOT_OPEN`, `PAYMENT_HOLD_FAILED`, etc.

---

## 8. Driver-facing open-trip item + rider bid flow additions

### 8.1 Open-trip item `courier` block (summary only — no phones)

Extend `buildOpenTripListItem()` (`trips.service.ts:3889`) to attach, when `dispatchMode=BID` and category is COURIER:

```jsonc
"courier": {
  "parcelType": 4,                 // FRAGILE
  "parcelTypeLabel": "Fragile",
  "parcelWeightKg": 18,
  "weightBucket": 3,               // UPTO_20KG
  "note": "Glass — keep upright",
  "dimensions": { "lengthCm": 40, "widthCm": 30, "heightCm": 30 }
  // NO senderPhone / receiverPhone here
}
```

This requires `findOpenTripsInRangeV2` / the open-rides query to LEFT JOIN `trip_courier_details` (or a second fetch keyed by tripId, mirroring `enrichOpenTripsWithRider`). Recommended: an `enrichOpenTripsWithCourier(items)` helper alongside the rider-enrichment one (`:3930`) to keep the SQL simple. Wire into `sendOpenTripsList` (`:3964`) and `getOpenTripsInRangeForDriver` (`:5012`).

### 8.2 `bid-won` / `bid-accepted` — release sender/receiver PII

In `riderAcceptBid()` fan-out, when courier, add to the winner's `v2/bid-won` payload (and `v2/bid-accepted` for the rider, who already knows the data):

```jsonc
"courier": {
  "parcelType": 4, "parcelWeightKg": 18, "note": "Glass — keep upright",
  "sender":   { "name": "Ali", "phone": "9665xxxxxxx" },   // released to winner only
  "receiver": { "name": "Sara", "phone": "9665yyyyyyy" }
}
```

This mirrors exactly how the rider's PII block is released at match today. No new socket events — same `v2/bid-won` / `v2/bid-accepted` envelopes, additive `courier` field.

### 8.3 End-to-end courier flow (create → bid → select → assign)

```
RIDER (Courier category UI)
  |- app fetches cab types for COURIER category (each carries maxWeightKg, categoryType)
  |- rider enters parcel: type, weight, sender/receiver phone, note
  |- app disables cabs whose maxWeightKg < weight  (UX gate)
  |- POST /v2/trips  { cabId, paymentMethod, riderOfferedFare,
  |                    addresses[pickup=sender, dropoff=receiver],
  |                    courier:{ senderPhone, receiverPhone, parcelType, parcelWeightKg, note } }
  |     api-gateway adds dispatchMode=2 -> TCP V2_CREATE_TRIP
  v
 rides create():
   |- resolve cab charges + meter recommended
   |- category = COURIER  =>  validateCourierCabCapacity(cab, courier)   [§6.2 -> PARCEL_EXCEEDS_* on fail]
   |- recommended *= courierFactor(weight,type)                          [§5]
   |- derive min/max, validate riderOfferedFare in band                 [existing :3290]
   |- persist trip (status=AWAITING_BIDS) + trip_courier_details (cascade)
   |- open_rides row + broadcastNewTrip()                               [existing V2]
  v
 ALL DRIVERS get new-trip-request / open-trips-list — item now has a `courier` SUMMARY (no phones)  [§8.1]
  v
 driver places bid  (v2/place-bid)  — unchanged; CAB_TYPE_MISMATCH already impossible (cab is a courier cab)
  v
 rider sees competing bids (v2/trip-bids-update) — unchanged
  v
 rider selects (v2/select-bid) -> riderAcceptBid()
   |- existing assignment cascade (payment hold, siblings rejected, open_rides removed)
   |- v2/bid-won to winner WITH sender/receiver phones  [§8.2];  v2/bid-accepted to rider
  v
 trip = ACCEPTED_BY_DRIVER (2) -> existing V1 lifecycle (reached -> OTP -> complete -> pay)
```

---

## 9. DTO changes

### 9.1 Create-trip (api-gateway `trips.dto.ts` `TripsCreateDTO`, and rides `TripsCreateDTO`)

Add an optional nested block:

```ts
export class CourierDetailsDto {
  @IsString() @IsNotEmpty() senderPhone: string;
  @IsString() @IsOptional() senderName?: string;
  @IsString() @IsNotEmpty() receiverPhone: string;
  @IsString() @IsOptional() receiverName?: string;
  @IsEnum(ParcelType) parcelType: ParcelType;
  @IsNumber() @IsPositive() parcelWeightKg: number;
  @IsEnum(ParcelWeightBucket) @IsOptional() weightBucket?: ParcelWeightBucket;
  @IsInt() @IsOptional() lengthCm?: number;
  @IsInt() @IsOptional() widthCm?: number;
  @IsInt() @IsOptional() heightCm?: number;
  @IsString() @IsOptional() @MaxLength(500) parcelNote?: string;
}
// on TripsCreateDTO:
@ValidateNested() @Type(() => CourierDetailsDto) @IsOptional()
courier?: CourierDetailsDto;
```

### 9.2 Category DTOs (driver-services + api-gateway admin)

Add to `CreateCabTypeCategoryDto` / `UpdateCabTypeCategoryDto`:

```ts
@IsEnum(CategoryType) @IsOptional() categoryType?: CategoryType;   // defaults DEFAULT
@IsNumber() @IsOptional() order?: number;                          // already optional in driver-svc; add to api-gw if missing
```

### 9.3 Cab-type DTOs (driver-services + api-gateway admin)

Add optional `maxWeightKg`, `maxLengthCm`, `maxWidthCm`, `maxHeightCm`, `maxDimSumCm` (all `@IsNumber() @IsOptional()`).

---

## 10. Migration + code-change plan (ordered, minimal-diff)

> Two services own schema here: **driver-services** owns `cab_type` + `cab_type_category`; **rides-service** owns `trips` + the new `trip_courier_details`. Both point at the same `rideschema` DB. Follow the existing additive-migration discipline (`V2Bidding` template, `synchronize:false`, `migrationsRun:false`).

### Phase A — schema (migrations)

1. **driver-services migration** `*-CourierCategoryAndCabLimits.ts`:
   - `ALTER TABLE cab_type_category ADD COLUMN categoryType ENUM('1','2','3','4','5') NOT NULL DEFAULT '1' COMMENT '…'`.
   - `ALTER TABLE cab_type ADD COLUMN maxWeightKg FLOAT NULL, ADD maxLengthCm INT NULL, ADD maxWidthCm INT NULL, ADD maxHeightCm INT NULL, ADD maxDimSumCm INT NULL`.
   - **Data backfill** UPDATEs mapping the four live categories (§2.4) + seed `maxWeightKg` for known courier cabs.
   - Clean `down()` dropping all six columns.
2. **rides-service migration** `*-CourierTripDetails.ts`:
   - `CREATE TABLE trip_courier_details (…)` per §4 (uuid PK, `tripId` unique, enums as `ENUM('1'…)`, index on `tripId`).
   - Clean `down()` = `DROP TABLE trip_courier_details`.

### Phase B — driver-services (category + cab limits)

3. `cab-type-category.enum.ts` (new) — `CategoryType`.
4. `cab-type-category.entity.ts` — add `categoryType` column.
5. `cab-type.entity.ts` — add the 5 capacity columns.
6. DTOs: `create-cab-type-category.dto.ts`, `update-cab-type-category.dto.ts`, `create-cab-type.dto.ts`, `update-cab-type.dto.ts` — add fields (§9.2/§9.3).
7. `cab-type.service.ts findAll()` — join parent `categoryType` into each cab item; include capacity columns in the returned shape.
8. api-gateway admin DTOs (`create-cab-type-category.dto.ts`, `category.dto.ts`, `create-cab-type.dto.ts`, `update-cab-type.dto.ts`) — mirror fields so the admin dashboard can set them.

### Phase C — rides-service (courier trip data + fare + validation)

9. `trips/enum/courier.enum.ts` (new) — `ParcelType`, `ParcelWeightBucket`.
10. `trips/entities/trip_courier_details.entity.ts` (new); register in the trips TypeORM module entity list.
11. `trips.entity.ts` — add `@OneToOne courierDetails` inverse.
12. `TripsCreateDTO` (rides + api-gateway) — add `CourierDetailsDto` (§9.1).
13. `trips.service.ts create()` (BID branch ~:3270):
    - resolve trip category from `cab_type.categoryId -> cab_type_category.categoryType` (one lookup; cache).
    - if COURIER: require `courier{}` (`COURIER_DETAILS_REQUIRED`), `validateCourierCabCapacity()` (`PARCEL_EXCEEDS_*`), apply `applyCourierFareFactor()` to `recommendedFare` before deriving min/max, then persist `trip_courier_details` (cascade).
14. `trips.service.ts` — new helpers: `getCategoryTypeForCab(cabId)`, `validateCourierCabCapacity(cab, courier)`, `applyCourierFareFactor(recommended, courier)` (Redis `SETTING_COURIER_*` with §5 defaults).
15. `buildOpenTripListItem()` (`:3889`) + `enrichOpenTripsWithCourier()` (new, mirror `enrichOpenTripsWithRider` `:3930`) — attach the `courier` **summary** block (§8.1) for courier trips. Wire into `sendOpenTripsList` (`:3964`) and `getOpenTripsInRangeForDriver` (`:5012`).
16. `riderAcceptBid()` fan-out — add the `courier` block **with phones** to `v2/bid-won` / `v2/bid-accepted` (§8.2).
17. Error catalog — register new codes (§7.2) wherever the existing bidding errors are returned.

### Phase D — config / docs / tests

18. Redis settings: `SETTING_COURIER_WEIGHT_FACTORS`, `SETTING_COURIER_TYPE_FACTORS`, `SETTING_COURIER_BASE_FEE` (optional). Defaults shipped in code.
19. Update `docs/V2_MOBILE_INTEGRATION_GUIDE.md` with the courier create-trip body, the open-trip `courier` summary, and the `bid-won` PII release (separate doc task).
20. Postman: extend `Ride-Captain-CabTypeCategory` / cab-type collections with `categoryType` + capacity fields; add a Courier create-trip example.

---

## 11. Coordination note for the SCHEDULE sibling design

- **`categoryType` enum + the `cab_type_category` column are defined HERE (§2) and are shared.** SCHEDULE = `CategoryType.SCHEDULE (3)`. The sibling doc must reference this section and only add `SCHEDULE`-specific create-trip branching (e.g. `riderScheduledAt`, city-to-city addresses) keyed off the same `categoryType` — it must NOT re-add or renumber the enum, nor re-add the column/migration. The driver-services migration in §10 Phase A is the **single** migration that introduces `categoryType`; SCHEDULE adds its own trip-side tables only.
- The generic propagation (§2.3) and the cab-type->category-type join (Phase B.7) serve all categories, including SCHEDULE.

---

## 12. Implementation checklist

- [ ] **Schema:** driver-services migration — `cab_type_category.categoryType`, `cab_type` capacity cols, backfill + seed.
- [ ] **Schema:** rides-service migration — `trip_courier_details` table.
- [ ] `CategoryType` enum (driver-services) + `ParcelType`/`ParcelWeightBucket` (rides-service).
- [ ] Entities: `cab_type_category` (+categoryType), `cab_type` (+limits), `trips` (+courierDetails inverse), `trip_courier_details` (new) — register in module entity arrays.
- [ ] DTOs: create/update cab-type-category (+categoryType, +order), create/update cab-type (+limits), create-trip (+CourierDetailsDto) — in **both** driver-services/rides-service and api-gateway.
- [ ] `cab-type.service.findAll` joins `categoryType` + returns limits.
- [ ] create-trip: category resolution, `COURIER_DETAILS_REQUIRED`, `validateCourierCabCapacity` (`PARCEL_EXCEEDS_*`), `applyCourierFareFactor`, persist courier child.
- [ ] open-trip item: `courier` summary (no phones) + enrich helper, wired into list/range builders.
- [ ] `bid-won`/`bid-accepted`: release sender/receiver PII to winner.
- [ ] Error codes registered (§7.2).
- [ ] Redis `SETTING_COURIER_*` with code defaults.
- [ ] Mobile guide + Postman updated.
- [ ] E2E: bike-vs-100kg rejected at create; valid courier trip -> broadcast carries summary -> driver bids -> rider selects -> winner gets phones -> V1 lifecycle.

---

## 13. Open decisions (need product sign-off)

1. **Cargo vs Courier buckets:** keep "Cargo Service" as a separate `CARGO` category, or fold heavy parcels into `COURIER` with a cargo-capable cab? (Default: separate.)
2. **Flat courier base fee** (`SETTING_COURIER_BASE_FEE`) — yes/no, and amount.
3. **COD / declared value / insurance** — modeled as columns now (nullable, future-use) but out of scope for v1 flow.
4. **Weight source of truth** — rider self-declares; do we let the driver dispute/adjust weight at pickup (re-price)? Out of scope for v1 (re-pricing needs a post-acceptance fare-edit flow).
5. **Receiver-side OTP / proof of delivery** — courier often needs a delivery OTP distinct from the start OTP. Out of scope for v1 (reuses the single trip OTP); flag for v2.
