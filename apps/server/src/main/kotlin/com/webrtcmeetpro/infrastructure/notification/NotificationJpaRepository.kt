package com.webrtcmeetpro.infrastructure.notification

import com.webrtcmeetpro.domain.notification.Notification
import com.webrtcmeetpro.domain.notification.NotificationRepository
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationJpaRepository : JpaRepository<Notification, Long>, NotificationRepository
