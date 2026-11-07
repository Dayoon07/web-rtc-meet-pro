package com.webrtcmeetpro.application.user

import org.springframework.security.crypto.password.PasswordEncoder
import com.webrtcmeetpro.domain.team.TeamStatus
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.chat.ChatRoomMemberRepository
import com.webrtcmeetpro.domain.auth.RefreshTokenRepository
import com.webrtcmeetpro.domain.auth.SocialAccountRepository
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.application.plan.PlanView
import com.webrtcmeetpro.domain.user.Gender
import com.webrtcmeetpro.domain.user.ProfileImageStorage
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserProfile
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MeView(
    val id: Long,
    val email: String,
    val nickname: String,
    val realName: String?,
    val phoneNumber: String?,
    val countryCode: String?,
    val birthYear: Int?,
    val gender: Gender?,
    val profileImageUrl: String?,
    val marketingAgreed: Boolean,
    /** false 면 프론트가 온보딩(추가 정보 입력)으로 보낸다 */
    val profileCompleted: Boolean,
    val hasPassword: Boolean,
    val linkedProviders: List<String>,
    val plan: PlanView,
)

data class UpdateProfileCommand(
    val profile: UserProfile,
    /** 아직 약관에 동의하지 않은 사용자(구글 가입 직후)만 의미 있음 */
    val termsAgreed: Boolean,
    val privacyAgreed: Boolean,
    val marketingAgreed: Boolean?,
)

/** 업로드 원본. presentation 의 MultipartFile 에 의존하지 않도록 바이트로 받는다 */
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
) {
    fun me(userId: Long): MeView = toView(findUser(userId))

    @Transactional
    fun updateProfile(userId: Long, command: UpdateProfileCommand): MeView {
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
    fun changeProfileImage(userId: Long, upload: ImageUpload): MeView {
        val extension = detectImageExtension(upload) ?: throw BusinessException(ErrorCode.INVALID_IMAGE)
        val user = findUser(userId)
        val previous = user.profileImageUrl
        user.changeProfileImage(profileImageStorage.store(userId, extension, upload.bytes.inputStream()))
        previous?.let(profileImageStorage::deleteIfOwned)
        return toView(user)
    }

    @Transactional
    fun removeProfileImage(userId: Long): MeView {
        val user = findUser(userId)
        user.profileImageUrl?.let(profileImageStorage::deleteIfOwned)
        user.changeProfileImage(null)
        return toView(user)
    }

    /**
     * 회원 탈퇴. 비밀번호가 있는 계정은 비밀번호로 한 번 더 확인한다 (구글로만 가입했으면 화면에서 확인 문구만).
     * 내가 만든 팀이 남아 있으면 막는다 (팀원들의 팀이 갑자기 사라지지 않게 — 먼저 팀을 삭제한다).
     * 팀·채팅방에서 나가고, 로그인 토큰·구독·구글 연결을 정리한 뒤 개인정보를 지운다.
     */
    @Transactional
    fun withdraw(userId: Long, password: String?) {
        val user = findUser(userId)
        val hash = user.passwordHash
        if (hash != null && (password.isNullOrEmpty() || !passwordEncoder.matches(password, hash))) {
            throw BusinessException(ErrorCode.INVALID_PASSWORD)
        }
        val owned = teamRepository.countByOwnerIdAndStatus(userId, TeamStatus.ACTIVE)
        if (owned > 0) throw BusinessException(ErrorCode.OWNED_TEAMS_EXIST, mapOf("count" to owned))

        teamMemberRepository.findAllByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE).forEach { it.leave() }
        chatMemberRepository.findAllByUserIdAndLeftAtIsNull(userId).forEach { it.leave() }
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId).forEach { it.revoke() }
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)?.cancel()
        socialAccountRepository.findAllByUserId(userId).forEach(socialAccountRepository::delete)
        user.profileImageUrl?.let(profileImageStorage::deleteIfOwned)
        user.withdraw()
    }

    private fun findUser(userId: Long): User =
        userRepository.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

    private fun toView(user: User) = MeView(
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
        linkedProviders = socialAccountRepository.findAllByUserId(user.id!!).map { it.provider.name },
        plan = PlanView.from(planService.currentPlanOf(user.id!!)),
    )

    companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

        /** Content-Type 을 믿지 않고 파일 시그니처(매직 바이트)로 형식을 판별 */
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
