package com.webrtcmeetpro.domain

import com.webrtcmeetpro.application.privacy.PrivacyRetentionService
import com.webrtcmeetpro.application.privacy.RetentionPurger
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.presentation.admin.AdminAccessLogFilter
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 개인정보 보관 기간·동의 기록 규칙 (개인정보처리방침에 적은 것과 같아야 한다) */
class PrivacyRulesTest {
    private val now = LocalDateTime.of(2026, 11, 11, 4, 0)

    @Test
    fun `보관 기간 - 로그인·에러·참가 IP·받아쓴 내용 1년, 관리자 접속기록 2년, 끝난 세션 30일`() {
        val cutoffs = mutableMapOf<String, LocalDateTime>()
        val purger = object : RetentionPurger {
            override fun deleteLoginHistoriesBefore(before: LocalDateTime) = 0.also { cutoffs["login"] = before }
            override fun deleteErrorLogsBefore(before: LocalDateTime) = 0.also { cutoffs["error"] = before }
            override fun clearParticipantClientInfoBefore(before: LocalDateTime) = 0.also { cutoffs["participant"] = before }
            override fun deleteTranscriptsBefore(before: LocalDateTime) = 0.also { cutoffs["transcript"] = before }
            override fun deleteAdminAccessLogsBefore(before: LocalDateTime) = 0.also { cutoffs["admin"] = before }
            override fun deleteRevokedRefreshTokensBefore(before: LocalDateTime) = 0.also { cutoffs["token"] = before }
        }
        PrivacyRetentionService(purger).purge(now)
        val oneYear = now.minusYears(1)
        assertEquals(mapOf("login" to oneYear, "error" to oneYear, "participant" to oneYear, "transcript" to oneYear,
            "admin" to now.minusYears(2), "token" to now.minusDays(30)), cutoffs)
    }

    @Test
    fun `광고성 정보 수신 동의는 동의한 시각을 남기고, 철회하거나 탈퇴하면 지운다`() {
        val user = User.registerSocial("a@test.com", "닉네임", null)
        user.agreeTerms(marketing = true, at = now)
        assertEquals(now, user.marketingAgreedAt)
        user.agreeTerms(marketing = true, at = now.plusDays(1))
        assertEquals(now, user.marketingAgreedAt, "다시 동의해도 처음 동의 시각")
        user.agreeTerms(marketing = false, at = now.plusDays(2))
        assertNull(user.marketingAgreedAt)
        user.agreeTerms(marketing = true, at = now.plusDays(3))
        assertNotNull(user.marketingAgreedAt)
        user.withdraw()
        assertNull(user.marketingAgreedAt)
    }

    @Test
    fun `관리자 접속기록의 쿼리에서 비밀값은 가린다`() {
        assertEquals("page=1&token=***&q=kim", AdminAccessLogFilter.mask("page=1&token=abc123&q=kim"))
        assertEquals("resetPassword=***", AdminAccessLogFilter.mask("resetPassword=hunter2"))
    }
}
