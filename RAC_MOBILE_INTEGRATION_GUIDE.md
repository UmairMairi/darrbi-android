# RAC Mobile Integration Guide — Car-Rental Marketplace (Renter / Consumer App)

> **Audience:** mobile (consumer/renter) developers integrating the **Derrbi Car Rental** product into the existing app (alongside ride-hailing, courier, city-to-city).
> **Status:** matches the implemented + E2E-tested code on branch `ride-demo-v2`. Every sample below is a **real shape captured from the running system** (see `docs/reviews/RAC_ORDER_CATALOG.md` for 9 fully-traced live orders).
> **Transport:** REST only (api-gateway `/v2/rac/renter/*`). No socket is required for the rental flow.
> **Base URL (demo):** `http://<host>:3010`  •  prod gateway `https://api-darbi.xintdev.com`
> **Auth:** identical to the rest of the app — the login JWT goes in the **`sessionid`** header (NOT `Authorization`). The renter is the **same platform user** as a ride rider (`req.user.id` = `customer.userId`); no separate rental account.
>
> **Related:** partner (B2B) side is the web portal at `/rac`; this guide is the **consumer** side only. Reference-data lists come from `GET /v2/rac/auth/lov`. Region rules (currency/VAT/phone/IBAN per country) live in `src/constants/regions.ts`.

---

## 0. TL;DR — the whole rental flow in 9 steps

1. **Auth** — reuse the app's existing OTP login → keep the `sessionid` JWT (§1).
2. **Discover filters** — `GET /v2/rac/renter/filters` → cities, categories, CDW tiers, sort options (public).
3. **Search vehicles** — `GET /v2/rac/renter/vehicles?city=&category=&pickupAt=&returnAt=&…` → only **available, approved** cars (public).
4. **Vehicle detail** — `GET /v2/rac/renter/vehicles/:id` → full specs, all pricing periods, **CDW tiers + deposit**, pickup branches, add-ons, reviews (public).
5. **Quote** — `POST /v2/rac/renter/quote` with dates + CDW tier (+ coupon) → server-computed price **breakdown**, **deposit**, and an `available` flag (public; never trust client math).
6. **Verify identity (Nafath)** — `POST /v2/rac/renter/nafath/verify` (auth).
7. **Create booking** — `POST /v2/rac/renter/bookings` (auth). Server **re-prices**, **re-checks availability under a row lock**, **captures the rental + authorizes the deposit hold**, and returns the booking. Payment happens here.
8. **Use it** — partner confirms pickup (executes the **Tajeer e-contract** + IoT unlock) → trip active → partner confirms return (penalties settled from the deposit, remainder released).
9. **Post-trip** — `POST /v2/rac/renter/bookings/:id/rate`. (Also: cancel before pickup with policy refund, request extension.)

---

## 1. Auth (same as the ride app)

The rental endpoints reuse the platform's existing rider auth. Nothing rental-specific.

```js
const REST = 'http://<host>:3010';
// 1. send OTP (OTP in this environment = the LAST 4 DIGITS of the mobile number)
let r = await fetch(`${REST}/api/v2/sendotp`, { method:'POST', headers:{'Content-Type':'application/json'},
  body: JSON.stringify({ mobileNo: '966512340001' }) }).then(x=>x.json());
const tId = r.data.tId;
// 2. verify → JWT
r = await fetch(`${REST}/api/v2/verifyotp`, { method:'POST', headers:{'Content-Type':'application/json'},
  body: JSON.stringify({ mobileNo:'966512340001', tId, otp:'0001' }) }).then(x=>x.json());
const sessionid = r.data.token;          // → send as `sessionid` header on every authenticated call
```

