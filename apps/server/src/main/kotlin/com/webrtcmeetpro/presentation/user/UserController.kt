package com.webrtcmeetpro.presentation.user

import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.application.user.ImageUpload
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

/** 회원 탈퇴 확인용 비밀번호 (구글로만 가입한 계정은 비워 둔다) */
data class WithdrawRequest(val password: String? = null)

@RestController
@RequestMapping("/api/users/me")
class UserController(private val userService: UserService) {

    @GetMapping
    fun me(@AuthenticationPrincipal jwt: Jwt): MeView = userService.me(jwt.userId)

    /** 온보딩(구글 가입 후 추가 정보)과 프로필 수정에 함께 사용 */
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

    /** 회원 탈퇴. 비밀번호가 있는 계정은 password 필수 */
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@AuthenticationPrincipal jwt: Jwt, @RequestBody(required = false) request: WithdrawRequest?) =
        userService.withdraw(jwt.userId, request?.password)
}
