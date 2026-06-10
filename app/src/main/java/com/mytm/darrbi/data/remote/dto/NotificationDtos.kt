package com.mytm.darrbi.data.remote.dto

import kotlinx.serialization.Serializable

/** Each entry of `GET /user/notifications` (Main API). */
@Serializable
data class NotificationDto(
    val id: String? = null,
    val title: String? = null,
    val message: String? = null,
    val sentTime: String? = null,
    val isRead: Int? = null,
    val type: String? = null,
)
