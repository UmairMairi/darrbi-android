package com.mytm.darrbi.domain.model

/** A user notification shown on the Notifications screen. */
data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val timeIso: String?,
    val isRead: Boolean,
)
