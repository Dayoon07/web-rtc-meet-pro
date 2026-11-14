package com.webrtcmeetpro.domain.notification.dto.res

import com.webrtcmeetpro.domain.notification.NotificationType
import java.time.LocalDateTime

data class NotificationResDto(
    val id: Long,
    val type: NotificationType,
    val params: Map<String, Any?>,
    val link: String?,
    val read: Boolean,
    val createdAt: LocalDateTime,
)
