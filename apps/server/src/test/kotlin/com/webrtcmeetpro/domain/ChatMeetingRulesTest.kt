package com.webrtcmeetpro.domain

import com.webrtcmeetpro.domain.chat.ChatRoom
import com.webrtcmeetpro.domain.chat.ChatRoomMember
import com.webrtcmeetpro.domain.chat.ChatRoomType
import com.webrtcmeetpro.config.JacksonConfig
import com.webrtcmeetpro.domain.meeting.Meeting
import com.webrtcmeetpro.domain.meeting.MeetingStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMeetingRulesTest {

    @Test
    fun `1대1 방 키는 두 사람 순서와 상관없이 같다`() {
        assertEquals(ChatRoom.directKeyOf(3, 7), ChatRoom.directKeyOf(7, 3))
        val room = ChatRoom.direct(teamId = 1, creatorId = 7, otherId = 3)
        assertEquals(ChatRoomType.DIRECT, room.roomType)
        assertEquals("3_7", room.directKey)
        assertNull(room.name)
        assertFalse(room.isGroup)
    }

    @Test
    fun `그룹 방은 이름이 있고 1대1 키가 없다`() {
        val room = ChatRoom.group(teamId = 1, creatorId = 1, name = "  디자인팀  ")
        assertEquals("디자인팀", room.name)
        assertNull(room.directKey)
        assertTrue(room.isGroup)
    }

    @Test
    fun `읽음 표시는 뒤로 가지 않는다`() {
        val m = ChatRoomMember(roomId = 1, userId = 1)
        m.markRead(10)
        m.markRead(5)
        assertEquals(10, m.lastReadMessageId)
    }

    private fun meeting(at: LocalDateTime) = Meeting(1, hostId = 1, roomCode = "abc", title = "t", description = null, scheduledAt = at)

    @Test
    fun `예약 회의는 주최자는 언제든, 다른 사람은 10분 전부터 입장`() {
        val now = LocalDateTime.of(2026, 11, 6, 9, 0)
        val m = meeting(now.plusMinutes(30))
        assertTrue(m.canJoin(userId = 1, now = now))
        assertFalse(m.canJoin(userId = 2, now = now))
        assertTrue(m.canJoin(userId = 2, now = now.plusMinutes(21)))
    }

    @Test
    fun `첫 입장만 회의를 시작시키고, 시작된 회의는 취소할 수 없다`() {
        val m = meeting(LocalDateTime.now())
        assertTrue(m.start())
        assertFalse(m.start())
        assertEquals(MeetingStatus.LIVE, m.status)
        assertThrows<IllegalStateException> { m.cancel() }
        m.end()
        assertEquals(MeetingStatus.ENDED, m.status)
    }

    @Test
    fun `모두 나가 끝난 회의도 예정 시간 안이면 다시 열린다 - 취소된 회의는 안 열린다`() {
        val start = LocalDateTime.of(2026, 11, 6, 10, 0)
        val m = meeting(start)
        m.start(start)
        m.end(start.plusMinutes(5))
        assertTrue(m.reopen(start.plusMinutes(10)))
        assertEquals(MeetingStatus.LIVE, m.status)
        m.end(start.plusMinutes(20))
        assertFalse(m.reopen(start.plusMinutes(61)), "60분 회의가 끝난 뒤에는 재입장 불가")

        val canceled = meeting(start.plusDays(1))
        canceled.cancel()
        assertFalse(canceled.reopen(start))
    }

    @Test
    fun `API 시간은 오프셋을 붙여 내보내고, 오프셋 있는 입력은 서버 시간대로 바꾼다`() {
        val local = LocalDateTime.of(2026, 11, 6, 10, 0)
        val offset = local.atZone(ZoneId.systemDefault()).offset
        assertEquals("2026-11-06T10:00$offset", JacksonConfig.toOffsetString(local))
        val fromUtc = JacksonConfig.parse(local.atZone(ZoneId.systemDefault()).toInstant().toString())
        assertEquals(local, fromUtc)
    }
}
