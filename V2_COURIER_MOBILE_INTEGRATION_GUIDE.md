# V2 Courier (Parcel Delivery) — Mobile Integration Guide

> **Audience:** mobile (rider + driver) developers building the Courier experience.
> **Status:** matches the implemented + E2E-tested code on branch `ride-demo-v2` (Flow B in `docs/V2_E2E_TEST_REPORT.md`, 19/19 PASS — re-verified live 2026-06-19).
> **Transport:** WebSocket (Socket.IO) for real-time + REST (api-gateway `/v2/trips`) as a fallback for every action.
> **Base URLs (demo):** REST `http://<host>:3010`  •  WebSocket `http://<host>:8100`
> **Auth:** every REST call sends the login JWT in the **`sessionid`** header (NOT `Authorization`). Sockets identify the user with `subscribe-user`.

> **↑ Entry point:** start at `docs/V2_MOBILE_MASTER_GUIDE.md` (all three modes + the shared house response/error contract). **Read the base guide first:** `docs/V2_MOBILE_INTEGRATION_GUIDE.md`.
> Courier is **NOT a new dispatch engine** — it is the exact same V2 broadcast + inDrive-style bidding flow (`dispatchMode = BID`) with three additive layers: parcel metadata on the trip, per-cab-type weight/size limits, and a separate **delivery OTP** at drop-off. Everything about login, sockets, the bid lifecycle, fare-range math, `place-bid` / `select-bid`, `PAYMENT_HOLD_FAILED` recovery, and the post-match V1 lifecycle is identical to the base guide. **This document fully specifies only the COURIER-specific additions and references the base guide for the shared bits.**

All sample values below are **real shapes captured from the running system** (Flow B harness, `scratchpad/flowB_events.json`) — you can copy them as fixtures.

---

## 0. TL;DR — the whole courier flow

1. Rider opens the **Courier category** (a `cab_type_category` whose `categoryType = COURIER (2)`). The app fetches courier cab types (each carries `maxWeightKg` + `categoryType`) and greys out cabs that can't carry the parcel.
2. Rider enters **sender + receiver** (phone, optional name) and **parcel info** (type, weight, optional dimensions/note). Pickup = sender, dropoff = receiver.
3. Rider **`POST /v2/trips`** with a courier cab + a `courier{}` block + a `riderOfferedFare`. The server validates the parcel against the cab's limits, applies a **weight × type fare factor**, derives the bid band, and opens the trip (`status 15 AWAITING_BIDS`).
4. Server **broadcasts `new-trip-request`** to courier drivers. The open-trip item carries a parcel **SUMMARY** (type, weight, note, dims) — **NO sender/receiver phone numbers**.
5. Drivers **bid** (`v2/place-bid` — accept the fare or counter) exactly as in the base guide.
6. Rider watches **`v2/trip-bids-update`** (cheapest-first) and **selects** one (`v2/select-bid`).
7. The **winning driver** gets `v2/bid-won` and the rider gets `v2/bid-accepted` — both now additionally carry the **sender & receiver `{name, phone}`** AND the **`deliveryOtp`**.
8. Payment is held; the trip becomes `ACCEPTED_BY_DRIVER (2)` and runs the **normal V1 lifecycle** (driver-reached → start OTP → … ).
9. At drop-off the driver **completes the trip carrying the `deliveryOtp`** (the receiver gives it to the driver). Wrong/missing OTP → `INVALID_DELIVERY_OTP`; correct OTP → trip completes (`status 8`).

> The rider sees the `deliveryOtp` in `v2/bid-accepted`. The rider relays it to the **receiver** (out of band), who hands it to the driver at delivery. This is a **second, separate OTP** — distinct from the `tripOtp` used to *start* the trip at pickup.

---

## 1. What's different from a normal V2 ride

| Aspect | Normal V2 ride | V2 Courier |
|---|---|---|
| Parcel info | none | rider sends a `courier{}` block (sender/receiver phone+name, parcel type, weight, dims, note); persisted in `trip_courier_details` |
| Cab types | passenger cabs (seats) | **courier cabs** with `maxWeightKg` + dimension limits; app greys out under-capacity cabs |
| Fare | `meter × fareMultiplier` | `meter × weightFactor(kg) × typeFactor(parcelType)`, then the same min/max band |
| PII at broadcast | rider profile shown | rider profile shown **+ parcel SUMMARY** (NO sender/receiver phones) |
| PII at match | driver/rider profiles | profiles **+ sender & receiver `{name, phone}`** released to the **winner only** |
| OTPs | one `tripOtp` (start at pickup) | `tripOtp` (start at pickup) **+ a separate `deliveryOtp`** required to **complete** at drop-off |
| Addresses | pickup → dropoff | pickup = **sender**, dropoff = **receiver** |

Everything else — broadcast, `open-trips-list`/`new-trip-request`/`open-trips-update`, bid TTL (45s), `select-bid`, `bid-lost`/`bid-expired`, payment hold, error envelope for bids, the V1 post-match lifecycle — is **identical** to `docs/V2_MOBILE_INTEGRATION_GUIDE.md`. Reuse that code unchanged.

---

## 2. Enums

### 2.1 `CategoryType` — the service-category discriminator (on `cab_type_category`)

