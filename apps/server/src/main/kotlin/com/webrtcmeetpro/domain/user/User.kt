package com.webrtcmeetpro.domain.user

import com.webrtcmeetpro.domain.common.BaseEntity
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

/** 팀 역할과 별개다. ADMIN 은 화면에서 바꾸지 않고 DB 에서 직접 지정한다 */
enum class SystemRole { USER, ADMIN }
enum class Gender { MALE, FEMALE, OTHER }

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
        // 출생 연도만 받으므로 생일이 안 지난 만 13세가 들어오지 않게 한 해 더 뺀다 (개인정보 보호법 제22조의2: 만 14세 미만은 법정대리인 동의 필요)
        check(birthYear >= 1900 && birthYear <= Year.now().value - MIN_AGE - 1, ErrorCode.UNDERAGE, mapOf("age" to MIN_AGE))
    }

    companion object {
        const val MIN_AGE = 14
        /** E.164 */
        val PHONE = Regex("^\\+[1-9]\\d{6,14}$")
        private val COUNTRIES = Locale.getISOCountries().toSet()

        fun normalizePhone(raw: String) = raw.replace(Regex("[\\s()-]"), "")
    }
}

@Entity
@Table(name = "users")
class User(
    email: String,
    passwordHash: String?,
    nickname: String,
) : BaseEntity() {

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

    /** null = 아직 인증 전. 구글 가입은 구글이 확인한 이메일이라 바로 채운다 */
    var emailVerifiedAt: LocalDateTime? = null
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

    /** 정보통신망법 제50조: 동의 일시를 남기고 2년마다 확인해야 한다 */
    var marketingAgreedAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE
        protected set

    var lastLoginAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var systemRole: SystemRole = SystemRole.USER
        protected set

    /** HH:mm, 사용자 시간대. 끝이 시작보다 이르면 다음 날까지 */
    var dndStart: String? = null
        protected set
    var dndEnd: String? = null
        protected set
    var dndTimeZone: String? = null
        protected set

    /** 관리자가 deleted_yn 만 바꿔도 로그인·토큰 갱신이 막혀야 한다 */
    val isActive: Boolean get() = status == UserStatus.ACTIVE && !deleted

    val signInMethod: String get() = if (passwordHash != null) "PASSWORD" else "GOOGLE"

    /** 모양이 틀리면 false */
    fun setQuietHours(start: String?, end: String?, timeZone: String?): Boolean {
        if (start.isNullOrBlank() || end.isNullOrBlank()) {
            dndStart = null
            dndEnd = null
            dndTimeZone = null
            return true
        }
        val hhmm = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        if (!hhmm.matches(start) || !hhmm.matches(end) || start == end) return false
        val zone = timeZone?.takeIf { runCatching { java.time.ZoneId.of(it) }.isSuccess } ?: "Asia/Seoul"
        dndStart = start
        dndEnd = end
        dndTimeZone = zone
        return true
    }

    fun inQuietHours(now: java.time.Instant = java.time.Instant.now()): Boolean {
        val start = dndStart?.let(java.time.LocalTime::parse) ?: return false
        val end = dndEnd?.let(java.time.LocalTime::parse) ?: return false
        val zone = runCatching { java.time.ZoneId.of(dndTimeZone ?: "Asia/Seoul") }.getOrDefault(java.time.ZoneId.of("Asia/Seoul"))
        val t = now.atZone(zone).toLocalTime()
        return if (start < end) t >= start && t < end else t >= start || t < end
    }

    /** 정지·탈퇴하면 관리자 권한도 쓸 수 없다 */
    val isSystemAdmin: Boolean get() = isActive && systemRole == SystemRole.ADMIN
    val hasPassword: Boolean get() = passwordHash != null
    val isEmailVerified: Boolean get() = emailVerifiedAt != null

    /** 구글 가입자는 온보딩에서 개인정보와 약관 동의를 채워야 한다 */
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
        if (marketing && !marketingAgreed) marketingAgreedAt = at
        if (!marketing) marketingAgreedAt = null
        marketingAgreed = marketing
    }

    fun changeProfileImage(url: String?) {
        profileImageUrl = url
    }

    fun recordLogin(at: LocalDateTime = LocalDateTime.now()) {
        lastLoginAt = at
    }

    /**
     * 보낸 메시지·회의 기록이 이 행을 가리키므로 행은 남기고 개인정보만 지운다.
     * 이메일은 deleted-{id}@deleted.invalid 로 바꿔 같은 이메일로 다시 가입할 수 있게 한다.
     */
    fun withdraw() {
        email = "deleted-$id@deleted.invalid"
        nickname = WITHDRAWN_NICKNAME
        passwordHash = null
        emailVerifiedAt = null
        realName = null
        phoneNumber = null
        countryCode = null
        birthYear = null
        gender = null
        profileImageUrl = null
        marketingAgreed = false
        marketingAgreedAt = null
        status = UserStatus.DELETED
        markDeleted()
    }

    fun verifyEmail(at: LocalDateTime = LocalDateTime.now()) {
        if (emailVerifiedAt == null) emailVerifiedAt = at
    }

    /** 구글로만 가입한 사람도 비밀번호를 만들 수 있다 */
    fun changePassword(hash: String) {
        passwordHash = hash
    }

    companion object {
        const val WITHDRAWN_NICKNAME = "(탈퇴한 사용자)"

        fun register(email: String, passwordHash: String, profile: UserProfile, marketingAgreed: Boolean) =
            User(email, passwordHash, profile.nickname).apply {
                updateProfile(profile)
                agreeTerms(marketingAgreed)
            }

    /** 나머지 개인정보는 온보딩에서 받는다 */
        fun registerSocial(email: String, displayName: String?, pictureUrl: String?) =
            User(email, null, displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")).apply {
                profileImageUrl = pictureUrl
                // 이메일 소유를 확인한 구글 계정만 여기까지 온다 (AuthService.loginWithGoogle)
                verifyEmail()
            }
    }
}
