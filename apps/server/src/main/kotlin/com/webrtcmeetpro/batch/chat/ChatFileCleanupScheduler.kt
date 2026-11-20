package com.webrtcmeetpro.batch.chat

import com.webrtcmeetpro.application.chat.ChatFileCleanupService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ChatFileCleanupScheduler(private val cleanup: ChatFileCleanupService) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 개인정보 파기(4시)와 겹치지 않게 4시 30분 */
    @Scheduled(cron = "\${app.chat.file-purge-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    fun purge() {
        runCatching { cleanup.purgeDeleted() }
            .onSuccess { if (it > 0) log.info("지운 채팅 첨부파일 {}개 정리", it) }
            .onFailure { log.error("지운 채팅 첨부파일 정리 실패", it) }
    }
}