Every category (and, joined through it, every cab type) carries this. The rider app branches its UI/flow on it (a COURIER category must collect parcel info before create-trip).

```
CategoryType:  1 = DEFAULT       // normal point-to-point ("Derrbi Taxi")
               2 = COURIER       // parcel / package delivery ("Delivery Service")  <-- THIS GUIDE
               3 = SCHEDULE      // scheduled / city-to-city ("City to City")
               4 = RENT_A_CAR    // hourly / daily rental ("Car Rental")
               5 = CARGO         // large freight ("Cargo Service")
```

> **Courier covers BOTH `COURIER (2)` and `CARGO (5)`** server-side — the create-trip flow treats either as a "courier trip" (parcel block required, capacity gate, delivery OTP). Live categories in `cab_type_category`: `Derrbi Taxi=1`, `Delivery Service=2`, `City to City=3`, `Car Rental=4`, `Cargo Service=5`.

### 2.2 `ParcelType` — parcel content type (with fare `typeFactor`)

Stored on `trip_courier_details.parcelType`. Each value carries a multiplicative fare factor (§5).

| Value | Enum | Label | typeFactor |
|---|---|---|---|
| 1 | DOCUMENTS | Documents | 1.00 |
| 2 | FOOD | Food | 1.05 |
| 3 | ELECTRONICS | Electronics | 1.15 |
| 4 | FRAGILE | Fragile | 1.20 |
| 5 | CLOTHING | Clothing | 1.05 |
| 6 | MEDICINE | Medicine | 1.10 |
| 7 | GROCERIES | Groceries | 1.05 |
| 8 | FURNITURE | Furniture | 1.25 |
| 9 | OTHER | Other | 1.00 |

`parcelTypeLabel` in the payloads is derived from this table (e.g. `4 → "Fragile"`).

### 2.3 `ParcelWeightBucket` — optional UI weight chip

A convenience the app may send alongside the authoritative `parcelWeightKg`. **Optional** — limits & fare key off `parcelWeightKg`, never the bucket.

```
ParcelWeightBucket:  1 = UPTO_1KG   // documents / small
                     2 = UPTO_5KG   // bag
                     3 = UPTO_20KG  // box
                     4 = UPTO_50KG  // heavy box / multiple
                     5 = OVER_50KG  // freight — cargo cab only
```

### 2.4 Reused enums (see base guide §2)

`bidType` (1 ACCEPT_FARE, 2 COUNTER), `BidStatus` (1–7), `TripStatus` additions (15 AWAITING_BIDS, 16 DRIVER_SELECTED; → 2 ACCEPTED_BY_DRIVER → V1 lifecycle), `dispatchMode` (1 AUTO, 2 BID), `paymentMethod` (1 card, 2 wallet), `addressType` (1 pickup, 2 destination). **Courier does not add or change any of these.** The server→client EVENT envelope is unchanged (`{ v, event, emittedAt, data }`). REST responses and client→server socket **acks** use the house pattern: success `{ statusCode, data }`, error `{ statusCode, message, data:{ code, ...details } }`.

---

## 3. Courier cab types + capacity limits

Courier cabs live under the COURIER category and carry capacity columns on `cab_type`. **Real, live values from `rideschema`:**

| Cab type | cabId | `maxWeightKg` | dims (L/W/H cm) | Arabic |
|---|---|---|---|---|
| Courier Bike | `c0021e7e-0000-4000-8000-000000000001` | 20 | not set (NULL) | دراجة توصيل |
| Courier Car  | `c0021e7e-0000-4000-8000-000000000002` | 50 | not set (NULL) | سيارة توصيل |
| Courier Van  | `c0021e7e-0000-4000-8000-000000000003` | 300 | not set (NULL) | فان توصيل |
| Cargo Truck  | `c0021e7e-0000-4000-8000-000000000004` | 1000 | not set (NULL) | شاحنة شحن |

> **Note on dimensions:** the design references per-side limits (e.g. 120 cm for the bike). In the current live DB those dimension columns (`maxLengthCm`/`maxWidthCm`/`maxHeightCm`/`maxDimSumCm`) are **NULL = no limit**, so today only the **weight** gate fires. The dimension gate is fully implemented and will activate as soon as an admin sets those columns — so build your UI to honour whatever the cab item returns (weight and/or dims), not hard-coded numbers.

### 3.1 How the app fetches cab types + limits

The rider app fetches the cab list it already uses, and each cab item now carries `categoryType` plus the capacity columns:

- **Categories:** `GET /master/cab-type-category/all` (admin) or `GET /captains/cab-type-category/all` — each category row includes `categoryType`. Pick the row with `categoryType === 2` (COURIER) — and, if you want freight in the same picker, `=== 5` (CARGO).
- **Cab types:** `GET /master/cab-type/all` — the non-admin response spreads the full cab row, so each cab item includes `categoryType`, `maxWeightKg`, `maxLengthCm`, `maxWidthCm`, `maxHeightCm`, `maxDimSumCm` (plus the usual `baseFare`, `costPerKm`, `costPerMin`, etc.). Filter to the courier `categoryId` / `categoryType`.

### 3.2 Grey out under-capacity cabs (UX gate)

Once the rider has entered `parcelWeightKg` (and optionally dimensions), **disable / grey out** any courier cab where:

