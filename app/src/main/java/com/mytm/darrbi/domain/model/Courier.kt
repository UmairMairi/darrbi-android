package com.mytm.darrbi.domain.model

/**
 * Courier (parcel delivery) domain types — see `V2_COURIER_MOBILE_INTEGRATION_GUIDE.md`. Courier is the
 * SAME V2 broadcast + bidding flow with three additive layers: a category discriminator, parcel metadata
 * on the trip, per-cab weight/size limits, and a separate delivery OTP at drop-off.
 */

/**
 * Service-category discriminator carried on every `cab_type_category` (and joined onto each cab type).
 * The rider app branches its flow on this — a courier category collects parcel info before create-trip.
 */
enum class CategoryType(val wire: Int) {
    Default(1),
    Courier(2),
    Schedule(3),
    RentACar(4),
    Cargo(5),
    Unknown(-1);

    /** COURIER (2) and CARGO (5) both run the courier (parcel) create-trip flow server-side (guide §2.1). */
    val isCourier: Boolean get() = this == Courier || this == Cargo

    companion object {
        fun from(value: Int?): CategoryType = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/** Parcel content type (guide §2.2). Each value also carries a fare `typeFactor` applied server-side. */
enum class ParcelType(val wire: Int) {
    Documents(1),
    Food(2),
    Electronics(3),
    Fragile(4),
    Clothing(5),
    Medicine(6),
    Groceries(7),
    Furniture(8),
    Other(9);

    companion object {
        fun from(value: Int?): ParcelType? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Optional rider-declared weight bucket (guide §2.3) — a UI convenience only. Limits and fare always key
 * off the authoritative [CourierDetails.parcelWeightKg], never the bucket.
 */
enum class ParcelWeightBucket(val wire: Int) {
    UpTo1Kg(1),
    UpTo5Kg(2),
    UpTo20Kg(3),
    UpTo50Kg(4),
    Over50Kg(5);

    companion object {
        fun from(value: Int?): ParcelWeightBucket? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The rider's courier input, sent in the create-trip `courier{}` block (guide §4). Pickup = sender,
 * dropoff = receiver. Sender/receiver phones are released to the winning driver only.
 */
data class CourierDetails(
    val senderPhone: String,
    val senderName: String?,
    val receiverPhone: String,
    val receiverName: String?,
    val parcelType: ParcelType,
    val parcelWeightKg: Double,
    val weightBucket: ParcelWeightBucket? = null,
    val lengthCm: Int? = null,
    val widthCm: Int? = null,
    val heightCm: Int? = null,
    val note: String? = null,
) {
    /** Multiplicative courier fare factor (weight × type), so the rider's offer aligns with the server band. */
    val fareFactor: Double get() = courierFareFactor(parcelWeightKg, parcelType)
}

/**
 * Client-side courier fare factor mirroring the server defaults (guide §5): a weight factor × a parcel-type
 * factor, always ≥ 1.0. The server recomputes this authoritatively at create-trip; this only shapes the
 * rider's recommended fare and stepper bounds so an in-band offer is the default.
 */
fun courierFareFactor(weightKg: Double, type: ParcelType): Double {
    val weightFactor = when {
        weightKg <= 5.0 -> 1.00
        weightKg <= 20.0 -> 1.15
        weightKg <= 50.0 -> 1.35
        else -> 1.60
    }
    val typeFactor = when (type) {
        ParcelType.Documents -> 1.00
        ParcelType.Food -> 1.05
        ParcelType.Electronics -> 1.15
        ParcelType.Fragile -> 1.20
        ParcelType.Clothing -> 1.05
        ParcelType.Medicine -> 1.10
        ParcelType.Groceries -> 1.05
        ParcelType.Furniture -> 1.25
        ParcelType.Other -> 1.00
    }
    return weightFactor * typeFactor
}

/**
 * Driver-facing parcel SUMMARY attached to an open (awaiting-bids) trip (guide §7). Carries type/weight/
 * note/dimensions so the driver can decide before bidding — but NEVER the sender/receiver phone numbers.
 */
data class CourierSummary(
    val parcelType: ParcelType?,
    val parcelTypeLabel: String?,
    val parcelWeightKg: Double,
    val weightBucket: ParcelWeightBucket?,
    val note: String?,
    val lengthCm: Int?,
    val widthCm: Int?,
    val heightCm: Int?,
)

/** A courier party (sender or receiver) released to the winner at match (guide §8.2/§8.3). */
data class CourierContact(val name: String?, val phone: String)

/**
 * Courier MATCH block delivered on `v2/bid-won` (winner) and `v2/bid-accepted` (rider) at match time
 * (guide §8). Adds sender/receiver `{name, phone}` and the [deliveryOtp] the receiver hands to the driver
 * to COMPLETE the trip at drop-off — a separate OTP from the `tripOtp` that starts the trip at pickup.
 */
data class CourierMatch(
    val parcelType: ParcelType?,
    val parcelTypeLabel: String?,
    val parcelWeightKg: Double,
    val note: String?,
    val deliveryOtp: Int?,
    val sender: CourierContact?,
    val receiver: CourierContact?,
)
