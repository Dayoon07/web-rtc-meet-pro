package com.webrtcmeetpro.domain.auth

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter

/** EMAIL_REGISTERED: 구글 로그인인데 그 이메일은 이메일·비밀번호로 가입돼 있음 */
enum class LoginFailureReason { USER_NOT_FOUND, WRONG_PASSWORD, USER_NOT_ACTIVE, SOCIAL_ACCOUNT_ONLY, EMAIL_NOT_VERIFIED, EMAIL_REGISTERED }
enum class LoginMethod { PASSWORD, GOOGLE }

/** 로그인 시도 1회 = 1행. 시도 시각은 created_dt */
@Entity
@Table(name = "login_histories")
class LoginHistory(
    val userId: Long?,

    @Column(nullable = false)
    val emailAttempted: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val loginMethod: LoginMethod,

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    val success: Boolean,

    val failureReason: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val country: String? = null,
    val city: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_history_id")
    var id: Long? = null
        protected set
}

interface LoginHistoryRepository {
    fun save(history: LoginHistory): LoginHistory
}
