package com.webrtcmeetpro.domain.user.dto.req

import com.webrtcmeetpro.domain.user.Gender
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class UpdateProfileReqDto(
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
