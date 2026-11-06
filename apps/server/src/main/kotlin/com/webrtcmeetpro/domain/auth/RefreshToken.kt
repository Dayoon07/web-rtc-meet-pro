package com.webrtcmeetpro.domain.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(nullable = false)
    val userId: Long,

    /** SHA-256 hex. 원문은 저장하지 않는다 */
    @Column(nullable = false, unique = true)
    val tokenHash: String,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    val ipAddress: String? = null,
    val userAgent: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    var id: Long? = null
        protected set

    var revokedAt: LocalDateTime? = null
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

    fun isUsable(now: LocalDateTime = LocalDateTime.now()): Boolean =
        revokedAt == null && now.isBefore(expiresAt)

    fun revoke(at: LocalDateTime = LocalDateTime.now()) {
        if (revokedAt == null) revokedAt = at
    }
}

interface RefreshTokenRepository {
    fun save(token: RefreshToken): RefreshToken
    fun findByTokenHash(tokenHash: String): RefreshToken?
}
