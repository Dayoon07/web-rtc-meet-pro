package com.webrtcmeetpro.meeting.application

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 30초마다 곧 시작할 예약 회의를 찾아 초대자에게 알림 (서버 여러 대면 ShedLock 등으로 한 대만 돌게 해야 함) */
@Component
class MeetingReminderScheduler(private val meetingService: MeetingService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.meeting.reminder-interval:30s}", initialDelayString = "10s")
    fun run() {
        runCatching { meetingService.sendDueReminders() }
            .onSuccess { if (it > 0) log.info("회의 알림 {}건 발송", it) }
            .onFailure { log.error("회의 알림 발송 실패", it) }
        runCatching { meetingService.endAbandoned() }
            .onSuccess { if (it > 0) log.info("빈 회의 {}건 종료", it) }
            .onFailure { log.error("빈 회의 정리 실패", it) }
    }
}
