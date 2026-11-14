package com.webrtcmeetpro

import com.webrtcmeetpro.domain.meeting.MeetingNotesAi
import com.webrtcmeetpro.domain.meeting.NotesActionItem
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesRejectedException
import com.webrtcmeetpro.domain.meeting.NotesResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * APP_AI_FAKE=true 로 bootTestRun 하면 AI 를 부르지 않고 회의록 화면을 확인할 수 있다.
 * "할 일:" 로 시작하는 말은 할 일, "결정:" 은 결정 사항, "거절" 이 들어 있으면 거절을 흉내 낸다.
 */
@Primary
@Component
@ConditionalOnProperty("app.ai.fake", havingValue = "true")
class FakeMeetingNotesAi : MeetingNotesAi {
    override val enabled = true

    override fun summarize(input: NotesInput): NotesResult {
        if (input.lines.any { "거절" in it.text }) throw NotesRejectedException("REFUSED")
        return NotesResult(
            title = "[가짜] ${input.meetingTitle}",
            language = "ko",
            summary = input.lines.take(3).map { "${it.speaker}: ${it.text}" },
            decisions = input.lines.filter { it.text.startsWith("결정:") }.map { it.text.removePrefix("결정:").trim() },
            actionItems = input.lines.filter { it.text.startsWith("할 일:") }.map {
                NotesActionItem(it.text.removePrefix("할 일:").trim(), it.speaker, "2026-11-13")
            },
            model = "fake",
        )
    }
}

/** "[en] 원문" 처럼 언어 표시만 붙인다. "번역실패" 가 들어 있으면 실패를 흉내 낸다 */
@Primary
@Component
@ConditionalOnProperty("app.ai.fake", havingValue = "true")
class FakeCaptionTranslator : com.webrtcmeetpro.domain.meeting.CaptionTranslator {
    override val enabled = true

    override fun translate(text: String, sourceLang: String, targetLangs: Collection<String>, context: List<String>): Map<String, String> {
        check("번역실패" !in text) { "fake failure" }
        Thread.sleep(200)
        return targetLangs.associateWith { "[$it] $text (ctx ${context.size})" }
    }
}