- **`sessionid` header** is required on: `nafath/verify`, `bookings` (create), `GET bookings`, `GET bookings/:id`, `cancel`, `extend`, `rate`.
- **Public (no header)**: `filters`, `vehicles` (search), `vehicles/:id`, `quote`, `companies/:id/reviews`.
- First-ever login is a **guest** (`userId` starts `96600…`) — guests are **403-blocked from creating bookings**. Promote once via `POST /user/update-customer { firstName, lastName, email, prefferedLanguage }`, then **re-login** to mint a non-guest sessionid. (This is the platform's standard guest→user promotion, not rental-specific.)

---

## 2. House response envelope (same contract as V2)

- **Success:** HTTP 200/201, body `{ "statusCode": 200, "data": { … }, "message"?: "…" }`.
- **Error:** a **real HTTP 4xx/5xx**, body `{ "statusCode": <code>, "message": "<human text>", "data"?: {…} }`. `message` is auto-translated to the user's `prefferedLanguage` (en/ar).
- Read `data` on success; show `message` on error. Key rental codes: **409** = vehicle not available for the window, **402** = payment failed, **400** = validation (bad dates, expired docs, invalid CDW tier), **401** = missing/expired session, **403** = guest or not-your-booking, **404** = booking/vehicle not found.

---

## 3. Endpoint reference (contracts + real samples)

### 3.1 `GET /v2/rac/renter/filters` — search filter LOVs *(public)*
Use to populate the search screen's dropdowns. (For the full reference-data set — all countries, cities-by-country, fuel types, transmissions, features, add-on types, plans — call `GET /v2/rac/auth/lov?country=SA`.)

```json
{ "statusCode": 200, "data": {
  "cities": ["Dhaka","Doha","Dubai","Jakarta","Karachi","Kuwait City","Manama","Muscat","Riyadh"],
  "categories": ["economy","sedan","suv","premium","electric","van","pickup"],
  "cdwTiers": ["none","basic","standard","premium"],
  "transmissions": ["automatic","manual"],
  "sortOptions": ["price_low","price_high","rating"]
} }
```

### 3.2 `GET /v2/rac/renter/vehicles` — search *(public)*
Query params (all optional): `city`, `category`, `pickupAt` (ISO), `returnAt` (ISO), `seats`, `transmission`, `sortBy` (`price_low|price_high|rating`), `page` (default 1), `limit` (default 20, max 50).
Returns only **ACTIVE** vehicles of **KYB-approved** companies whose assigned branch is **ACTIVE**; when `pickupAt`+`returnAt` are supplied, vehicles with an overlapping booking/block for that window are **excluded**.

```json
{ "statusCode": 200, "data": {
  "items": [{
    "vehicleId": "3d1bffe8-…","make": "Toyota","model": "Camry","year": 2023,
    "category": "sedan","photo": "https://…/front.jpg","seats": 5,"transmission": "automatic",
    "ratingAvg": 4.6,"ratingCount": 18,"companyName": "Al-Khozami Rentals LLC","city": "Riyadh",
    "perDay": 240,"featured": true
  }],
  "total": 12
} }
```

### 3.3 `GET /v2/rac/renter/vehicles/:id` — vehicle detail *(public)*
Full listing for the detail screen: specs, **all 7 pricing periods**, mileage policy, company, **pickup branches** (with GPS + parking instructions), **CDW tiers with deposit + excess**, add-ons, and recent reviews.

```json
{ "statusCode": 200, "data": {
  "vehicleId":"3d1bffe8-…","make":"Toyota","model":"Camry","year":2023,"color":"White",
  "category":"sedan","fuelType":"petrol","transmission":"automatic","engineCc":2500,
  "specs": { "seats":5,"doors":4,"smallLuggage":2,"largeLuggage":2 },
  "features": ["air_conditioning","bluetooth","usb_charging"],
  "photos": ["https://…/1.jpg","https://…/2.jpg","https://…/3.jpg"],
  "ratingAvg":4.6,"ratingCount":18,"featured":true,
  "pricing": { "perHour":35,"h2to3":90,"h4to5":140,"h6to12":195,"perDay":240,"perWeek":1420,"perMonth":5000 },
  "mileagePolicy": { "policy":"limited","includedKmPerDay":300,"extraKmRate":0.5 },
  "company": { "id":"785f…","name":"Al-Khozami Rentals LLC","logo":null,"ratingAvg":4.6,"ratingCount":18 },
  "pickupBranches": [{ "id":"6f3e…","name":"Al Olaya Branch","city":"Riyadh","address":"3221 Al Olaya St",
                       "latitude":24.69,"longitude":46.68,"parkingInstructions":"Pickup zone B, slots 22–35" }],
  "cdwTiers": [
    { "tier":"none","dailyRate":0,"deposit":0,"excess":0 },
    { "tier":"basic","dailyRate":35,"deposit":800,"excess":1500 },
    { "tier":"standard","dailyRate":60,"deposit":800,"excess":750 },
    { "tier":"premium","dailyRate":80,"deposit":800,"excess":0 }
  ],
  "addons": [],
  "reviews": [{ "id":"f900…","rating":5,"text":"Great car","renterName":"Mohammed","companyResponse":null,"createdAt":"2026-06-21T20:02:11.115Z" }]
} }
```
> **CDW + deposit are Derrbi-controlled** (per vehicle category, read-only). `deposit` is held as a pre-authorization on the renter's card at booking and released on clean return. `excess` is the renter's max liability for that tier.

### 3.4 `POST /v2/rac/renter/quote` — price quote *(public, authoritative)*
Always quote before booking; the booking endpoint re-computes server-side and ignores any client amounts.

Request:
```json
{ "vehicleId":"3d1bffe8-…","pickupAt":"2026-07-01T10:00:00.000Z","returnAt":"2026-07-03T10:00:00.000Z",
  "cdwTier":"basic", "addonIds":[], "couponCode":"DRB10" }
```
Response (real shape; `available:false` means the window is taken → don't proceed):
```json
{ "statusCode": 200, "data": {
  "durationHours":48,"days":2,
  "baseAmount":480,"cdwAmount":70,"addonsAmount":0,"discount":0,"couponAmount":48,
  "vatAmount":75.3,"totalAmount":577.3,"depositAmount":800,
  "breakdown":[
    {"label":"Vehicle rental (2 days)","amount":480},
    {"label":"CDW — basic","amount":70},
    {"label":"Coupon","amount":-48},
    {"label":"VAT 15%","amount":75.3}
  ],
  "cdwTier":"basic","excess":1500,"available":true
} }
```
- **VAT % is region-driven** by the vehicle's company country (SA 15, AE 5, QA/KW 0, BH 10, OM 5, PK 18, BD 15, ID 11).
- **Coupons** are validated server-side (`couponCode`) against the campaign catalog (active, date window, usage caps, category/country eligibility). Invalid/expired coupons are ignored (couponAmount 0), not an error.
- Validation: `returnAt` must be after `pickupAt`; `pickupAt` cannot be in the past (≥1h lead). Bad dates → 400.

### 3.5 `POST /v2/rac/renter/nafath/verify` — identity verification *(auth)*
```json
// request → response
{ "nationalId":"1098765432" }
{ "statusCode":200, "data": { "verified":true, "verifiedAt":"2026-06-22T…Z" } }
```
Run before booking; pass `nafathVerified:true` in the booking body. (Backed by the Nafath mock provider; real Nafath OIDC is the production swap-in.)

### 3.6 `POST /v2/rac/renter/bookings` — create booking *(auth)* — **payment happens here**
The server: (1) re-checks the vehicle is ACTIVE + docs not expired, (2) **opens a DB transaction and locks the vehicle row**, (3) re-checks availability inside the lock (→ **409** if taken), (4) re-computes the quote, (5) **captures the rental total and authorizes the deposit hold** (→ **402** on payment failure, transaction rolls back, nothing saved), (6) saves the booking, (7) consumes the coupon. Atomic — you are never charged without a booking.

Request:
```json
{ "vehicleId":"3d1bffe8-…","pickupAt":"2026-07-01T10:00:00.000Z","returnAt":"2026-07-03T10:00:00.000Z",
  "cdwTier":"basic","addonIds":[],"couponCode":"DRB10","nafathVerified":true,
  "renterNationalId":"1098765432","renterLicenseNo":"6692XXXXX" }
```
(`renterName`/`renterPhone`/`renterEmail` are taken from the authenticated user; you may also pass `renterNationalId`/`renterLicenseNo`.)

Response (real shape):
```json
{ "statusCode":200, "message":"Booking confirmed", "data": {
  "bookingId":"2122dc69-…","bookingRef":"DRB-26-A905CF","status":"pending","paymentStatus":"paid",
  "vehicleId":"3d1bffe8-…","vehicle":{"make":"Toyota","model":"Camry","year":2023,"category":"sedan","photo":"https://…"},
  "pickupAt":"2026-07-01T10:00:00.000Z","returnAt":"2026-07-03T10:00:00.000Z","durationHours":48,
  "cdwTier":"basic","baseAmount":480,"cdwAmount":70,"addonsAmount":0,"addons":[],
  "discount":0,"couponAmount":48,"couponCode":"DRB10","vatAmount":75.3,"totalAmount":577.3,
  "depositAmount":800,"refundAmount":0
} }
```
Status after create = **`pending`** (paid, awaiting pickup). Save `bookingId` for all follow-ups.

### 3.7 `GET /v2/rac/renter/bookings` — my bookings *(auth)*
Optional `?status=upcoming|active|past` (or an exact status). Returns `{ items:[…], total }` with vehicle summary + dates + total + status per booking.

### 3.8 `GET /v2/rac/renter/bookings/:id` — booking detail *(auth, own booking only)*
Full record incl. pickup branch, renter block, payment refs, **penalties**, **tripData** (live GPS/fuel during active rental), and the **Tajeer** contract id once pickup is confirmed.

```json
{ "statusCode":200, "data": {
  "bookingId":"2122dc69-…","bookingRef":"DRB-26-A905CF","status":"active","paymentStatus":"paid",
  "vehicle":{ "make":"Toyota","model":"Camry","year":2023,"category":"sedan","photo":"https://…" },
  "pickupAt":"…","returnAt":"…","actualPickupAt":"…","actualReturnAt":null,"durationHours":48,
  "cdwTier":"basic","baseAmount":480,"cdwAmount":70,"addonsAmount":0,"addons":[],
  "discount":0,"couponAmount":48,"couponCode":"DRB10","vatAmount":75.3,"totalAmount":577.3,
  "depositAmount":800,"refundAmount":0,
  "companyName":"Al-Khozami Rentals LLC",
  "pickupBranch":{ "id":"6f3e…","name":"Al Olaya Branch","city":"Riyadh","address":"…","latitude":24.69,"longitude":46.68,"parkingInstructions":null },
  "renter":{ "name":"Mohammed Al-Naimi","phone":"9665…","email":"…","nationalId":"…","licenseNo":"…","nafathVerified":true },
  "penalties": { "lateReturn":0,"lowFuel":0,"outOfZone":0,"penaltiesCollected":true },
  "tripData": { "tajeerContractId":"TJR-G6JQMZ","tajeerQrUrl":"https://…","lat":24.71,"lng":46.69,"fuelStart":98,"fuelNow":62,"distanceKm":84,"lastPing":"…" },
  "extensionRequest": null,
  "paymentRef":"SIMCHG-35df8eff-a3e","authorizationRef":"SIMAUTH-2a972e57-4d5"
} }
```

### 3.9 `POST /v2/rac/renter/bookings/:id/cancel` — cancel before pickup *(auth)*
Allowed only while `pending`. Refund follows the policy windows: **>48h before pickup → full refund**, **24–48h → 50%**, **<24h → no refund**. Vehicle availability is restored.
```json
{ "statusCode":200, "message":"Booking cancelled", "data": { "refundAmount":577.3 } }
```

### 3.10 `POST /v2/rac/renter/bookings/:id/extend` — request extension *(auth)*
Only while `active`. Creates a pending extension request (pro-rated cost); the **partner approves/rejects** it. Re-poll booking detail for `extensionRequest.status`.
```json
// request → response
{ "additionalHours":24 }
{ "statusCode":200, "message":"Extension requested", "data": { "hours":24,"additionalCost":240,"status":"pending" } }
```

### 3.11 `POST /v2/rac/renter/bookings/:id/rate` — rate after completion *(auth)*
Only `completed`, once per booking.
```json
{ "rating":5, "text":"Great car, smooth handover" }   // rating 1–5
```

### 3.12 `GET /v2/rac/renter/companies/:id/reviews` — company reviews *(public)*
`?limit=` → list of `{ rating, text, renterName, companyResponse, createdAt }`.

---

## 4. Payment & deposit (what actually happens)

Derrbi's model: **the renter pays Derrbi; Derrbi pays the rental company directly to its IBAN; the security deposit is a pre-authorization hold on the renter's card** — Derrbi never holds rental float.

| Event | Money action | Where |
|---|---|---|
| Create booking (§3.6) | **Capture** the rental `totalAmount` + **authorize** (hold) the `depositAmount` — two legs, two refs (`paymentRef`, `authorizationRef`) | inside the booking DB transaction; rolls back + refunds on any failure |
| Partner confirms return | If penalties/damage > 0 → **capture that amount from the deposit hold**; then **release the remaining deposit** | `confirm-return` / `report-damage` |
| Renter cancels (§3.9) | Refund per policy window to the original card | `cancel` |
| Weekly | Derrbi pays each company its completed-booking earnings to its IBAN (no commission; subscription model) | payout cron |

> **Demo/integration mode:** payments run in **SIMULATE** mode — `paymentRef`/`authorizationRef` are mock refs (`SIMCHG-…` / `SIMAUTH-…`) so the full lifecycle is exercisable without live card tokens. The production swap-in is the platform PSP (Tap / mada / BurqPay hosted checkout). For a real card flow you'd pass a `cardId` (saved-card token) in the booking body; the contract shape is unchanged. **No card PAN ever touches this API** — only tokens.

---

## 5. Booking lifecycle & statuses

```
search → quote → (nafath) → CREATE BOOKING ─────────────► pending  (paid, deposit held)
                                                              │  partner confirms pickup
                                                              ▼  (Tajeer e-contract executed + IoT unlock)
                                                           active   (tripData live: GPS/fuel/distance)
                                                              │  partner confirms return
                                                              ▼  (penalties captured from deposit, remainder released)
                                                          completed ──► renter rates
   pending ──cancel──► cancelled_by_renter (refund per policy)
   active  ──return overdue──► late_return (per-hour penalty)
   active  ──damage reported──► disputed
```

| Status | Meaning |
|---|---|
| `pending` | Paid, deposit held, awaiting pickup |
| `active` | Picked up; Tajeer contract live; trip in progress |
| `completed` | Returned, deposit settled/released |
| `late_return` | Past return time, not yet returned |
| `cancelled_by_renter` / `cancelled_by_company` | Cancelled; refund applied |
| `disputed` | Damage claim open |

**Penalties** (auto, shown on booking detail, charged from the deposit): late return (per-hour), low-fuel return, out-of-zone. **Tajeer**: the KSA unified e-rental contract is created at pickup; the booking cannot go active if Tajeer rejects (mandatory). The `tajeerContractId` + `tajeerQrUrl` appear in `tripData`.

---

## 6. Multi-country notes

The same endpoints serve all 9 launch countries (SA, AE, QA, KW, BH, OM, PK, BD, ID). What changes per the **vehicle's company country**: **currency** and **VAT %** in the quote, the pickup city set, and (on the partner side) business-ID/IBAN formats. The renter app should render amounts in the company's currency (returned by `GET /v2/rac/auth/lov`'s `countries[].currency`, and implied by the listing's city/company). No per-country branching is needed on the renter endpoints themselves.

---

## 7. Quick-start (copy-paste, full happy path)

```js
const REST = 'http://<host>:3010';
const H = (s) => ({ 'Content-Type':'application/json', ...(s?{sessionid:s}:{}) });

// 1. auth (OTP = last 4 of mobile in this env)
let r = await (await fetch(`${REST}/api/v2/sendotp`, {method:'POST',headers:H(),body:JSON.stringify({mobileNo:'966512340001'})})).json();
r = await (await fetch(`${REST}/api/v2/verifyotp`, {method:'POST',headers:H(),body:JSON.stringify({mobileNo:'966512340001',tId:r.data.tId,otp:'0001'})})).json();
let sid = r.data.token;
// (first-time only) promote guest → then re-login for a non-guest sid
await fetch(`${REST}/user/update-customer`, {method:'POST',headers:H(sid),body:JSON.stringify({firstName:'Test',lastName:'Rider',email:'t@x.com',prefferedLanguage:'en'})});
r = await (await fetch(`${REST}/api/v2/sendotp`, {method:'POST',headers:H(),body:JSON.stringify({mobileNo:'966512340001'})})).json();
r = await (await fetch(`${REST}/api/v2/verifyotp`, {method:'POST',headers:H(),body:JSON.stringify({mobileNo:'966512340001',tId:r.data.tId,otp:'0001'})})).json();
sid = r.data.token;

// 2. search + detail (public)
const list = await (await fetch(`${REST}/v2/rac/renter/vehicles?city=Riyadh&category=sedan&sortBy=price_low`)).json();
const vId = list.data.items[0].vehicleId;
const detail = await (await fetch(`${REST}/v2/rac/renter/vehicles/${vId}`)).json();

// 3. quote (public)
const dates = { pickupAt:'2026-07-01T10:00:00.000Z', returnAt:'2026-07-03T10:00:00.000Z' };
const quote = await (await fetch(`${REST}/v2/rac/renter/quote`, {method:'POST',headers:H(),
  body: JSON.stringify({ vehicleId:vId, ...dates, cdwTier:'basic', couponCode:'DRB10' })})).json();
if (!quote.data.available) throw new Error('not available');

// 4. nafath + book (auth) — payment + deposit happen here
await fetch(`${REST}/v2/rac/renter/nafath/verify`, {method:'POST',headers:H(sid),body:JSON.stringify({nationalId:'1098765432'})});
const booking = await (await fetch(`${REST}/v2/rac/renter/bookings`, {method:'POST',headers:H(sid),
  body: JSON.stringify({ vehicleId:vId, ...dates, cdwTier:'basic', couponCode:'DRB10', nafathVerified:true })})).json();
const bookingId = booking.data.bookingId;

// 5. track + (after completion) rate
const view = await (await fetch(`${REST}/v2/rac/renter/bookings/${bookingId}`, {headers:H(sid)})).json();
// await fetch(`${REST}/v2/rac/renter/bookings/${bookingId}/rate`, {method:'POST',headers:H(sid),body:JSON.stringify({rating:5,text:'Great'})});
```

---

## 8. Error catalog (rental-specific)

| HTTP | Message (example) | When | Mobile action |
|---|---|---|---|
| 400 | `Return time must be after pickup time` / `Pickup time cannot be in the past` | bad dates on quote/book | fix dates |
| 400 | `CDW tier must be one of: none, basic, standard, premium` | invalid `cdwTier` | use a value from `filters.cdwTiers` |
| 402 | `Payment failed` | capture/auth declined at booking | prompt another card; no booking created |
| 403 | (guest) | guest tried to book | promote via `update-customer` + re-login |
| 403 | `Forbidden` | accessing another renter's booking | only fetch own `bookingId`s |
| 404 | `Vehicle not found` / `Booking not found` | bad id | refresh |
| 409 | `Vehicle not available for selected dates` | window taken (re-checked under lock at booking) | re-search / pick other dates |

---

### Appendix — endpoint quick table

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/v2/rac/renter/filters` | public | search dropdowns |
| GET | `/v2/rac/renter/vehicles` | public | search |
| GET | `/v2/rac/renter/vehicles/:id` | public | vehicle detail |
| POST | `/v2/rac/renter/quote` | public | price quote |
| GET | `/v2/rac/renter/companies/:id/reviews` | public | company reviews |
| POST | `/v2/rac/renter/nafath/verify` | sessionid | identity verify |
| POST | `/v2/rac/renter/bookings` | sessionid | create booking (pay) |
| GET | `/v2/rac/renter/bookings` | sessionid | my bookings |
| GET | `/v2/rac/renter/bookings/:id` | sessionid | booking detail |
| POST | `/v2/rac/renter/bookings/:id/cancel` | sessionid | cancel (refund) |
| POST | `/v2/rac/renter/bookings/:id/extend` | sessionid | request extension |
| POST | `/v2/rac/renter/bookings/:id/rate` | sessionid | rate |
| GET | `/v2/rac/auth/lov` | public | full reference data |

> Live, fully-traced examples of every call (with real payloads + responses across all order cases — CDW tiers, coupon, cancel tiers, extension, damage, cross-country) are in **`docs/reviews/RAC_ORDER_CATALOG.md`**.
