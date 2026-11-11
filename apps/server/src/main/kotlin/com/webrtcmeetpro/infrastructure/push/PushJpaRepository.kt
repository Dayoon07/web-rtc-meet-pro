package com.webrtcmeetpro.infrastructure.push

import com.webrtcmeetpro.domain.push.PushSubscription
import com.webrtcmeetpro.domain.push.PushSubscriptionRepository
import org.springframework.data.jpa.repository.JpaRepository

interface PushSubscriptionJpaRepository : JpaRepository<PushSubscription, Long>, PushSubscriptionRepository
