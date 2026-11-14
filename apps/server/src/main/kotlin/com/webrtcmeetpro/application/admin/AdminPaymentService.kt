package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.config.properties.AdminProperties
import com.webrtcmeetpro.domain.admin.AdminQueryRepository
import com.webrtcmeetpro.domain.admin.dto.res.AdminPaymentResDto
import org.springframework.stereotype.Service

@Service
class AdminPaymentService(
    private val queries: AdminQueryRepository,
    private val props: AdminProperties,
) {
    fun recent(q: String?, status: String?): List<AdminPaymentResDto> =
        queries.findRecentPayments(
            props.tablePrefix,
            q?.trim()?.takeIf { it.isNotEmpty() }?.let(::likeUpper),
            status?.trim()?.takeIf { it.matches(Regex("[A-Z_]{2,20}")) },
        )
}

/** %, _, \ 를 글자 그대로 찾도록 이스케이프한 대소문자 무시 LIKE 패턴 */
internal fun likeUpper(text: String): String =
    "%" + text.uppercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
