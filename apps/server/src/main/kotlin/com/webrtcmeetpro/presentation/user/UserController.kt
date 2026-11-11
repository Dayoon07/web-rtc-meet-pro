package com.webrtcmeetpro.presentation.user

import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.application.auth.AuthService
import com.webrtcmeetpro.application.auth.AuthTokens
import com.webrtcmeetpro.application.auth.EmailVerificationService
import com.webrtcmeetpro.application.user.ImageUpload
import com.webrtcmeetpro.common.web.ClientInfo
import jakarta.servlet.http.HttpServletRequest
import com.webrtcmeetpro.application.user.MeView
import com.webrtcmeetpro.application.user.UpdateProfileCommand
import com.webrtcmeetpro.application.user.UserService
import com.webrtcmeetpro.domain.user.Gender
import com.webrtcmeetpro.domain.user.UserProfile
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class UpdateProfileRequest(
    @field:NotBlank @field:Size(max = 50) val nickname: String,
    @field:NotBlank @field:Size(max = 50) val realName: String,
    @field:NotBlank @field:Size(max = 20) val phoneNumber: String,
    @field:NotBlank @field:Size(min = 2, max = 2) val countryCode: String,
    @field:NotNull val birthYear: Int?,
    val gender: Gender? = null,
    val termsAgreed: Boolean = false,
    val privacyAgreed: Boolean = false,
    val marketingAgreed: Boolean? = null,
)

/** 비밀번호 바꾸기. 비밀번호가 없는(구글 전용) 계정은 currentPassword 를 비워 둔다 */
data class ChangePasswordRequest(
    @field:Size(max = 72) val currentPassword: String? = null,
    @field:NotBlank @field:Size(min = 8, max = 72) val newPassword: String,
)

/** 회원 탈퇴 확인용 비밀번호 (구글로만 가입한 계정은 비워 둔다) */
data class WithdrawRequest(val password: String? = null)

/** 방해 금지 시간: "HH:mm" 둘 다 비우면 끄기, timeZone 예: Asia/Seoul */
data class QuietHoursRequest(
    @field:Size(max = 5) val start: String? = null,
    @field:Size(max = 5) val end: String? = null,
    @field:Size(max = 50) val timeZone: String? = null,
)

@RestController
@RequestMapping("/api/users/me")
class UserController(
    private val userService: UserService,
    private val authService: AuthService,
    private val emailVerification: EmailVerificationService) {

    @GetMapping
    fun me(@AuthenticationPrincipal jwt: Jwt): MeView = userService.me(jwt.userId)

    /** 온보딩(구글 가입 후 추가 정보)과 프로필 수정에 함께 사용 */
    /** 방해 금지 시간 (이 시간에는 브라우저 푸시를 보내지 않는다). start·end 를 비우면 끄기 */
    @PutMapping("/quiet-hours")
    fun quietHours(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: QuietHoursRequest): MeView =
        userService.updateQuietHours(jwt.userId, request.start, request.end, request.timeZone)

    @PutMapping("/profile")
    fun updateProfile(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: UpdateProfileRequest): MeView {
        val profile = UserProfile(
            nickname = request.nickname.trim(),
            realName = request.realName.trim(),
            phoneNumber = UserProfile.normalizePhone(request.phoneNumber),
            countryCode = request.countryCode.uppercase(),
            birthYear = request.birthYear!!,
            gender = request.gender,
        )
        return userService.updateProfile(
            jwt.userId,
            UpdateProfileCommand(profile, request.termsAgreed, request.privacyAgreed, request.marketingAgreed),
        )
    }

    @PostMapping("/profile-image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadProfileImage(@AuthenticationPrincipal jwt: Jwt, @RequestPart("file") file: MultipartFile): MeView =
        userService.changeProfileImage(jwt.userId, ImageUpload(file.contentType, file.bytes))

    @DeleteMapping("/profile-image")
    fun removeProfileImage(@AuthenticationPrincipal jwt: Jwt): MeView = userService.removeProfileImage(jwt.userId)

    /** 비밀번호 바꾸기·만들기. 다른 기기는 로그아웃되고, 이 기기에는 새 토큰을 돌려준다 */
    @PostMapping("/password")
    fun changePassword(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: ChangePasswordRequest, http: HttpServletRequest): AuthTokens =
        authService.changePassword(jwt.userId, request.currentPassword, request.newPassword, ClientInfo.from(http))

    /** 가입 인증 메일 다시 보내기 (60초에 한 번) */
    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resendVerification(@AuthenticationPrincipal jwt: Jwt) = emailVerification.resend(jwt.userId)

    /** 회원 탈퇴. 비밀번호가 있는 계정은 password 필수 */
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@AuthenticationPrincipal jwt: Jwt, @RequestBody(required = false) request: WithdrawRequest?) =
        userService.withdraw(jwt.userId, request?.password)
}