```
cab.maxWeightKg != null && parcelWeightKg > cab.maxWeightKg
   // or, when set:
cab.maxLengthCm != null && lengthCm > cab.maxLengthCm   (same for width/height)
cab.maxDimSumCm != null && (lengthCm+widthCm+heightCm) > cab.maxDimSumCm
```

This is **UX only**. The server runs the same gate authoritatively at create-trip (§6) — a stale client or a direct API caller is still rejected.

---

## 4. Courier `courier{}` block — fields

The rider sends this nested object in the create-trip body. Persisted 1:1 in `trip_courier_details`.

| Field | Type | Required | Notes |
|---|---|---|---|
| `senderPhone` | string | **yes** | E.164, e.g. `"966500000011"`. Released to winner only. |
| `senderName` | string | no | e.g. `"Ali"` |
| `receiverPhone` | string | **yes** | E.164, e.g. `"966500000022"`. Released to winner only. |
| `receiverName` | string | no | e.g. `"Sara"` |
| `parcelType` | `ParcelType` (1–9) | **yes** | drives `typeFactor` + label |
| `parcelWeightKg` | number (> 0) | **yes** | authoritative for limits & fare |
| `weightBucket` | `ParcelWeightBucket` (1–5) | no | UI convenience only |
| `lengthCm` / `widthCm` / `heightCm` | int | no | checked against cab dims if both sides set |
| `parcelNote` | string (≤ 500) | no | e.g. `"Glass - keep upright"` |

`COURIER_DETAILS_REQUIRED` fires if `senderPhone`, `receiverPhone`, `parcelType`, or `parcelWeightKg` is missing on a courier cab (see §6 for the validation rules and the exact error envelope).

---

## 5. Fare model (parcel weight × type factor)

Courier reuses the existing meter + bid-band engine and inserts one multiplicative **courier factor** before the band is derived, so the *whole* window scales with parcel difficulty:

```
recommended  = (baseFare + costPerKm*distance + costPerMin*time) * fareMultiplier   // existing meter
courierFactor = weightFactor(parcelWeightKg) * typeFactor(parcelType)               // >= 1.0
recommended  = recommended * courierFactor
min = recommended * SETTING_BID_MIN_FACTOR   (0.80)
max = recommended * SETTING_BID_MAX_FACTOR   (2.00)
//  riderOfferedFare must satisfy  min <= riderOfferedFare <= max
```

**Weight factor (`courierWeightFactor(kg)`):**

| Weight | factor |
|---|---|
| ≤ 5 kg | 1.00 |
| ≤ 20 kg | 1.15 |
| ≤ 50 kg | 1.35 |
| > 50 kg | 1.60 |

**Type factor (`typeFactor`):** see the table in §2.2 (DOCUMENTS 1.00 … FURNITURE 1.25).

The factor is computed **once at create** and baked into the stored `recommendedFare / minFare / maxFare`, so the bid window is stable for the trip's lifetime. The final fare is the **winning bid amount** plus the existing fee pipeline (tax/MOT/WASL/transaction fee) — the factor is not re-applied later, so there is no double counting.

**Worked example (real, Courier Van, 80 kg, FRAGILE):** a bare Van meter over this ~12.5 km trip is ~SAR 46–60. With `weightFactor(80kg)=1.60 × typeFactor(FRAGILE)=1.20 = 1.92`, the recommended lifts to **200.89** → band **min 160.71 / max 401.78**. An offered fare of 90 is correctly rejected as below the 160.71 floor (the factor lifted the whole band).

**Band validation of `riderOfferedFare`:** identical to a normal V2 ride. If the offer is outside `[minFare, maxFare]`, create-trip returns a real HTTP 400 with the house error body (§6.4):
```jsonc
{ "statusCode": 400, "message": "Offered fare 90 is below the minimum 160.71 SAR", "data": { "code": "OFFER_OUT_OF_BAND", "minFare": 160.71, "submitted": 90 } }
{ "statusCode": 400, "message": "Offered fare 500 is above the maximum 401.78 SAR", "data": { "code": "OFFER_OUT_OF_BAND", "maxFare": 401.78, "submitted": 500 } }
```
Show the rider `recommendedFare / minFare / maxFare` from the create response (or compute client-side) so they pick a sensible offer.

---

## 6. Create the courier trip — `POST /v2/trips`

Same endpoint as a normal V2 ride; just send a courier `cabId` + the `courier{}` block. `addresses[0]` (pickup) is the **sender** location, `addresses[1]` (destination) is the **receiver** location.

### 6.1 Request — full courier body

```jsonc
// headers: { "sessionid": "<rider token>", "Content-Type": "application/json" }
{
  "cabId": "c0021e7e-0000-4000-8000-000000000003",   // a COURIER/CARGO cab (here: Courier Van)
  "paymentMethod": 2,                                  // 1=card, 2=wallet
  "tripType": 1,
  "riderOfferedFare": 200,                             // must land in [minFare, maxFare]
  "addresses": [
    { "addressType": 1, "latitude": 31.4595, "longitude": 74.2765, "address": "Sender" },
    { "addressType": 2, "latitude": 31.5200, "longitude": 74.3500, "address": "Receiver" }
  ],
  "courier": {
    "senderPhone": "966500000011",
    "senderName": "Ali",
    "receiverPhone": "966500000022",
    "receiverName": "Sara",
    "parcelType": 4,                                   // FRAGILE
    "parcelWeightKg": 80,
    "weightBucket": null,                              // optional
    "lengthCm": null, "widthCm": null, "heightCm": null,  // optional
    "parcelNote": "Glass - keep upright"
  }
}
```

