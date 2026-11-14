package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.config.properties.AdminProperties
import com.webrtcmeetpro.domain.admin.AdminQueryRepository
import com.webrtcmeetpro.domain.admin.dto.res.AdminPaymentResDto
import org.springframework.stereotype.Service

/** 관리자 사이트 "결제·환불": 최근 결제 (주문번호·이메일·주문명·팀 이름으로 찾기) */
@Service
class AdminPaymentService(
    private val queries: AdminQueryRepository,
    private val props: AdminProperties,
) {
    /** 최근 결제 200건. q = 주문번호·이메일·주문명·팀 이름 일부, status = DONE | CANCELED | FAILED ... */
    fun recent(q: String?, status: String?): List<AdminPaymentResDto> =
        queries.findRecentPayments(
            props.tablePrefix,
            q?.trim()?.takeIf { it.isNotEmpty() }?.let(::likeUpper),
            status?.trim()?.takeIf { it.matches(Regex("[A-Z_]{2,20}")) },
        )
}

/** 대소문자 없이 찾는 LIKE 패턴 (%, _, \ 는 글자 그대로 찾게 이스케이프) */
internal fun likeUpper(text: String): String =
    "%" + text.uppercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
