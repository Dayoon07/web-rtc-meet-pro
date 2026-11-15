package com.webrtcmeetpro.domain.admin

import com.webrtcmeetpro.domain.admin.dto.res.AdminPaymentResDto
import com.webrtcmeetpro.domain.admin.dto.res.PendingSignupResDto

interface AdminQueryRepository {
    /** q 는 대문자로 바꾸고 %, _ 를 이스케이프한 LIKE 패턴 */
    fun findRecentPayments(q: String?, status: String?): List<AdminPaymentResDto>

    fun findPendingSignups(q: String?): List<PendingSignupResDto>
}