### 6.2 Success — HTTP `201`, house `{ statusCode, data }`

```jsonc
{
  "statusCode": 201,
  "data": {
    "id": "951902b3-b8cd-48ae-a95e-3e1b06962a1e",
    "message": "Trip added successfully",
    "tripRequestTimeLimit": "2026-06-19 06:09:50",
    "status": 15,                       // AWAITING_BIDS
    "riderOfferedFare": 200,
    "recommendedFare": 200.89,          // meter * weight(1.60) * type(1.20)
    "minFare": 160.71,
    "maxFare": 401.78,
    "currency": "SAR"
  }
}
```

The trip is now open for bids and broadcast to courier drivers.

### 6.3 Validation order at create (courier cab)

When the chosen cab's category is COURIER/CARGO, the server runs (before opening the bid window):

1. **`courier{}` complete?** sender+receiver phone + `parcelType` + `parcelWeightKg` present → else `COURIER_DETAILS_REQUIRED`.
2. **Weight numeric & > 0?** → else `INVALID_PARCEL_WEIGHT`.
3. **Capacity gate** (`validateCourierCabCapacity`): `parcelWeightKg > cab.maxWeightKg` → `PARCEL_EXCEEDS_CAB_WEIGHT`; a parcel dimension (or the L+W+H sum) over a set cab limit → `PARCEL_EXCEEDS_CAB_DIMENSIONS`. NULL cab limits = no limit.
4. **Fare factor** applied → band derived → `riderOfferedFare` band check (the "below the minimum / above the maximum" message).

A `courier{}` block sent on a **non-courier** cab → `COURIER_NOT_SUPPORTED_FOR_CAB`.

### 6.4 The error body: real HTTP 4xx + house `{ statusCode, message, data:{ code, ...details } }`

Courier create-trip business/validation errors come back as a **real HTTP 4xx** (400 for validation) whose body is the house error shape `{ statusCode, message, data:{ code, ...details } }`. The HTTP status IS reliable; the human-readable string is the top-level `message`; the stable machine `code` plus any structured detail fields are **flattened into `data`** (not nested under a `details` key). Real captured shapes:

```jsonc
// HTTP 400 — 100 kg parcel on Courier Bike (max 20):
{ "statusCode": 400, "message": "PARCEL_EXCEEDS_CAB_WEIGHT", "data": { "code": "PARCEL_EXCEEDS_CAB_WEIGHT", "cabMaxKg": 20, "parcelKg": 100 } }

// HTTP 400 — courier cab but no courier{} block:
{ "statusCode": 400, "message": "COURIER_DETAILS_REQUIRED", "data": { "code": "COURIER_DETAILS_REQUIRED" } }

// HTTP 400 — dimension over a (set) cab limit:
{ "statusCode": 400, "message": "PARCEL_EXCEEDS_CAB_DIMENSIONS", "data": { "code": "PARCEL_EXCEEDS_CAB_DIMENSIONS", "axis": "length", "cabMax": 120, "parcel": 150 } }

// HTTP 400 — weight <= 0 / non-numeric:
{ "statusCode": 400, "message": "INVALID_PARCEL_WEIGHT", "data": { "code": "INVALID_PARCEL_WEIGHT" } }

// HTTP 400 — courier payload on a non-courier cab:
{ "statusCode": 400, "message": "COURIER_NOT_SUPPORTED_FOR_CAB", "data": { "code": "COURIER_NOT_SUPPORTED_FOR_CAB" } }

// HTTP 400 — offer outside the band:
{ "statusCode": 400, "message": "Offered fare 90 is below the minimum 160.71 SAR", "data": { "code": "OFFER_OUT_OF_BAND", "minFare": 160.71, "submitted": 90 } }
```

**Parsing is trivial** — read the flattened `data`:

```js
function parseCreateError(resp) {
  if (resp?.statusCode >= 200 && resp?.statusCode < 300) return null;  // success (201)
  return resp.data;                                    // { code, ...details } ; human text in resp.message
}
// PARCEL_EXCEEDS_CAB_WEIGHT     -> data: { code, cabMaxKg, parcelKg }
// PARCEL_EXCEEDS_CAB_DIMENSIONS -> data: { code, axis: "length"|"width"|"height"|"sum", cabMax, parcel }
// OFFER_OUT_OF_BAND             -> data: { code, minFare?|maxFare?, submitted } ; human string in resp.message
```

> The **socket** ack path and the **REST create-trip** path use the SAME house shape: success `{ statusCode, data }`, error `{ statusCode, message, data:{ code, ...details } }` (base guide §5.3). The old 201-wrapped-400 create quirk has been removed — create errors are real HTTP 4xx.

---

## 7. Driver-facing open-trip item — `courier` SUMMARY (no phones)

