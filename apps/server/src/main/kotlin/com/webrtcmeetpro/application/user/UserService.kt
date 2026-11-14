package com.webrtcmeetpro.application.user

import com.webrtcmeetpro.domain.user.dto.res.MeResDto
import org.springframework.security.crypto.password.PasswordEncoder
import com.webrtcmeetpro.domain.team.TeamStatus
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.plan.BillingKeyRepository
import com.webrtcmeetpro.domain.chat.ChatRoomMemberRepository
import com.webrtcmeetpro.domain.auth.RefreshTokenRepository
import com.webrtcmeetpro.domain.auth.SocialAccountRepository
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.domain.user.Gender
import com.webrtcmeetpro.domain.user.ProfileImageStorage
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserProfile
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class UpdateProfileCommand(
    val profile: UserProfile,
    /** 구글 가입 직후에만 false 일 수 있다 */
    val termsAgreed: Boolean,
    val privacyAgreed: Boolean,
    val marketingAgreed: Boolean?,
)

/** presentation 의 MultipartFile 에 묶이지 않게 바이트로 받는다 */
data class ImageUpload(val contentType: String?, val bytes: ByteArray)

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val planService: PlanService,
    private val profileImageStorage: ProfileImageStorage,
    private val passwordEncoder: PasswordEncoder,
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val chatMemberRepository: ChatRoomMemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val billingKeyRepository: BillingKeyRepository,
) {
    fun me(userId: Long): MeResDto = toView(findUser(userId))

    @Transactional
    fun updateProfile(userId: Long, command: UpdateProfileCommand): MeResDto {
        val user = findUser(userId)
        if (user.termsAgreedAt == null && (!command.termsAgreed || !command.privacyAgreed)) {
            throw BusinessException(ErrorCode.TERMS_NOT_AGREED)
        }
        user.updateProfile(command.profile)
        if (user.termsAgreedAt == null || command.marketingAgreed != null) {
            user.agreeTerms(command.marketingAgreed ?: user.marketingAgreed)
        }
        return toView(user)
    }

    @Transactional
    fun changeProfileImage(userId: Long, upload: ImageUpload): MeResDto {
        val extension = detectImageExtension(upload) ?: throw BusinessException(ErrorCode.INVALID_IMAGE)
        val user = findUser(userId)
        val previous = user.profileImageUrl
        user.changeProfileImage(profileImageStorage.store(userId, extension, upload.bytes.inputStream(), upload.bytes.size.toLong()))
        previous?.let(profileImageStorage::deleteIfOwned)
        return toView(user)
    }

    @Transactional
    fun removeProfileImage(userId: Long): MeResDto {
        val user = findUser(userId)
        user.profileImageUrl?.let(profileImageStorage::deleteIfOwned)
        user.changeProfileImage(null)
        return toView(user)
    }

    /**
     * 내가 만든 팀이 남아 있으면 막는다: 팀원들의 팀이 갑자기 사라지면 안 되니 먼저 팀을 삭제하게 한다.
     * 구글로만 가입한 계정은 비밀번호가 없어 화면에서 확인 문구만 받는다.
     */
    @Transactional
    fun withdraw(userId: Long, password: String?) {
        val user = findUser(userId)
        val hash = user.passwordHash
        if (hash != null && (password.isNullOrEmpty() || !passwordEncoder.matches(password, hash))) {
            throw BusinessException(ErrorCode.INVALID_PASSWORD)
        }
        erase(user)
    }

    /** @return 기록용 이메일 */
    @Transactional
    fun verifyEmailByAdmin(userId: Long): String {
        val user = findPendingSignup(userId)
        user.verifyEmail()
        return user.email
    }

    /** 이메일을 비워 같은 이메일로 다시 가입할 수 있게 한다. @return 지우기 전 이메일 (기록용) */
    @Transactional
    fun rejectSignup(userId: Long): String {
        val user = findPendingSignup(userId)
        val email = user.email
        erase(user)
        return email
    }

    private fun findPendingSignup(userId: Long): User {
        val user = findUser(userId)
        if (user.isEmailVerified || !user.isActive || user.isSystemAdmin) throw BusinessException(ErrorCode.ADMIN_VERIFICATION_NOT_PENDING)
        return user
    }

    private fun erase(user: User) {
        val userId = user.id!!
        val owned = teamRepository.countByOwnerIdAndStatus(userId, TeamStatus.ACTIVE)
        if (owned > 0) throw BusinessException(ErrorCode.OWNED_TEAMS_EXIST, mapOf("count" to owned))

        teamMemberRepository.findAllByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE).forEach { it.leave() }
        chatMemberRepository.findAllByUserIdAndLeftAtIsNull(userId).forEach { it.leave() }
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId).forEach { it.revoke() }
        subscriptionRepository.findAllByUserIdAndStatusIn(userId, listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE)).forEach { it.cancel() }
        billingKeyRepository.findAllByUserIdAndActiveTrue(userId).forEach { it.revoke() }
        socialAccountRepository.findAllByUserId(userId).forEach(socialAccountRepository::delete)
        user.profileImageUrl?.let(profileImageStorage::deleteIfOwned)
        user.withdraw()
    }

    private fun findUser(userId: Long): User =
        userRepository.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

    private fun toView(user: User) = MeResDto(
        id = user.id!!,
        email = user.email,
        nickname = user.nickname,
        realName = user.realName,
        phoneNumber = user.phoneNumber,
        countryCode = user.countryCode,
        birthYear = user.birthYear,
        gender = user.gender,
        profileImageUrl = user.profileImageUrl,
        marketingAgreed = user.marketingAgreed,
        profileCompleted = user.isProfileCompleted,
        hasPassword = user.hasPassword,
        emailVerified = user.isEmailVerified,
        linkedProviders = socialAccountRepository.findAllByUserId(user.id!!).map { it.provider.name },
        // 플랜은 소유자 단위
        plan = planService.view(planService.bestOwnedPlan(user.id!!)),
        dndStart = user.dndStart,
        dndEnd = user.dndEnd,
        dndTimeZone = user.dndTimeZone,
    )

    /** 이 시간에는 브라우저 푸시를 보내지 않는다. start·end 가 비면 끄기 */
    @Transactional
    fun updateQuietHours(userId: Long, start: String?, end: String?, timeZone: String?): MeResDto {
        val user = userRepository.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        if (!user.setQuietHours(start, end, timeZone)) throw BusinessException(ErrorCode.INVALID_QUIET_HOURS)
        return toView(user)
    }

    companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

        /** Content-Type 은 조작할 수 있어서 매직 바이트로 판별한다 */
        fun detectImageExtension(upload: ImageUpload): String? {
            val b = upload.bytes
            if (b.isEmpty() || b.size > MAX_IMAGE_BYTES) return null
            fun startsWith(vararg sig: Int) = b.size >= sig.size && sig.indices.all { (b[it].toInt() and 0xFF) == sig[it] }
            return when {
                startsWith(0xFF, 0xD8, 0xFF) -> "jpg"
                startsWith(0x89, 0x50, 0x4E, 0x47) -> "png"
                startsWith(0x47, 0x49, 0x46, 0x38) -> "gif"
                b.size >= 12 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WEBP" -> "webp"
                else -> null
            }
        }
    }
}
