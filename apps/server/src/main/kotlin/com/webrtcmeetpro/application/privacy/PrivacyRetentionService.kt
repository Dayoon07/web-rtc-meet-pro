package com.webrtcmeetpro.application.privacy

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

interface RetentionPurger {
    fun deleteLoginHistoriesBefore(before: LocalDateTime): Int
    fun deleteErrorLogsBefore(before: LocalDateTime): Int
    /** 참가 사실은 남긴다 */
    fun clearParticipantClientInfoBefore(before: LocalDateTime): Int
    /** 요약은 남긴다 */
    fun deleteTranscriptsBefore(before: LocalDateTime): Int
    fun deleteAdminAccessLogsBefore(before: LocalDateTime): Int
    fun deleteRevokedRefreshTokensBefore(before: LocalDateTime): Int
}

/**
 * 개인정보 보호법 제21조: 목적을 이루면 지체 없이 파기. 기간은 개인정보처리방침과 같아야 한다.
 * - 로그인·에러 기록, 회의 참가 IP, 받아쓴 내용: 1년
 * - 관리자 접속기록: 2년 (안전성 확보조치 기준 제8조)
 * - 결제 기록은 전자상거래법상 5년 보관해야 해서 지우지 않는다
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