When a courier trip is broadcast/listed, the open-trip item carries an extra **`courier` summary** alongside the usual `rider` block. It contains parcel type/label/weight/note/dimensions — and **never** the sender/receiver phone numbers (verified in Flow B: the broadcast string contains no phone). This is the shape on **`new-trip-request`**, in **`open-trips-list`**, in the `added` items of **`open-trips-update`**, in the **`v2/sync-open-trips`** ack, and in **`GET /v2/trips/open-in-range`**.

**Real `new-trip-request` (server → driver) — full sample:**

```jsonc
{
  "v": 2, "event": "new-trip-request", "emittedAt": 1781849270870,
  "data": {
    "trip": {
      "tripId": "951902b3-b8cd-48ae-a95e-3e1b06962a1e",
      "cabId": "c0021e7e-0000-4000-8000-000000000003",
      "dispatchMode": "BID",
      "pickup":  { "latitude": 31.4595, "longitude": 74.2765 },
      "dropoff": { "latitude": 31.52,   "longitude": 74.35 },
      "tripDistanceKm": 12.51,
      "pickupDistanceKm": null,          // null on broadcast — compute from YOUR gps
      "etaToPickupSec": null,            // null on broadcast — compute locally
      "fareRange": { "currency": "SAR", "recommended": 200.89, "min": 160.71, "max": 401.78, "riderOfferedFare": 200 },
      "riderOfferedFare": 200,
      "requestExpiresAt": "2026-06-19 06:09:50",
      "createdAt": "2026-06-19T06:07:50.817Z",
      "rider": {
        "riderId": "966220000025",
        "name": "Mona Al-Shammari",
        "arabicName": "منى الشمري",
        "profileImage": "",
        "rating": 0,
        "totalReviews": 0
      },
      "courier": {                       // PARCEL SUMMARY — no phones
        "parcelType": 4,
        "parcelTypeLabel": "Fragile",
        "parcelWeightKg": 80,
        "weightBucket": null,
        "note": "Glass - keep upright",
        "dimensions": null               // or { lengthCm, widthCm, heightCm } when provided
      }
    }
  }
}
```

The merge rules (`new-trip-request` append, `open-trips-list` replace, `open-trips-update` delta by `tripId`) and the `rider` block semantics are exactly as in the base guide §5.1. The only addition is the `courier` summary — render it (parcel icon, type label, weight, note) so the driver can decide before bidding. **Do not expect phones here.**

REST fallback: `GET /v2/trips/open-in-range?cabId=c0021e7e-0000-4000-8000-000000000003&lat=31.4595&long=74.2765` (header `sessionid`) — items carry the same `courier` summary.

---

## 8. Bid → select (shared flow) + courier match payloads

### 8.1 Place a bid / select a bid — unchanged

The driver bids and the rider selects **exactly** as in the base guide §5.3 (`v2/place-bid`) and §6.3 (`v2/select-bid`). No courier-specific fields. `CAB_TYPE_MISMATCH` is effectively impossible here because the cab is already a courier cab. Real courier `v2/place-bid` ACCEPT_FARE ack (Flow B):

```jsonc
{ "statusCode": 200, "data": {
    "bidId": "d9f3bea5-a166-4a83-a743-c9c15edbdac2",
    "tripId": "951902b3-b8cd-48ae-a95e-3e1b06962a1e",
    "driverId": "966220000014",
    "bidType": 1, "bidFare": 200, "currency": "SAR", "status": 1,
    "etaToPickupSec": null, "pickupDistanceKm": null, "message": null,
    "expiredAt": "2026-06-19 06:08:38", "createdAt": "2026-06-19T06:07:53.000Z"
} }
```

The rider's `v2/trip-bids-update` is the standard shape (each bid embeds a `driver` block with vehicle) — no courier additions. Select with `v2/select-bid { tripId, bidId }` → ack `{ statusCode: 200, data:{ tripStatus: 2, ... } }`.

### 8.2 `v2/bid-won` (server → winning driver) — now carries sender/receiver + deliveryOtp

The base `bid-won` (with the `rider` PII block) is **extended** with a `courier` MATCH block containing the sender & receiver `{name, phone}` and the **`deliveryOtp`**. Released to the **winner only**. Real sample:

```jsonc
{
  "v": 2, "event": "v2/bid-won", "emittedAt": 1781849274807,
  "data": {
    "event": "bid-won",
    "driverId": "966220000014",
    "bidId": "d9f3bea5-a166-4a83-a743-c9c15edbdac2",
    "tripId": "951902b3-b8cd-48ae-a95e-3e1b06962a1e",
    "agreedFare": 200,
    "currency": "SAR",
    "rider": {
      "riderId": "966220000025", "name": "Mona Al-Shammari", "arabicName": "منى الشمري",
      "profileImage": "", "rating": 0, "totalReviews": 0
    },
    "courier": {
      "parcelType": 4,
      "parcelTypeLabel": "Fragile",
      "parcelWeightKg": 80,
      "note": "Glass - keep upright",
      "deliveryOtp": 7377,                            // driver needs this to COMPLETE at drop-off
      "sender":   { "name": "Ali",  "phone": "966500000011" },
      "receiver": { "name": "Sara", "phone": "966500000022" }
    }
  }
}
```

### 8.3 `v2/bid-accepted` (server → rider) — also carries the courier block

