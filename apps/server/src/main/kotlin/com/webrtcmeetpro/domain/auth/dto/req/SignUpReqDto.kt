package com.webrtcmeetpro.domain.auth.dto.req

import com.webrtcmeetpro.domain.user.Gender
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class SignUpReqDto(
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
