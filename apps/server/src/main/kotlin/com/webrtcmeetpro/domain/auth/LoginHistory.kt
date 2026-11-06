package com.webrtcmeetpro.domain.auth

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
import java.time.LocalDateTime

enum class LoginFailureReason { USER_NOT_FOUND, WRONG_PASSWORD, USER_NOT_ACTIVE, SOCIAL_ACCOUNT_ONLY, EMAIL_NOT_VERIFIED }
enum class LoginMethod { PASSWORD, GOOGLE }

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
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_history_id")
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
}

interface LoginHistoryRepository {
    fun save(history: LoginHistory): LoginHistory
}
