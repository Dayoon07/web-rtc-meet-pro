package com.webrtcmeetpro.auth.infrastructure

import com.webrtcmeetpro.auth.domain.LoginHistory
import com.webrtcmeetpro.auth.domain.LoginHistoryRepository
import com.webrtcmeetpro.auth.domain.RefreshToken
import com.webrtcmeetpro.auth.domain.RefreshTokenRepository
import com.webrtcmeetpro.auth.domain.SocialAccount
import com.webrtcmeetpro.auth.domain.SocialAccountRepository
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenJpaRepository : JpaRepository<RefreshToken, Long>, RefreshTokenRepository

interface LoginHistoryJpaRepository : JpaRepository<LoginHistory, Long>, LoginHistoryRepository

interface SocialAccountJpaRepository : JpaRepository<SocialAccount, Long>, SocialAccountRepository
