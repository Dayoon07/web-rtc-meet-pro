package com.webrtcmeetpro.application.auth

import com.webrtcmeetpro.domain.auth.LoginFailureReason
import com.webrtcmeetpro.domain.auth.LoginHistory
import com.webrtcmeetpro.domain.auth.LoginHistoryRepository
import com.webrtcmeetpro.domain.auth.LoginMethod
import com.webrtcmeetpro.common.web.ClientInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 로그인 시도 기록.
 * - 성공: 호출한 트랜잭션에 합류. 회원가입 직후엔 users 행이 아직 커밋 전이라
 *   별도 트랜잭션에서 FK 로 참조하면 Oracle 이 그 커밋을 기다리며 멈춘다.
 * - 실패: 직후 예외로 바깥 트랜잭션이 롤백돼도 기록은 남아야 하므로 REQUIRES_NEW.
 *   (같은 빈 안에서 호출하면 프록시를 안 거치므로 별도 빈으로 분리)
 */
@Component
class LoginHistoryRecorder(private val loginHistoryRepository: LoginHistoryRepository) {

    @Transactional
    fun success(userId: Long, email: String, method: LoginMethod, client: ClientInfo) {
        loginHistoryRepository.save(
            LoginHistory(
                userId = userId,
                emailAttempted = email.take(255),
                loginMethod = method,
                success = true,
                ipAddress = client.ipAddress,
                userAgent = client.userAgent,
            ),
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun failure(userId: Long?, email: String, method: LoginMethod, reason: LoginFailureReason, client: ClientInfo) {
        loginHistoryRepository.save(
            LoginHistory(
                userId = userId,
                emailAttempted = email.take(255),
                loginMethod = method,
                success = false,
                failureReason = reason.name,
                ipAddress = client.ipAddress,
                userAgent = client.userAgent,
            ),
        )
    }
}
