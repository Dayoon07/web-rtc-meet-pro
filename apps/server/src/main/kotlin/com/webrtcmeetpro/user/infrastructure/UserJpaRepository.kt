package com.webrtcmeetpro.user.infrastructure

import com.webrtcmeetpro.user.domain.User
import com.webrtcmeetpro.user.domain.UserRepository
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<User, Long>, UserRepository
