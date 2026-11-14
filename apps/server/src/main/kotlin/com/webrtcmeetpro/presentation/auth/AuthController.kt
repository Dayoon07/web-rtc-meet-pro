package com.webrtcmeetpro.presentation.auth

import com.webrtcmeetpro.domain.auth.dto.req.EmailVerificationConfirmReqDto
import com.webrtcmeetpro.domain.auth.dto.req.LoginReqDto
import com.webrtcmeetpro.domain.auth.dto.req.OAuthExchangeReqDto
import com.webrtcmeetpro.domain.auth.dto.req.PasswordResetConfirmReqDto
import com.webrtcmeetpro.domain.auth.dto.req.PasswordResetReqDto
import com.webrtcmeetpro.domain.auth.dto.req.RefreshReqDto
import com.webrtcmeetpro.domain.auth.dto.req.SignUpReqDto
import com.webrtcmeetpro.domain.auth.dto.res.AuthProvidersResDto
import com.webrtcmeetpro.domain.auth.dto.res.AuthTokensResDto
import com.webrtcmeetpro.application.auth.AuthService
import com.webrtcmeetpro.application.auth.LoginCommand
import com.webrtcmeetpro.application.auth.OAuthLoginCodeStore
import com.webrtcmeetpro.application.auth.EmailVerificationService
import com.webrtcmeetpro.application.auth.PasswordResetService
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

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val codeStore: OAuthLoginCodeStore,
    private val oauth2Properties: OAuth2Properties,
    private val passwordReset: PasswordResetService,
    private val emailVerification: EmailVerificationService,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpReqDto, http: HttpServletRequest): AuthTokensResDto {
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
    fun login(@Valid @RequestBody request: LoginReqDto, http: HttpServletRequest): AuthTokensResDto =
        authService.login(LoginCommand(request.email, request.password), ClientInfo.from(http))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshReqDto, http: HttpServletRequest): AuthTokensResDto =
        authService.refresh(request.refreshToken, ClientInfo.from(http))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshReqDto) = authService.logout(request.refreshToken)

    /** 회원가입 1단계에서 이메일 중복 확인. 이미 있으면 signInMethod(PASSWORD · GOOGLE)로 어떻게 로그인하면 되는지 */
    @GetMapping("/email-availability")
    fun emailAvailability(@RequestParam email: String): Map<String, Any?> {
        val method = authService.registeredSignInMethod(email)
        return mapOf("available" to (method == null), "signInMethod" to method)
    }

    /** 로그인 화면에서 구글 버튼 활성 여부 */
    @GetMapping("/providers")
    fun providers() = AuthProvidersResDto(google = oauth2Properties.googleEnabled)

    /** 비밀번호 재설정 메일 요청. 가입하지 않은 이메일이어도 똑같이 204 (가입 여부를 알려 주지 않는다) */
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun requestPasswordReset(@Valid @RequestBody request: PasswordResetReqDto) = passwordReset.request(request.email)

    /** 메일 링크의 토큰으로 새 비밀번호 정하기. 다른 기기의 로그인은 모두 끊긴다 */
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmPasswordReset(@Valid @RequestBody request: PasswordResetConfirmReqDto) =
        passwordReset.confirm(request.token.trim(), request.password)

    /** 가입 인증 메일의 링크. 로그인하지 않은 기기에서 열어도 된다 */
    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmEmail(@Valid @RequestBody request: EmailVerificationConfirmReqDto) = emailVerification.confirm(request.token.trim())

    /** 구글 로그인 후 리다이렉트로 받은 1회용 코드를 토큰으로 교환 */
    @PostMapping("/oauth/exchange")
    fun exchange(@Valid @RequestBody request: OAuthExchangeReqDto, http: HttpServletRequest): AuthTokensResDto {
        val userId = codeStore.consume(request.code) ?: throw BusinessException(ErrorCode.OAUTH_CODE_INVALID)
        return authService.issueTokensFor(userId, ClientInfo.from(http))
    }
}
