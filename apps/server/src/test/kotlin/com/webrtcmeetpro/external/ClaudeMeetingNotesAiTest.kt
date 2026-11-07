package com.webrtcmeetpro.external

import com.sun.net.httpserver.HttpServer
import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesLine
import com.webrtcmeetpro.domain.meeting.NotesRejectedException
import com.webrtcmeetpro.domain.meeting.NotesSpeaker
import com.webrtcmeetpro.external.anthropic.ClaudeClients
import com.webrtcmeetpro.external.anthropic.ClaudeMeetingNotesAi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 실제 SDK 로 가짜 Claude 서버(로컬)에 요청을 보내 요청 모양과 응답 해석을 확인한다 (API 키·네트워크 없이) */
class ClaudeMeetingNotesAiTest {
    private val json = JsonMapper.builder().build()
    private var server: HttpServer? = null
    private var lastBody: JsonNode? = null
    private var lastBeta: String? = null

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun fakeClaude(text: String, stopReason: String = "end_turn"): ClaudeMeetingNotesAi {
        val reply = json.writeValueAsString(
            mapOf(
                "id" to "msg_test", "type" to "message", "role" to "assistant", "model" to "claude-opus-5-5",
                "content" to listOf(mapOf("type" to "text", "text" to text)),
                "stop_reason" to stopReason, "stop_sequence" to null,
                "usage" to mapOf("input_tokens" to 100, "output_tokens" to 50),
            ),
        ).toByteArray()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/messages") { ex ->
                lastBody = json.readTree(ex.requestBody.readAllBytes())
                lastBeta = ex.requestHeaders.getFirst("anthropic-beta")
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, reply.size.toLong())
                ex.responseBody.use { it.write(reply) }
            }
            start()
        }
        val props = AiProperties(apiKey = "test-key", baseUrl = "http://127.0.0.1:${server!!.address.port}")
        return ClaudeMeetingNotesAi(ClaudeClients(props), props, json)
    }

    private val input = NotesInput(
        meetingTitle = "주간 회의",
        meetingDate = LocalDateTime.of(2026, 11, 8, 10, 0),
        participants = listOf(NotesSpeaker(1, "민지"), NotesSpeaker(2, "서준")),
        lines = listOf(
            NotesLine(LocalDateTime.of(2026, 11, 8, 10, 1), "민지", "출시는 화요일로 미뤄요"),
            NotesLine(LocalDateTime.of(2026, 11, 8, 10, 2), "서준", "제가 금요일까지 결제 버그 고칠게요"),
        ),
    )

    @Test
    fun `요청 모양 - 모델, 구조화 출력, 거절 시 대체 모델, 받아쓴 내용`() {
        val ai = fakeClaude(
            """{"title":"출시 일정","language":"ko","summary":["출시를 화요일로 미룸"],"decisions":["화요일 출시"],
               "actionItems":[{"task":"결제 버그 고치기","owner":"서준","due":"2026-11-13"},{"task":"공지","owner":null,"due":"금요일"}]}""",
        )
        val result = ai.summarize(input)

        val body = lastBody!!
        assertEquals("claude-opus-5-5", body["model"].asString())
        assertEquals("json_schema", body["output_config"]["format"]["type"].asString())
        assertEquals("medium", body["output_config"]["effort"].asString())
        val schema = body["output_config"]["format"]["schema"]
        assertEquals(false, schema["additionalProperties"].asBoolean())
        assertTrue(schema["required"].toString().contains("actionItems"))
        assertEquals("default", body["fallbacks"].asString())
        assertEquals("server-side-fallback-2026-07-01", lastBeta)
        assertTrue(body["thinking"] == null || body["thinking"].isNull, "Opus 5.5 는 thinking 을 보내지 않는다 (항상 켜짐)")
        val user = body["messages"][0]["content"].toString()
        assertTrue("[10:01:00] 민지: 출시는 화요일로 미뤄요" in user, user)
        assertTrue("participants: 민지, 서준" in user)

        assertEquals("출시 일정", result.title)
        assertEquals(listOf("화요일 출시"), result.decisions)
        assertEquals("서준", result.actionItems[0].owner)
        assertEquals("2026-11-13", result.actionItems[0].due)
        assertEquals(null, result.actionItems[1].due, "날짜가 아닌 기한은 버린다")
        assertEquals("claude-opus-5-5", result.model)
    }

    @Test
    fun `거절·잘림은 다시 시도하지 않는 실패`() {
        assertThrows<NotesRejectedException> { fakeClaude("", stopReason = "refusal").summarize(input) }
        stop()
        assertThrows<NotesRejectedException> { fakeClaude("{\"title\":", stopReason = "max_tokens").summarize(input) }
    }
}
