package com.webrtcmeetpro.batch.meeting

import com.webrtcmeetpro.application.meeting.RecordingService
import com.webrtcmeetpro.application.meeting.MeetingNotesService
import com.webrtcmeetpro.application.meeting.MeetingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 서버가 여러 대가 되면 ShedLock 등으로 한 대만 돌게 해야 알림이 두 번 가지 않는다 */
@Component
class MeetingReminderScheduler(
    private val meetingService: MeetingService,
    private val recordingService: RecordingService,
    private val notesService: MeetingNotesService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.meeting.reminder-interval:30s}", initialDelayString = "10s")
    fun run() {
        runCatching { meetingService.sendDueReminders() }
            .onSuccess { if (it > 0) log.info("회의 알림 {}건 발송", it) }
            .onFailure { log.error("회의 알림 발송 실패", it) }
        runCatching { meetingService.endAbandoned() }
            .onSuccess { if (it > 0) log.info("빈 회의 {}건 종료", it) }
            .onFailure { log.error("빈 회의 정리 실패", it) }
        runCatching { recordingService.finishStale() }
            .onSuccess { if (it > 0) log.info("끊긴 녹화 {}건 마무리", it) }
            .onFailure { log.error("끊긴 녹화 정리 실패", it) }
        runCatching { recordingService.uploadPending() }
            .onFailure { log.error("녹화 올리기 재시도 실패", it) }
        runCatching { notesService.processPending() }
            .onSuccess { if (it > 0) log.info("AI 회의록 {}건 처리", it) }
            .onFailure { log.error("AI 회의록 처리 실패", it) }
    }
}
