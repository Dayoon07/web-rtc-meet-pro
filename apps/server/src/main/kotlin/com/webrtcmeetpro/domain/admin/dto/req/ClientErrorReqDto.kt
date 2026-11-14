package com.webrtcmeetpro.domain.admin.dto.req

import com.webrtcmeetpro.domain.admin.ErrorCategory
import com.webrtcmeetpro.domain.admin.ErrorSource
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 서버에 연결이 안 될 때도 사용자에게 바로 코드를 보여 주려고 에러 코드는 화면이 만든다 */
data class ClientErrorReqDto(
    @field:NotNull @field:Pattern(regexp = "^ERR-\\d{8}-[0-9A-F]{20}$") val errorCode: String?,
    @field:NotNull val source: ErrorSource?,
    val category: ErrorCategory = ErrorCategory.CLIENT,
    @field:Size(max = 300) val name: String? = null,
    @field:Size(max = 4000) val message: String? = null,
    @field:Size(max = 32000) val stack: String? = null,
    @field:Size(max = 1000) val path: String? = null,
)
