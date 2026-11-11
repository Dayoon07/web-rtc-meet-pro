package com.webrtcmeetpro.infrastructure.admin

import com.webrtcmeetpro.domain.admin.AdminAccessLog
import com.webrtcmeetpro.domain.admin.AdminAccessLogRepository

import com.webrtcmeetpro.domain.admin.AdminErrorLog
import com.webrtcmeetpro.domain.admin.AdminErrorLogRepository
import org.springframework.data.jpa.repository.JpaRepository

interface AdminErrorLogJpaRepository : JpaRepository<AdminErrorLog, Long>, AdminErrorLogRepository

interface AdminAccessLogJpaRepository : org.springframework.data.jpa.repository.JpaRepository<AdminAccessLog, Long>, AdminAccessLogRepository
