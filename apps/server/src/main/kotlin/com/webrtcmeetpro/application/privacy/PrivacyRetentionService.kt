package com.webrtcmeetpro.application.privacy

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 보관 기간이 지난 개인정보를 지우는 일 (infrastructure 에 JPQL 로 구현) */
interface RetentionPurger {
    /** 로그인 기록 (IP·브라우저) */
    fun deleteLoginHistoriesBefore(before: LocalDateTime): Int
    /** 에러 기록 (IP·브라우저·사용자) */
    fun deleteErrorLogsBefore(before: LocalDateTime): Int
    /** 회의 참가 기록의 IP·브라우저 (참가 사실은 남긴다) */
    fun clearParticipantClientInfoBefore(before: LocalDateTime): Int
    /** AI 회의록 받아쓴 내용 (요약은 남긴다) */
    fun deleteTranscriptsBefore(before: LocalDateTime): Int
    /** 관리자 접속기록 */
    fun deleteAdminAccessLogsBefore(before: LocalDateTime): Int
    /** 끝난 로그인 세션 (만료·로그아웃된 리프레시 토큰) */
    fun deleteRevokedRefreshTokensBefore(before: LocalDateTime): Int
}

/**
 * 개인정보 보관 기간 (개인정보 보호법 제21조: 목적을 이루면 지체 없이 파기). 개인정보처리방침에 적은 기간과 같아야 한다.
 * - 로그인 기록·에러 기록: 1년 (부정 로그인 확인·장애 대응. 통신비밀보호법의 접속기록 3개월보다 길게)
 * - 회의 참가 IP·브라우저: 1년 뒤 지움 (참가 사실은 회의 기록으로 남김)
 * - AI 회의록 받아쓴 내용: 1년 (요약·할 일은 워크스페이스 기록으로 남김)
 * - 관리자 접속기록: 2년 (안전성 확보조치 기준 제8조: 1년 이상, 이용자 5만 명 이상이면 2년 이상)
 * - 결제 기록: 5년 (전자상거래법) → 지우지 않는다. 탈퇴 회원 정보는 탈퇴할 때 바로 익명화 (UserService.withdraw)
 */
@Service
class PrivacyRetentionService(private val purger: RetentionPurger) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Result(val loginHistories: Int, val errorLogs: Int, val participants: Int, val transcripts: Int, val adminLogs: Int, val tokens: Int) {
        val total get() = loginHistories + errorLogs + participants + transcripts + adminLogs + tokens
    }

    fun purge(now: LocalDateTime = LocalDateTime.now()): Result {
        val oneYear = now.minusYears(1)
        val result = Result(
            loginHistories = purger.deleteLoginHistoriesBefore(oneYear),
            errorLogs = purger.deleteErrorLogsBefore(oneYear),
            participants = purger.clearParticipantClientInfoBefore(oneYear),
            transcripts = purger.deleteTranscriptsBefore(oneYear),
            adminLogs = purger.deleteAdminAccessLogsBefore(now.minusYears(ADMIN_LOG_YEARS)),
            tokens = purger.deleteRevokedRefreshTokensBefore(now.minusDays(30)),
        )
        if (result.total > 0) log.info("보관 기간이 지난 개인정보 파기: {}", result)
        return result
    }

    companion object {
        const val ADMIN_LOG_YEARS = 2L
    }
}
