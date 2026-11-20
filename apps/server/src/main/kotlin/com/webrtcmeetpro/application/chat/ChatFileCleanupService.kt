package com.webrtcmeetpro.application.chat

import com.webrtcmeetpro.domain.chat.ChatFileStorage
import com.webrtcmeetpro.domain.chat.ChatMessageFileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime

/**
 * 지운 채팅의 첨부파일을 실제 저장소(서버 디스크 또는 S3)에서 지운다.
 * 메시지를 지우거나 팀을 지워 방이 지워져도 행은 deleted_yn 만 바뀌어서, 파일은 그대로 남아 저장 공간을 차지한다.
 * 개인정보 처리방침의 "처리 목적을 이루면 지체 없이(매일 자동 점검) 파기" 에 맞춰 매일 한 번 돈다.
 *
 * 지운 지 GRACE 가 지난 것만 지운다: 막 지운 메시지를 받고 있던 다운로드가 중간에 끊기지 않게.
 * 저장소에서 먼저 지우고 행을 지운 것으로 표시한다. 저장소 삭제는 몇 번 해도 같아서, 행 표시에 실패하면 다음 날 다시 한다.
 */
@Service
class ChatFileCleanupService(
    private val files: ChatMessageFileRepository,
    private val storage: ChatFileStorage,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    /** @return 지운 파일 수 */
    fun purgeDeleted(now: LocalDateTime = LocalDateTime.now()): Int {
        var purged = 0
        for (file in files.findOrphanedBefore(now.minus(GRACE))) {
            runCatching {
                storage.delete(file.storedName)
                tx.executeWithoutResult { files.findFileById(file.id!!)?.takeIf { !it.deleted }?.delete() }
                purged++
            }.onFailure { log.warn("지운 채팅 첨부파일 정리 실패, 내일 다시 (fileId={})", file.id, it) }
        }
        return purged
    }

    companion object {
        val GRACE: Duration = Duration.ofDays(1)
    }
}
