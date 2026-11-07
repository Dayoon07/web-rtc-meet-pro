package com.webrtcmeetpro.domain

import com.webrtcmeetpro.domain.meeting.MeetingNote
import com.webrtcmeetpro.domain.meeting.NoteStatus
import com.webrtcmeetpro.domain.plan.AiUsage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeetingNotesRulesTest {
    private val t0 = LocalDateTime.of(2026, 11, 8, 10, 0)

    @Test
    fun `켜고 끈 시간만 센다 (여러 번 켜도 합산)`() {
        val note = MeetingNote(meetingId = 1, startedBy = 1)
        assertTrue(note.turnOn(t0))
        assertFalse(note.turnOn(t0.plusMinutes(1)), "이미 켜져 있으면 다시 켜지지 않는다")
        assertEquals(NoteStatus.RECORDING, note.status)
        assertEquals(300, note.turnOff(t0.plusMinutes(5)))
        assertEquals(NoteStatus.PAUSED, note.status)
        assertEquals(0, note.turnOff(t0.plusMinutes(6)), "꺼져 있으면 0")
        note.turnOn(t0.plusMinutes(10))
        assertEquals(300 + 120, note.totalSeconds(t0.plusMinutes(12)), "지금 켜진 구간도 포함")
        assertEquals(300, note.activeSeconds)
    }

    @Test
    fun `회의가 끝나면 요약 대기, 받아쓴 말이 없으면 EMPTY`() {
        val withLines = MeetingNote(1, 1).apply { turnOn(t0) }
        assertEquals(60, withLines.close(hasLines = true, now = t0.plusMinutes(1)))
        assertEquals(NoteStatus.PROCESSING, withLines.status)
        assertNull(withLines.activeSince)

        val empty = MeetingNote(2, 1).apply { turnOn(t0) }
        empty.close(hasLines = false, now = t0.plusMinutes(1))
        assertEquals(NoteStatus.EMPTY, empty.status)
    }

    @Test
    fun `요약 완료는 PROCESSING 일 때만, 실패는 포기할 때만 FAILED`() {
        val note = MeetingNote(1, 1).apply { turnOn(t0); close(true, t0.plusMinutes(3)) }
        note.attempt()
        note.failed("timeout", giveUp = false)
        assertEquals(NoteStatus.PROCESSING, note.status, "다시 시도할 수 있게 남는다")
        note.ready("주간 회의", """{"summary":[]}""", "ko", "claude-opus-5-5")
        assertEquals(NoteStatus.READY, note.status)
        assertNull(note.errorMessage)
        note.failed("late", giveUp = true)
        assertEquals(NoteStatus.READY, note.status, "끝난 뒤에 온 실패는 무시")

        note.requeue()
        assertEquals(NoteStatus.PROCESSING, note.status)
        assertEquals(0, note.attempts)
        note.failed("refused", giveUp = true)
        assertEquals(NoteStatus.FAILED, note.status)
    }

    @Test
    fun `받아쓰는 중에는 다시 만들 수 없다`() {
        val note = MeetingNote(1, 1).apply { turnOn(t0) }
        assertThrows<IllegalStateException> { note.requeue() }
    }

    @Test
    fun `다시 열린 회의에서 켜면 다시 받아쓴다`() {
        val note = MeetingNote(1, 1).apply { turnOn(t0); close(true, t0.plusMinutes(1)) }
        note.ready(null, "{}", null, "m")
        assertTrue(note.turnOn(t0.plusMinutes(5)))
        assertEquals(NoteStatus.RECORDING, note.status)
        note.close(true, t0.plusMinutes(6))
        assertEquals(NoteStatus.PROCESSING, note.status)
        assertEquals(120, note.activeSeconds)
    }

    @Test
    fun `AI 사용량은 달마다 따로`() {
        assertEquals("2026-11", AiUsage.monthOf(t0))
        assertEquals("2027-01", AiUsage.monthOf(LocalDateTime.of(2027, 1, 1, 0, 0)))
        val usage = AiUsage(1, com.webrtcmeetpro.domain.plan.AiFeature.NOTES, "2026-11")
        usage.add(-5)
        usage.add(90)
        assertEquals(90, usage.usedSeconds)
    }
}
