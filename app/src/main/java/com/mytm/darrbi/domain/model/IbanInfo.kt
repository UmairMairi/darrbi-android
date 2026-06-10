package com.mytm.darrbi.domain.model

/** Result of validating an IBAN: the resolved bank name (e.g. "Rajhi Bank"). */
data class IbanInfo(
    val bank: String?,
    val iban: String?,
)
