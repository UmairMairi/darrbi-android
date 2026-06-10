package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.NotificationDto
import com.mytm.darrbi.domain.model.Notification

fun NotificationDto.toDomain() = Notification(
    id = id.orEmpty(),
    title = title.orEmpty(),
    message = message.orEmpty(),
    timeIso = sentTime,
    isRead = isRead == 1,
)
