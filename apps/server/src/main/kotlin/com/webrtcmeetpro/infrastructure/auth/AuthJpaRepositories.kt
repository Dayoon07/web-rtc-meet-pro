package com.webrtcmeetpro.infrastructure.auth

import com.webrtcmeetpro.domain.auth.LoginHistory
import com.webrtcmeetpro.domain.auth.LoginHistoryRepository
import com.webrtcmeetpro.domain.auth.RefreshToken
import com.webrtcmeetpro.domain.auth.RefreshTokenRepository
import com.webrtcmeetpro.domain.auth.SocialAccount
import com.webrtcmeetpro.domain.auth.SocialAccountRepository
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenJpaRepository : JpaRepository<RefreshToken, Long>, RefreshTokenRepository

interface LoginHistoryJpaRepository : JpaRepository<LoginHistory, Long>, LoginHistoryRepository

interface SocialAccountJpaRepository : JpaRepository<SocialAccount, Long>, SocialAccountRepository