Mirror of the above for the rider, with the chosen `driver` block plus the same `courier` block (incl. `deliveryOtp`). The rider needs the `deliveryOtp` to relay to the receiver. An alias event **`driver-selected`** carries identical `data`. Real sample:

```jsonc
{
  "v": 2, "event": "v2/bid-accepted", "emittedAt": 1781849274807,
  "data": {
    "event": "bid-accepted",
    "riderId": "966220000025",
    "tripId": "951902b3-b8cd-48ae-a95e-3e1b06962a1e",
    "bidId": "d9f3bea5-a166-4a83-a743-c9c15edbdac2",
    "driverId": "966220000014",
    "agreedFare": 200, "currency": "SAR",
    "driver": {
      "driverId": "966220000014", "name": "Ahmed Al-Subaie", "arabicName": "أحمد السبيعي",
      "profileImage": "", "mobile": "966500011007", "rating": 0, "totalReviews": 0,
      "vehicle": { "plateNo": "RID-207", "sequenceNo": "900001107", "model": "توجيلا", "color": "بني" }
    },
    "courier": {
      "parcelType": 4, "parcelTypeLabel": "Fragile", "parcelWeightKg": 80,
      "note": "Glass - keep upright",
      "deliveryOtp": 7377,                            // show this to the rider; rider relays to receiver
      "sender":   { "name": "Ali",  "phone": "966500000011" },
      "receiver": { "name": "Sara", "phone": "966500000022" }
    }
  }
}
```

After this, the trip is `ACCEPTED_BY_DRIVER (2)` and both apps switch to the existing V1 assigned-trip screens. The follow-up V1 `trip-detail` (`action: "driver_accepted"`) arrives as usual; for a courier trip its `source.address`/`destination.address` read `"Sender"`/`"Receiver"` and `cabType` is e.g. `"Courier Van"`. The losing-driver / expired / closed events (`v2/bid-lost`, `v2/bid-expired`, `v2/trip-closed`) and `open-trips-update {removed}` are unchanged from the base guide.

---

## 9. Delivery confirmation — complete with the `deliveryOtp`

A courier trip runs the standard V1 lifecycle up to completion: `driver-reached-at-pickup-point` → `started` (with the **`tripOtp`** — same as any ride, given by the *sender* at pickup) → finally **`completed`**, which for a courier trip is **gated on the `deliveryOtp`**.

> **Two distinct OTPs.** `tripOtp` *starts* the trip at pickup (sender). `deliveryOtp` *completes* it at drop-off (receiver). A normal ride has `deliveryOtp = NULL` and no completion gate; the courier gate fires **only** when a `trip_courier_details` row exists.

### 9.1 Rider → receiver OTP handoff

The rider receives `deliveryOtp` in `v2/bid-accepted` (§8.3). The rider passes it to the **receiver** out of band (chat/call/share). At delivery, the receiver tells the driver the code, and the driver submits it on completion. The driver also obtained `deliveryOtp` directly in `v2/bid-won`, so for in-person handoff the driver can confirm the receiver's code matches.

### 9.2 Complete — `PATCH /trips/completed/:tripId` carrying `deliveryOtp`

```jsonc
// headers: { "sessionid": "<driver token>" }
// body:
{ "latitude": 31.5200, "longitude": 74.3500, "address": "Drop", "deliveryOtp": 7377 }
```

**Wrong / missing OTP → real `INVALID_DELIVERY_OTP` (HTTP 400):**
```jsonc
// PATCH /trips/completed/...  { ..., "deliveryOtp": 99999 }
{ "statusCode": 400, "message": "Enter a valid delivery OTP" }
```

**Correct OTP → success (HTTP 200):**
```jsonc
// PATCH /trips/completed/...  { ..., "deliveryOtp": 7377 }
{ "statusCode": 200, "data": { "message": "Trip has completed successfully", "tripData": { "id": "951902b3-...", "tripNo": "1527", "status": 8, "...": "..." } } }
```

> Unlike create-trip, this completion endpoint returns the **real** HTTP status (400 on bad OTP, 200 on success). The gate: completion fails if `deliveryOtp` is missing OR doesn't match the trip's stored value. After success the trip is `status 8` (completed), `completed = 1`, and the normal fare/payment settlement runs.

---

## 10. REST + socket reference (courier-specific)

### 10.1 REST (all require header `sessionid`)

| Method | Path | Actor | Body (courier bits) | Returns |
|---|---|---|---|---|
| GET | `/master/cab-type/all` | Rider | — | cab list; each item has `categoryType`, `maxWeightKg`, `maxLengthCm/WidthCm/HeightCm`, `maxDimSumCm` |
| GET | `/master/cab-type-category/all` (or `/captains/cab-type-category/all`) | Rider/Driver | — | categories incl. `categoryType` |
| POST | `/v2/trips` | Rider | create body + `courier{}` + `riderOfferedFare` | `201 { statusCode:201, data:{ id, status:15, recommendedFare, minFare, maxFare, currency } }` — **errors are real HTTP 4xx `{ statusCode, message, data:{ code, ...details } }`** (§6.4) |
| GET | `/v2/trips/open-in-range?cabId=&lat=&long=` | Driver | — | open items incl. `courier` **summary** (no phones) |
| PATCH | `/trips/completed/:tripId` | Driver | `{ latitude, longitude, address, deliveryOtp }` | `200 success` or `400 "Enter a valid delivery OTP"` |

