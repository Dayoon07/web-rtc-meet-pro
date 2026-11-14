package com.webrtcmeetpro.domain.auth

import com.webrtcmeetpro.domain.common.BaseEntity
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

    /** DB 가 새어도 토큰을 쓸 수 없게 SHA-256 해시만 저장한다 */
    @Column(nullable = false, unique = true)
    val tokenHash: String,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    val ipAddress: String? = null,
    val userAgent: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    var id: Long? = null
        protected set

    var revokedAt: LocalDateTime? = null
        protected set

    fun isUsable(now: LocalDateTime = LocalDateTime.now()): Boolean =
        revokedAt == null && now.isBefore(expiresAt)

    fun revoke(at: LocalDateTime = LocalDateTime.now()) {
        if (revokedAt == null) revokedAt = at
    }
}

interface RefreshTokenRepository {
    fun save(token: RefreshToken): RefreshToken
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun findAllByUserIdAndRevokedAtIsNull(userId: Long): List<RefreshToken>
}
