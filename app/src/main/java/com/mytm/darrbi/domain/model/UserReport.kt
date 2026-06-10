package com.mytm.darrbi.domain.model

/** A support ticket the user raised, shown on the My Reports screen. */
data class UserReport(
    val id: Long,
    val issueType: String,
    val description: String,
    val dateTimeIso: String?,
    val solved: Boolean,
    /** Submitted rating (1–5) if the user already rated; null otherwise. */
    val rating: Int?,
)
