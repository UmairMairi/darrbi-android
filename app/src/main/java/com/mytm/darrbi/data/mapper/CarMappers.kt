package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.CaptainDetailsData
import com.mytm.darrbi.data.remote.dto.CarBySequenceData
import com.mytm.darrbi.data.remote.dto.IbanValidationData
import com.mytm.darrbi.domain.model.CaptainDetails
import com.mytm.darrbi.domain.model.CarInfo
import com.mytm.darrbi.domain.model.IbanInfo

private const val DEFAULT_SEATS = 4
private const val DEFAULT_LICENCE_TYPE = 1

fun CarBySequenceData.toDomain(): CarInfo {
    // ride-android normalises the middle plate letter "ي" -> "ى" before building the plate string.
    val middle = plateText2?.let { if (it == "ي") "ى" else it }.orEmpty()
    val plateNo = "${plateNumber ?: ""}-${plateText1.orEmpty()}$middle${plateText3.orEmpty()}"
    return CarInfo(
        model = listOfNotNull(vehicleMaker, vehicleModel).joinToString(" ").ifBlank { "—" },
        year = modelYear?.toString().orEmpty(),
        sequenceNo = carSequenceNo.orEmpty(),
        seats = DEFAULT_SEATS,
        plateNumbers = plateNumber?.toString().orEmpty(),
        plateLetters = listOfNotNull(plateText1, plateText2, plateText3).joinToString(" "),
        carPlateNo = plateNo,
        plateTypeCode = plateTypeCode ?: DEFAULT_LICENCE_TYPE,
    )
}

fun IbanValidationData.toDomain(): IbanInfo = IbanInfo(bank = bank, iban = iban)

fun CaptainDetailsData.toDomain(): CaptainDetails = CaptainDetails(
    id = id,
    driverName = driverName,
    driverNationalId = driverNationalId,
    carPlateNo = carPlateNo,
    carSequenceNo = carSequenceNo,
    approved = approved,
    isWaslApproved = isWASLApproved,
    driverSubStatus = driverSubStatus,
    driverModeSwitch = driverModeSwitch,
    iban = iban,
    mobileNo = mobileNo,
    dateOfBirth = dateOfBirth,
    overallRating = overallRating,
)
