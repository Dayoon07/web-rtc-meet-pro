package com.webrtcmeetpro.user.domain

import com.webrtcmeetpro.common.domain.BaseTimeEntity
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
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
import java.time.Year
import java.util.Locale

enum class UserStatus { ACTIVE, SUSPENDED, DELETED }
enum class Gender { MALE, FEMALE, OTHER }

/** 회원가입 / 온보딩 / 프로필 수정에서 함께 바뀌는 개인정보 묶음 (값 객체) */
data class UserProfile(
    val nickname: String,
    val realName: String,
    val phoneNumber: String,
    val countryCode: String,
    val birthYear: Int,
    val gender: Gender?,
) {
    init {
        fun check(ok: Boolean, code: ErrorCode, params: Map<String, Any> = emptyMap()) {
            if (!ok) throw BusinessException(code, params)
        }
        check(nickname.isNotBlank() && nickname.length <= 50, ErrorCode.INVALID_NICKNAME)
        check(realName.isNotBlank() && realName.length <= 50, ErrorCode.INVALID_NAME)
        check(PHONE.matches(phoneNumber), ErrorCode.INVALID_PHONE)
        check(countryCode in COUNTRIES, ErrorCode.INVALID_COUNTRY)
        check(birthYear >= 1900 && birthYear <= Year.now().value - MIN_AGE, ErrorCode.UNDERAGE, mapOf("age" to MIN_AGE))
    }

    companion object {
        const val MIN_AGE = 14
        /** E.164: +국가번호 포함 숫자 7~15자리 */
        val PHONE = Regex("^\\+[1-9]\\d{6,14}$")
        private val COUNTRIES = Locale.getISOCountries().toSet()

        /** 공백·하이픈을 지운 형태로 정규화 */
        fun normalizePhone(raw: String) = raw.replace(Regex("[\\s()-]"), "")
    }
}

@Entity
@Table(name = "users")
class User(
    email: String,
    passwordHash: String?,
    nickname: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    var id: Long? = null
        protected set

    @Column(nullable = false, unique = true)
    var email: String = email.trim().lowercase()
        protected set

    /** 구글로만 가입한 사용자는 null */
    var passwordHash: String? = passwordHash
        protected set

    @Column(nullable = false)
    var nickname: String = nickname.take(50)
        protected set

    var realName: String? = null
        protected set

    var phoneNumber: String? = null
        protected set

    var countryCode: String? = null
        protected set

    var birthYear: Int? = null
        protected set

    @Enumerated(EnumType.STRING)
    var gender: Gender? = null
        protected set

    var profileImageUrl: String? = null
        protected set

    var termsAgreedAt: LocalDateTime? = null
        protected set

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var marketingAgreed: Boolean = false
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE
        protected set

    var lastLoginAt: LocalDateTime? = null
        protected set

    var deletedAt: LocalDateTime? = null
        protected set

    val isActive: Boolean get() = status == UserStatus.ACTIVE
    val hasPassword: Boolean get() = passwordHash != null

    /** 서비스를 쓰려면 개인정보 + 약관 동의가 모두 있어야 한다 (구글 가입자는 온보딩에서 채움) */
    val isProfileCompleted: Boolean
        get() = realName != null && phoneNumber != null && countryCode != null &&
            birthYear != null && termsAgreedAt != null

    fun updateProfile(profile: UserProfile) {
        nickname = profile.nickname.trim()
        realName = profile.realName.trim()
        phoneNumber = profile.phoneNumber
        countryCode = profile.countryCode
        birthYear = profile.birthYear
        gender = profile.gender
    }

    fun agreeTerms(marketing: Boolean, at: LocalDateTime = LocalDateTime.now()) {
        if (termsAgreedAt == null) termsAgreedAt = at
        marketingAgreed = marketing
    }

    fun changeProfileImage(url: String?) {
        profileImageUrl = url
    }

    fun recordLogin(at: LocalDateTime = LocalDateTime.now()) {
        lastLoginAt = at
    }

    fun withdraw(at: LocalDateTime = LocalDateTime.now()) {
        status = UserStatus.DELETED
        deletedAt = at
    }

    companion object {
        /** 이메일 가입: 프로필 + 약관 동의를 한 번에 받는다 */
        fun register(email: String, passwordHash: String, profile: UserProfile, marketingAgreed: Boolean) =
            User(email, passwordHash, profile.nickname).apply {
                updateProfile(profile)
                agreeTerms(marketingAgreed)
            }

        /** 구글 가입: 이름·사진만 받고 나머지는 온보딩에서 */
        fun registerSocial(email: String, displayName: String?, pictureUrl: String?) =
            User(email, null, displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")).apply {
                profileImageUrl = pictureUrl
            }
    }
}
