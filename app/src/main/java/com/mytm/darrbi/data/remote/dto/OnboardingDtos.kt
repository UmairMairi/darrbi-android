package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.Serializable

/** `POST /user/set-rider-name` (note: backend spells the field `referalCode`). */
@Serializable
data class SetRiderNameRequest(
    val mobileNo: String,
    val name: String,
    val referalCode: String? = null,
)

/**
 * `POST /captains/guest-to-driver` — converts the OTP-verified guest into a driver with their license
 * details. [userId] is the captain's National ID (matches ride-android, which sends the entered ID here).
 */
@Serializable
data class GuestToDriverRequest(
    val mobileNo: String,
    val userId: String,
    val licExpiry: String,
    val referalCode: String? = null,
)

/**
 * `POST /car-info-by-sequence`.
 * [userid] is the car owner's National ID — sent ONLY when registering on someone else's authorization.
 * When the captain is the owner it is left null and omitted from the body (`explicitNulls = false`).
 */
@Serializable
data class CarBySequenceRequest(
    val sequenceNumber: String,
    val userid: String? = null,
)

/** Subset of ride-android's `CarDetailsModel.Data` that onboarding needs (display + become-captain). */
@Serializable
data class CarBySequenceData(
    val carSequenceNo: String? = null,
    val modelYear: Int? = null,
    val ownerName: String? = null,
    val plateNumber: Long? = null,
    val plateText1: String? = null,
    val plateText2: String? = null,
    val plateText3: String? = null,
    val plateTypeCode: Int? = null,
    val vehicleMaker: String? = null,
    val vehicleModel: String? = null,
)

/** One entry of become-captain's `drivingModes` array, e.g. `{"drivingMode":1}`. */
@Serializable
data class DrivingMode(val drivingMode: Int)

/** `GET /user/get-balance` — the user's wallet balance. */
@Serializable
data class BalanceData(val balance: Double? = null)

/** `GET /master/pages/{terms-and-conditions|privacy-policy}/{language}` — legal content sections. */
@Serializable
data class LegalData(val data: List<LegalItem> = emptyList())

@Serializable
data class LegalItem(
    val title: String? = null,
    /** HTML body of the section. */
    val description: String? = null,
    val order: Long? = null,
)

/** `GET /user/validate-iban/{iban}` — bank lookup for a valid IBAN. */
@Serializable
data class IbanValidationData(
    val bank: String? = null,
    val iban: String? = null,
)

/** `GET /captains` — captain account details (subset; unknown fields are ignored). */
@Serializable
data class CaptainDetailsData(
    val id: String? = null,
    val driverName: String? = null,
    val driverNationalId: String? = null,
    val carPlateNo: String? = null,
    val carSequenceNo: String? = null,
    val approved: Boolean? = null,
    val isWASLApproved: Int? = null,
    val driverSubStatus: Int? = null,
    val iban: String? = null,
    val mobileNo: String? = null,
    val dateOfBirth: String? = null,
    val overallRating: Double? = null,
)

/** `POST /captains/become-captain` (mirrors ride-android's request body). */
@Serializable
data class BecomeCaptainRequest(
    val subscriptionId: String,
    val autoRenewal: Boolean,
    val driverNationalId: String,
    val carPlateNo: String,
    val carSequenceNo: String,
    val carLicenceType: Int,
    val cab: String,
    val drivingModes: List<DrivingMode>,
    val acceptTC: Boolean,
)
