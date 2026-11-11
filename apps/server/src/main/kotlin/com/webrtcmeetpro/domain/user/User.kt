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

/**
 * 서비스 전체(시스템) 역할. 팀 안 역할(TeamRole)과 별개다.
 * ADMIN 은 관리자 사이트와 관리자 API(/api/admin 아래)를 쓸 수 있다. 화면에서 바꾸지 않고 DB 에서 직접 지정한다.
 */
enum class SystemRole { USER, ADMIN }
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
        // 출생 연도만 받으므로, 생일이 지나지 않은 만 13세가 들어오지 않게 한 해 더 (만 14세 미만은 법정대리인 동의가 필요: 개인정보 보호법 제22조의2)
        check(birthYear >= 1900 && birthYear <= Year.now().value - MIN_AGE - 1, ErrorCode.UNDERAGE, mapOf("age" to MIN_AGE))
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

    /** 이메일 주인임을 확인한 시각 (가입 인증 메일 링크, 또는 구글이 확인한 이메일). null = 아직 인증 전 */
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


    /** 광고성 정보 수신에 동의한 시각 (정보통신망법 제50조: 동의 일시를 남기고 2년마다 확인). 동의 안 하면 null */

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

    /** 방해 금지 시간 (HH:mm, 사용자 시간대). 둘 다 null = 안 씀. 끝이 시작보다 이르면 다음 날까지 */
    var dndStart: String? = null
        protected set
    var dndEnd: String? = null
        protected set
    var dndTimeZone: String? = null
        protected set

    val isActive: Boolean get() = status == UserStatus.ACTIVE

    /** 방해 금지 시간 정하기 (start·end 가 비면 끄기). 모양이 틀리면 false */
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

    /** 지금이 방해 금지 시간인지 (사용자 시간대 기준) */
    fun inQuietHours(now: java.time.Instant = java.time.Instant.now()): Boolean {
        val start = dndStart?.let(java.time.LocalTime::parse) ?: return false
        val end = dndEnd?.let(java.time.LocalTime::parse) ?: return false
        val zone = runCatching { java.time.ZoneId.of(dndTimeZone ?: "Asia/Seoul") }.getOrDefault(java.time.ZoneId.of("Asia/Seoul"))
        val t = now.atZone(zone).toLocalTime()
        return if (start < end) t >= start && t < end else t >= start || t < end
    }

    /** 활동 중인 시스템 관리자 (정지·탈퇴하면 관리자 권한도 쓸 수 없다) */
    val isSystemAdmin: Boolean get() = isActive && systemRole == SystemRole.ADMIN
    val hasPassword: Boolean get() = passwordHash != null
    val isEmailVerified: Boolean get() = emailVerifiedAt != null

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
     * 탈퇴: 행은 남기되(보낸 메시지·회의 기록이 이 행을 가리킨다) 개인정보는 지운다.
     * 이메일은 deleted-{id}@deleted.invalid 로 바꿔서 같은 이메일로 다시 가입할 수 있게 한다.
     * status = DELETED + deleted_yn = 'Y' (탈퇴 시각은 edited_dt)
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

    /**
     * 구글이 확인한 같은 이메일로 이 계정에 연결할 때 (= 이메일 주인이 왔다).
     * 아직 이메일 인증 전인 계정이면, 비밀번호는 이메일 주인이 아닌 사람이 만들었을 수 있으므로 지운다.
     * @return 비밀번호를 지웠으면 true → 호출한 쪽이 그 비밀번호로 로그인한 세션도 끊는다
     */
    fun verifyEmailByProvider(): Boolean {
        if (isEmailVerified) return false
        val hadPassword = passwordHash != null
        passwordHash = null
        verifyEmail()
        return hadPassword
    }

    /** 비밀번호 바꾸기·만들기 (구글로만 가입한 사람도 비밀번호를 만들 수 있다). 해시는 서비스가 만든다 */
    fun changePassword(hash: String) {
        passwordHash = hash
    }

    companion object {
        /** 탈퇴한 사용자가 보낸 메시지 등에 보이는 이름 */
        const val WITHDRAWN_NICKNAME = "(탈퇴한 사용자)"

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
                // 구글이 이메일 소유를 확인한 계정만 가입시킨다 (AuthService.loginWithGoogle)
                verifyEmail()
            }
    }
}
