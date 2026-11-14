package com.webrtcmeetpro.domain.admin

import com.webrtcmeetpro.domain.admin.dto.res.AdminPaymentResDto
import com.webrtcmeetpro.domain.admin.dto.res.PendingSignupResDto

/**
 * 관리자 화면의 검색 조회 (MyBatis: mapper/admin/AdminMapper.xml).
 * tablePrefix 는 관리자 사이트가 다른 DB 계정으로 접속할 때 붙이는 스키마 이름(설정값)이라 SQL 에 그대로 넣는다
 */
interface AdminQueryRepository {
    /** q 는 대문자로 바꾸고 %, _ 를 이스케이프한 LIKE 패턴 */
    fun findRecentPayments(tablePrefix: String, q: String?, status: String?): List<AdminPaymentResDto>

    fun findPendingSignups(tablePrefix: String, q: String?): List<PendingSignupResDto>
}
