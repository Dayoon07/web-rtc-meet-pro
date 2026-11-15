package com.webrtcmeetpro

import com.webrtcmeetpro.core.model.AppNotification
import com.webrtcmeetpro.feature.notifications.NotificationTarget
import com.webrtcmeetpro.feature.notifications.target
import com.webrtcmeetpro.feature.settings.formatAmount
import com.webrtcmeetpro.feature.teams.inviteTokenOf
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkParsingTest {
    private fun notification(link: String) = AppNotification(id = 1, type = "THREAD_REPLY", link = link, createdAt = "2026-11-13T09:00:00+09:00")

    @Test
    fun `스레드 알림 링크는 채팅방과 스레드를 함께 읽는다`() {
        assertEquals(NotificationTarget.ChatRoom(1106, 1131), notification("/app/chat/1/1106?thread=1131").target())
    }

    @Test
    fun `스레드가 없는 채팅 링크는 채팅방만`() {
        assertEquals(NotificationTarget.ChatRoom(1106, null), notification("/app/chat/1/1106").target())
    }

    @Test
    fun `회의 링크`() {
        assertEquals(NotificationTarget.Meeting(12), notification("/app/meetings/12").target())
    }

    @Test
    fun `초대 링크 주소나 코드만 붙여 넣어도 코드를 찾는다`() {
        assertEquals("AbC-123_x", inviteTokenOf("  https://mq.example.com/join/AbC-123_x  "))
        assertEquals("AbC-123_x", inviteTokenOf("https://mq.example.com/join/AbC-123_x?ref=1"))
        assertEquals("AbC-123_x", inviteTokenOf("AbC-123_x"))
    }

    @Test
    fun `금액은 세 자리마다 쉼표`() {
        assertEquals("0", formatAmount(0))
        assertEquals("4,900", formatAmount(4900))
        assertEquals("214,920", formatAmount(214920))
        assertEquals("1,234,567", formatAmount(1234567))
    }

    @Test
    fun `구글 로그인에서 돌아온 주소의 코드와 오류를 읽는다`() {
        com.webrtcmeetpro.platform.OAuthCallbacks.deliver("mq://oauth/callback?code=ab%2Bc-1")
        assertEquals(com.webrtcmeetpro.platform.OAuthResult("ab+c-1", null), com.webrtcmeetpro.platform.OAuthCallbacks.consume())
        com.webrtcmeetpro.platform.OAuthCallbacks.deliver("/oauth/callback?error=EMAIL_REGISTERED_WITH_PASSWORD")
        assertEquals(com.webrtcmeetpro.platform.OAuthResult(null, "EMAIL_REGISTERED_WITH_PASSWORD"), com.webrtcmeetpro.platform.OAuthCallbacks.consume())
        assertEquals(null, com.webrtcmeetpro.platform.OAuthCallbacks.consume())
    }

    @Test
    fun `알림을 눌렀을 때의 채팅 링크는 팀 번호 없이도 방을 찾는다`() {
        assertEquals(NotificationTarget.ChatRoom(1106, null), com.webrtcmeetpro.feature.notifications.linkTarget("/app/chat/0/1106"))
    }
}
