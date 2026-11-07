package com.webrtcmeetpro.domain.auth

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class SocialProvider { GOOGLE }

/** 소셜 로그인 연결. (provider, providerUserId) 로 사용자를 찾는다. 구글은 sub 클레임 */
@Entity
@Table(name = "user_social_accounts")
class SocialAccount(
    @Column(nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: SocialProvider,

    @Column(nullable = false)
    val providerUserId: String,

    val email: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val linkedAt: LocalDateTime = LocalDateTime.now()

    var lastLoginAt: LocalDateTime? = null
        protected set

    fun recordLogin(at: LocalDateTime = LocalDateTime.now()) {
        lastLoginAt = at
    }
}

interface SocialAccountRepository {
    fun save(account: SocialAccount): SocialAccount
    fun findByProviderAndProviderUserId(provider: SocialProvider, providerUserId: String): SocialAccount?
    fun findAllByUserId(userId: Long): List<SocialAccount>
}
