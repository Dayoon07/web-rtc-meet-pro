package com.webrtcmeetpro.infrastructure.admin

import com.webrtcmeetpro.domain.admin.AdminErrorLog
import com.webrtcmeetpro.domain.admin.AdminErrorLogRepository
import org.springframework.data.jpa.repository.JpaRepository

interface AdminErrorLogJpaRepository : JpaRepository<AdminErrorLog, Long>, AdminErrorLogRepository