All shared bid endpoints (`POST /v2/trips/:tripId/bids`, `GET /v2/trips/:tripId/bids`, `PATCH .../accept` `.../reject`, `.../raise-offer`, `.../cancel`, `DELETE .../bids/:bidId`) and the V1 lifecycle endpoints (`driver-reached-at-pickup-point`, `started`) are **unchanged** — see base guide §7.

### 10.2 Socket events (courier-specific deltas)

| Event | Direction | Courier-specific payload |
|---|---|---|
| `new-trip-request` | server → driver | `data.trip.courier` = parcel **summary** (no phones) |
| `open-trips-list` | server → driver | each `data.trips[].courier` = summary |
| `open-trips-update` (added) | server → driver | added items carry the `courier` summary |
| `v2/sync-open-trips` (ack) | driver → server | `ack.data.trips[].courier` = summary |
| `v2/bid-won` | server → winner | `data.courier` = match block **with** sender/receiver `{name,phone}` + `deliveryOtp` |
| `v2/bid-accepted` / `driver-selected` | server → rider | `data.courier` = match block **with** sender/receiver + `deliveryOtp` |

All other socket events (`v2/place-bid`, `v2/trip-bids-update`, `v2/select-bid`, `v2/bid-lost`, `v2/bid-expired`, `v2/trip-closed`, `open-trips-update {removed}`, presence/location) behave identically to the base guide.

---

## 11. Error-code catalog

### 11.1 Courier additions (create-trip; REST returns these as a real HTTP 4xx `{ statusCode, message, data:{ code, ...details } }`)

The machine `code` and any structured fields are flattened into `data`; the human string is the top-level `message`.

| code | when | flattened `data` fields (besides `code`) |
|---|---|---|
| `COURIER_DETAILS_REQUIRED` | courier cab but `courier{}` missing/incomplete (needs sender+receiver phone, `parcelType`, `parcelWeightKg`) | none |
| `INVALID_PARCEL_WEIGHT` | `parcelWeightKg` ≤ 0 or non-numeric | none |
| `PARCEL_EXCEEDS_CAB_WEIGHT` | `parcelWeightKg > cab.maxWeightKg` | `"cabMaxKg":20, "parcelKg":100` |
| `PARCEL_EXCEEDS_CAB_DIMENSIONS` | a parcel dim (or L+W+H sum) over a set cab limit | `"axis":"length"\|"width"\|"height"\|"sum", "cabMax":120, "parcel":150` |
| `COURIER_NOT_SUPPORTED_FOR_CAB` | `courier{}` sent on a non-courier cab | none |
| `OFFER_OUT_OF_BAND` | `riderOfferedFare` outside `[minFare,maxFare]` (band reflects the courier factor); human text in top-level `message` | `"minFare"?\|"maxFare"?, "submitted"` |

### 11.2 Delivery-completion (REST returns real HTTP status)

| code / message | HTTP | when |
|---|---|---|
| `INVALID_DELIVERY_OTP` → `"Enter a valid delivery OTP"` | 400 | completing a courier trip with a missing/wrong `deliveryOtp` |

### 11.3 Reused (bid/select — house socket ack `{ statusCode, message, data:{ code, ...details } }`)

`DRIVER_INELIGIBLE`, `DRIVER_OUT_OF_RANGE`, `CAB_TYPE_MISMATCH`, `BID_BELOW_FLOOR`/`BID_ABOVE_CEILING`, `TRIP_NOT_OPEN`, `TRIP_NOT_FOUND`, `BID_NOT_FOUND`, `BID_NOT_OWNED`, `BID_NO_LONGER_ACTIVE`, `BID_ALREADY_TERMINAL`, `DRIVER_RACE_LOST`, `NOT_TRIP_OWNER`, `PAYMENT_HOLD_FAILED` (recoverable — top up & re-select the same bid), `TRIP_ALREADY_ASSIGNED`/`TRIP_ALREADY_TERMINAL`, `VALIDATION_ERROR`, `INTERNAL_ERROR`. See base guide §8 for semantics.

---

## 12. Full worked example (real values, Flow B)

