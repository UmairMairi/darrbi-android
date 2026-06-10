package com.mytm.darrbi.domain.model

/** Car details resolved from a sequence number (via `/car-info-by-sequence`). */
data class CarInfo(
    val model: String,
    val year: String,
    val sequenceNo: String,
    val seats: Int,
    val plateNumbers: String,
    val plateLetters: String,
    /** Formatted plate sent to become-captain, e.g. "1234-A B C". */
    val carPlateNo: String,
    /** Licence/plate type code sent to become-captain as `carLicenceType`. */
    val plateTypeCode: Int,
)
