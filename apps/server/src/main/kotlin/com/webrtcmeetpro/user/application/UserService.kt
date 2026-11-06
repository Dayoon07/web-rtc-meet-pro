package com.webrtcmeetpro.user.application

import com.webrtcmeetpro.auth.domain.SocialAccountRepository
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.plan.application.PlanService
import com.webrtcmeetpro.plan.application.PlanView
import com.webrtcmeetpro.user.domain.Gender
import com.webrtcmeetpro.user.domain.ProfileImageStorage
import com.webrtcmeetpro.user.domain.User
import com.webrtcmeetpro.user.domain.UserProfile
import com.webrtcmeetpro.user.domain.UserRepository
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
