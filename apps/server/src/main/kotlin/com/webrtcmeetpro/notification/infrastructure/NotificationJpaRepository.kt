package com.webrtcmeetpro.notification.infrastructure

import com.webrtcmeetpro.notification.domain.Notification
import com.webrtcmeetpro.notification.domain.NotificationRepository
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationJpaRepository : JpaRepository<Notification, Long>, NotificationRepository
