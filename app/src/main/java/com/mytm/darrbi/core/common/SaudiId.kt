package com.mytm.darrbi.core.common

/**
 * Validates a Saudi National ID / Iqama number:
 * - exactly 10 digits, and
 * - starts with 1 (citizen NID) or 2 (resident Iqama).
 *
 * (The authoritative check is done server-side by become-captain; we keep the client rule to the standard
 * length + prefix so well-formed numbers aren't rejected by a strict checksum.)
 */
object SaudiId {
    fun isValid(id: String): Boolean =
        id.length == 10 && id.all { it.isDigit() } && (id[0] == '1' || id[0] == '2')
}
