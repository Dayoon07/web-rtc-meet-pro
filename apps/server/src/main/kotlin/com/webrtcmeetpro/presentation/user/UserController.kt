package com.webrtcmeetpro.presentation.user

import com.webrtcmeetpro.domain.auth.dto.res.AuthTokensResDto
import com.webrtcmeetpro.domain.user.dto.req.ChangePasswordReqDto
import com.webrtcmeetpro.domain.user.dto.req.QuietHoursReqDto
import com.webrtcmeetpro.domain.user.dto.req.UpdateProfileReqDto
import com.webrtcmeetpro.domain.user.dto.req.WithdrawReqDto
import com.webrtcmeetpro.domain.user.dto.res.MeResDto
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.application.auth.AuthService
import com.webrtcmeetpro.application.auth.EmailVerificationService
import com.webrtcmeetpro.application.user.ImageUpload
import com.webrtcmeetpro.common.web.ClientInfo
import jakarta.servlet.http.HttpServletRequest
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

@RestController
@RequestMapping("/api/users/me")
class UserController(
    private val userService: UserService,
    private val authService: AuthService,
    private val emailVerification: EmailVerificationService) {

    @GetMapping
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResDto = userService.me(jwt.userId)

    @PutMapping("/quiet-hours")
    fun quietHours(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: QuietHoursReqDto): MeResDto =
        userService.updateQuietHours(jwt.userId, request.start, request.end, request.timeZone)

    @PutMapping("/profile")
    fun updateProfile(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: UpdateProfileReqDto): MeResDto {
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
    fun uploadProfileImage(@AuthenticationPrincipal jwt: Jwt, @RequestPart("file") file: MultipartFile): MeResDto =
        userService.changeProfileImage(jwt.userId, ImageUpload(file.contentType, file.bytes))

    @DeleteMapping("/profile-image")
    fun removeProfileImage(@AuthenticationPrincipal jwt: Jwt): MeResDto = userService.removeProfileImage(jwt.userId)

    @PostMapping("/password")
    fun changePassword(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: ChangePasswordReqDto, http: HttpServletRequest): AuthTokensResDto =
        authService.changePassword(jwt.userId, request.currentPassword, request.newPassword, ClientInfo.from(http))

    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resendVerification(@AuthenticationPrincipal jwt: Jwt) = emailVerification.resend(jwt.userId)

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@AuthenticationPrincipal jwt: Jwt, @RequestBody(required = false) request: WithdrawReqDto?) =
        userService.withdraw(jwt.userId, request?.password)
}
