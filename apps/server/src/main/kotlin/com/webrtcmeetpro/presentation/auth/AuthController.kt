package com.webrtcmeetpro.presentation.auth

import com.webrtcmeetpro.application.auth.AuthService
import com.webrtcmeetpro.application.auth.AuthTokens
import com.webrtcmeetpro.application.auth.LoginCommand
import com.webrtcmeetpro.application.auth.OAuthLoginCodeStore
import com.webrtcmeetpro.application.auth.SignUpCommand
import com.webrtcmeetpro.config.properties.OAuth2Properties
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.common.web.ClientInfo
import com.webrtcmeetpro.domain.user.Gender
import com.webrtcmeetpro.domain.user.UserProfile
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class SignUpRequest(
    @field:NotBlank @field:Email @field:Size(max = 255) val email: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val password: String,
    @field:NotBlank @field:Size(max = 50) val nickname: String,
    @field:NotBlank @field:Size(max = 50) val realName: String,
    @field:NotBlank @field:Size(max = 20) val phoneNumber: String,
    @field:NotBlank @field:Size(min = 2, max = 2) val countryCode: String,
    @field:NotNull val birthYear: Int?,
    val gender: Gender? = null,
    val termsAgreed: Boolean = false,
    val privacyAgreed: Boolean = false,
    val marketingAgreed: Boolean = false,
)

data class LoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

data class RefreshRequest(@field:NotBlank val refreshToken: String)
data class OAuthExchangeRequest(@field:NotBlank val code: String)
data class AuthProvidersResponse(val google: Boolean)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val codeStore: OAuthLoginCodeStore,
    private val oauth2Properties: OAuth2Properties,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpRequest, http: HttpServletRequest): AuthTokens {
        val profile = UserProfile(
            nickname = request.nickname.trim(),
            realName = request.realName.trim(),
            phoneNumber = UserProfile.normalizePhone(request.phoneNumber),
            countryCode = request.countryCode.uppercase(),
            birthYear = request.birthYear!!,
            gender = request.gender,
        )
        val command = SignUpCommand(
            email = request.email,
            password = request.password,
            profile = profile,
            termsAgreed = request.termsAgreed,
            privacyAgreed = request.privacyAgreed,
            marketingAgreed = request.marketingAgreed,
        )
        return authService.signUp(command, ClientInfo.from(http))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest): AuthTokens =
        authService.login(LoginCommand(request.email, request.password), ClientInfo.from(http))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest, http: HttpServletRequest): AuthTokens =
        authService.refresh(request.refreshToken, ClientInfo.from(http))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshRequest) = authService.logout(request.refreshToken)

    /** 회원가입 1단계에서 이메일 중복 확인 */
    @GetMapping("/email-availability")
    fun emailAvailability(@RequestParam email: String) =
        mapOf("available" to authService.isEmailAvailable(email))

    /** 로그인 화면에서 구글 버튼 활성 여부 */
    @GetMapping("/providers")
    fun providers() = AuthProvidersResponse(google = oauth2Properties.googleEnabled)

    /** 구글 로그인 후 리다이렉트로 받은 1회용 코드를 토큰으로 교환 */
    @PostMapping("/oauth/exchange")
    fun exchange(@Valid @RequestBody request: OAuthExchangeRequest, http: HttpServletRequest): AuthTokens {
        val userId = codeStore.consume(request.code) ?: throw BusinessException(ErrorCode.OAUTH_CODE_INVALID)
        return authService.issueTokensFor(userId, ClientInfo.from(http))
    }
}
