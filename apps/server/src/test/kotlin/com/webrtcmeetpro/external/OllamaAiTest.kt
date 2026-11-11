package com.webrtcmeetpro.external

import com.sun.net.httpserver.HttpServer
import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.config.properties.AiProvider
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesLine
import com.webrtcmeetpro.domain.meeting.NotesSpeaker
import com.webrtcmeetpro.external.ollama.OllamaCaptionTranslator
import com.webrtcmeetpro.external.ollama.OllamaClient
import com.webrtcmeetpro.external.ollama.OllamaMeetingNotesAi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaAiTest {
    private val json = JsonMapper.builder().build()
    private var server: HttpServer? = null
    private val bodies = mutableListOf<JsonNode>()

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private fun fakeOllama(reply: (JsonNode) -> String): AiProperties {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/chat") { ex ->
                val body = json.readTree(ex.requestBody.readAllBytes())
                bodies.add(body)
                val out = json.writeValueAsString(mapOf("model" to body["model"].asString(), "message" to mapOf("role" to "assistant", "content" to reply(body)), "done" to true, "done_reason" to "stop")).toByteArray()
                ex.sendResponseHeaders(200, out.size.toLong())
                ex.responseBody.use { it.write(out) }
            }
            start()
        }
        return AiProperties(ollama = AiProperties.Ollama(url = "http://127.0.0.1:${server!!.address.port}", maxContext = 4096))
    }

    private val notesJson = """{"title":"출시","language":"ko","summary":["요약"],"decisions":["화요일 출시"],"actionItems":[{"task":"버그","owner":"민지","due":"2026-11-13"}]}"""

    private fun input(lines: Int, length: Int = 20) = NotesInput(
        "주간 회의", LocalDateTime.of(2026, 11, 8, 10, 0), listOf(NotesSpeaker(1, "민지")),
        (1..lines).map { NotesLine(LocalDateTime.of(2026, 11, 8, 10, 0).plusSeconds(it.toLong()), "민지", "가".repeat(length)) },
    )

    @Test
    fun `어느 AI 를 쓸지 - auto 는 키가 있으면 claude, 없으면 ollama, 자막만 따로`() {
        assertEquals(AiProvider.NONE, AiProperties().notesProvider)
        assertEquals(AiProvider.OLLAMA, AiProperties(ollama = AiProperties.Ollama(url = "http://x")).notesProvider)
        assertEquals(AiProvider.CLAUDE, AiProperties(apiKey = "k", ollama = AiProperties.Ollama(url = "http://x")).notesProvider)
        val mixed = AiProperties(provider = "ollama", captionProvider = "claude", apiKey = "k", ollama = AiProperties.Ollama(url = "http://x"))
        assertEquals(AiProvider.OLLAMA, mixed.notesProvider)
        assertEquals(AiProvider.CLAUDE, mixed.captionsProvider)
        assertEquals(AiProvider.NONE, AiProperties(provider = "claude").notesProvider, "키 없이 claude 를 고르면 꺼짐")
    }

    @Test
    fun `회의록 요청 - 모델, JSON 스키마(format), 문맥 크기, 메모리 유지 시간`() {
        val props = fakeOllama { notesJson }
        val result = OllamaMeetingNotesAi(OllamaClient(props, json), props, json).summarize(input(3))
        val body = bodies.single()
        assertEquals("qwen2.5:7b", body["model"].asString())
        assertEquals(false, body["stream"].asBoolean())
        assertEquals("object", body["format"]["type"].asString())
        assertTrue(body["format"]["required"].toString().contains("actionItems"))
        assertEquals(4096, body["options"]["num_ctx"].asInt())
        assertEquals("10m", body["keep_alive"].asString())
        assertTrue("민지: 가가가" in body["messages"][1]["content"].asString())
        assertEquals("ollama/qwen2.5:7b", result.model)
        assertEquals("민지", result.actionItems.single().owner)
    }

    @Test
    fun `긴 회의는 나눠서 요약한 뒤 합친다`() {
        val props = fakeOllama { notesJson }
        // maxContext 4096 → 글자 예산 약 1900. 200줄 × 60글자면 여러 조각
        OllamaMeetingNotesAi(OllamaClient(props, json), props, json).summarize(input(200, 60))
        assertTrue(bodies.size >= 3, "조각 요약 + 마지막 합치기: ${bodies.size}")
        val last = bodies.last()["messages"][1]["content"].asString()
        assertTrue("(part 1 summary)" in last && "notes from" in last, last.take(400))
        bodies.forEach { assertTrue(it["options"]["num_ctx"].asInt() <= 4096) }
    }

    @Test
    fun `자막 번역 - 요청한 언어만, 모델이 더 주거나 빈 값이면 버림`() {
        val props = fakeOllama { """{"en":"Let's go","ja":" "}""" }
        val out = OllamaCaptionTranslator(OllamaClient(props, json), props, json).translate("가자", "ko", listOf("en", "ja", "??"), listOf("민지: 안녕"))
        assertEquals(mapOf("en" to "Let's go"), out)
        val body = bodies.single()
        assertEquals("""["en","ja"]""", body["format"]["required"].toString())
        assertEquals(2048, body["options"]["num_ctx"].asInt())
    }

    @Test
    fun `요일 기한은 서버가 날짜로 - 이번 주는 처음 오는 그 요일, 다음 주는 다음 월요일부터`() {
        val sunday = java.time.LocalDate.of(2026, 11, 8)
        val wednesday = java.time.LocalDate.of(2026, 11, 11)
        val d = { day: java.time.LocalDate, w: String, next: Boolean -> com.webrtcmeetpro.external.ai.NotesPrompt.weekdayDate(day, w, next).toString() }
        assertEquals("2026-11-13", d(sunday, "FRI", false))
        assertEquals("2026-11-12", d(sunday, "THU", false))
        assertEquals("2026-11-09", d(sunday, "MON", true), "일요일에 말한 다음 주 월요일 = 내일")
        assertEquals("2026-11-11", d(wednesday, "WED", false), "같은 요일이면 그날")
        assertEquals("2026-11-20", d(wednesday, "FRI", true))
        val parsed = com.webrtcmeetpro.external.ai.NotesPrompt.parse(
            json, """{"title":"t","language":"ko","summary":[],"decisions":[],"actionItems":[{"task":"a","owner":null,"due":"2026-11-10","dueWeekday":"FRI","dueNextWeek":false}]}""",
            "m", sunday,
        )
        assertEquals("2026-11-13", parsed.actionItems.single().due, "모델이 계산한 날짜보다 요일을 믿는다")
    }
}
