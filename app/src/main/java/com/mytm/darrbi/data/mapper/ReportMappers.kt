package com.mytm.darrbi.data.mapper

import com.mytm.darrbi.data.remote.dto.TicketDto
import com.mytm.darrbi.domain.model.UserReport

private const val STATUS_PENDING = 1L

fun TicketDto.toDomain(): UserReport = UserReport(
    id = id ?: 0L,
    issueType = type.orEmpty(),
    description = description.orEmpty(),
    dateTimeIso = createdAt,
    // Treat anything past the initial "pending" status as solved (CMS status ids vary by env).
    solved = statusId != null && statusId != STATUS_PENDING,
    rating = rating?.takeIf { it > 0 },
)