```
# 1) Rider login (Mona) -> token used as sessionid; userId 966220000025

# 2) NEGATIVE: 100 kg on Courier Bike (max 20)
POST /v2/trips (sessionid: rider)
  { cabId:"c0021e7e-...0001", paymentMethod:2, riderOfferedFare:50,
    addresses:[sender,receiver], courier:{ senderPhone, receiverPhone, parcelType:4, parcelWeightKg:100 } }
  -> HTTP 400  { statusCode:400, message:"PARCEL_EXCEEDS_CAB_WEIGHT", data:{ code:"PARCEL_EXCEEDS_CAB_WEIGHT", cabMaxKg:20, parcelKg:100 } }

# 3) NEGATIVE: courier cab, no courier{} block
POST /v2/trips  { cabId:"c0021e7e-...0003", ... , (no courier) }
  -> HTTP 400  { statusCode:400, message:"COURIER_DETAILS_REQUIRED", data:{ code:"COURIER_DETAILS_REQUIRED" } }

# 4) POSITIVE: Courier Van, 80 kg, FRAGILE, offer 200
POST /v2/trips  { cabId:"c0021e7e-...0003", paymentMethod:2, riderOfferedFare:200,
                  addresses:[sender(31.4595,74.2765),receiver(31.52,74.35)],
                  courier:{ senderPhone:"966500000011", senderName:"Ali",
                            receiverPhone:"966500000022", receiverName:"Sara",
                            parcelType:4, parcelWeightKg:80, parcelNote:"Glass - keep upright" } }
  -> 201 { statusCode:201, data:{ id:"951902b3-...", status:15, recommendedFare:200.89, minFare:160.71, maxFare:401.78, currency:"SAR" } }

# 5) Drivers (subscribed before create) get new-trip-request with a courier SUMMARY (no phones):
#    courier:{ parcelType:4, parcelTypeLabel:"Fragile", parcelWeightKg:80, note:"Glass - keep upright", dimensions:null }

# 6) Driver Ahmed (966220000014) bids ACCEPT_FARE
socket.emit('v2/place-bid', { tripId:"951902b3-...", bidType:1, cabId:"c0021e7e-...0003" }, ack)
  -> ack { statusCode:200, data:{ bidId:"d9f3bea5-...", bidFare:200, status:1 } }
#    rider gets v2/trip-bids-update (1 bid, driver block w/ vehicle)

# 7) Rider selects
socket.emit('v2/select-bid', { tripId:"951902b3-...", bidId:"d9f3bea5-..." }, ack)
  -> ack { statusCode:200, data:{ tripStatus:2, agreedFare:200, driverId:"966220000014" } }
#    winner v2/bid-won  -> courier{ sender:{Ali,966500000011}, receiver:{Sara,966500000022}, deliveryOtp:7377 }
#    rider  v2/bid-accepted (+driver-selected) -> same courier block incl. deliveryOtp:7377

# 8) V1 lifecycle (driver, REST)
PATCH /trips/driver-reached-at-pickup-point/951902b3-...   -> status 5
PATCH /trips/started/951902b3-...  { tripOtp: <tripOtp> }  -> status 7  (sender's OTP at pickup)

# 9) Delivery completion (driver, REST) — deliveryOtp gate
PATCH /trips/completed/951902b3-...  { latitude:31.52, longitude:74.35, address:"Drop", deliveryOtp:99999 }
  -> HTTP 400 { statusCode:400, message:"Enter a valid delivery OTP" }
PATCH /trips/completed/951902b3-...  { latitude:31.52, longitude:74.35, address:"Drop", deliveryOtp:7377 }
  -> HTTP 200 { statusCode:200, data:{ message:"Trip has completed successfully", tripData:{ status:8 } } }
```

---

## 13. Integration checklist & gotchas

- [ ] **Auth:** REST header is **`sessionid`** (verifyotp `token`), not `Authorization`. Socket identity via **`subscribe-user { userID }`** on every (re)connect.
- [ ] **Courier category detection:** pick the category/cabs where `categoryType === 2` (COURIER) — include `=== 5` (CARGO) if you surface freight in the same picker. The rider app MUST collect the `courier{}` block before `POST /v2/trips` for these cabs.
- [ ] **Grey out under-capacity cabs:** use each cab's `maxWeightKg` (and dims, when set) from `GET /master/cab-type/all`. Don't hard-code limits — read them from the cab item.
- [ ] **Addresses:** `addressType 1` = sender (pickup), `addressType 2` = receiver (dropoff).
- [ ] **Fare band:** the band already includes the weight × type factor — just validate `riderOfferedFare ∈ [minFare, maxFare]` from the create response.
- [ ] **Parse the house create error:** on `POST /v2/trips`, a failure is a real HTTP 4xx with `{ statusCode, message, data:{ code, ...details } }`. Read `data.code` (e.g. `PARCEL_EXCEEDS_CAB_WEIGHT`) and the flattened `data` fields directly; the human string is the top-level `message` — **no colon-string splitting** (§6.4).
- [ ] **Driver UI:** render the `courier` **summary** (type label, weight, note, dims) on every open-trip item. **Never expect sender/receiver phones in the open list** — they appear only in `v2/bid-won`.
- [ ] **Show the delivery OTP to the rider:** read `data.courier.deliveryOtp` from `v2/bid-accepted` and prompt the rider to share it with the receiver. The winning driver gets the same `deliveryOtp` (+ sender/receiver phones) in `v2/bid-won`.
- [ ] **Two OTPs:** `tripOtp` starts at pickup (sender); `deliveryOtp` completes at drop-off (receiver). Don't conflate them.
- [ ] **Completion:** the driver MUST send `deliveryOtp` in `PATCH /trips/completed/:tripId` for courier trips; handle `400 "Enter a valid delivery OTP"`. This endpoint returns real HTTP statuses (unlike create).
- [ ] **Reuse the base flow:** bid/select/bid-lost/bid-expired, `PAYMENT_HOLD_FAILED` recovery, and the V1 post-match lifecycle are identical to `docs/V2_MOBILE_INTEGRATION_GUIDE.md` — don't reimplement them.
- [ ] **Rate limits:** `sendotp` ~15s/number; trip-create ~30s/rider (applies to courier too).
```
