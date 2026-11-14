package com.webrtcmeetpro.domain.admin

import com.webrtcmeetpro.domain.admin.dto.res.AdminPaymentResDto
import com.webrtcmeetpro.domain.admin.dto.res.PendingSignupResDto

/** tablePrefix 는 사용자 입력이 아니라 설정값이라 SQL 에 그대로 넣는다 */
interface AdminQueryRepository {
    /** q 는 대문자로 바꾸고 %, _ 를 이스케이프한 LIKE 패턴 */
    fun findRecentPayments(tablePrefix: String, q: String?, status: String?): List<AdminPaymentResDto>

    fun findPendingSignups(tablePrefix: String, q: String?): List<PendingSignupResDto>
}
