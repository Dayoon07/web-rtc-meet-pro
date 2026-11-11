package com.webrtcmeetpro.external.ollama

import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesLine
import com.webrtcmeetpro.domain.meeting.NotesRejectedException
import com.webrtcmeetpro.domain.meeting.NotesResult
import com.webrtcmeetpro.external.ai.CaptionPrompt
import com.webrtcmeetpro.external.ai.NotesPrompt
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 내 서버의 Ollama (무료, CPU 로도 돈다). POST /api/chat 에 JSON 스키마(format)를 주면 그 모양의 JSON 으로 답한다.
 * 모델은 처음 쓸 때 메모리에 올라가고 keep_alive 가 지나면 내려간다.
 */
@Component
class OllamaClient(private val props: AiProperties, private val json: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    /** @return 모델이 쓴 JSON 글자 */
    fun chat(model: String, system: String, user: String, schema: Map<String, Any>, numCtx: Int, timeout: Duration): String {
        val body = mapOf(
            "model" to model,
            "stream" to false,
            "format" to schema,
            "keep_alive" to props.ollama.keepAlive,
            "options" to mapOf("temperature" to 0.2, "num_ctx" to numCtx),
            "messages" to listOf(mapOf("role" to "system", "content" to system), mapOf("role" to "user", "content" to user)),
        )
        val request = HttpRequest.newBuilder(URI.create(props.ollama.url.trimEnd('/') + "/api/chat"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build()
        val started = System.nanoTime()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            // 모델이 없으면 404 (ollama pull 필요)
            throw IllegalStateException("Ollama ${response.statusCode()}: ${response.body().take(300)}")
        }
        val node = json.readTree(response.body())
        log.debug("Ollama {} {}ms (입력 {} · 출력 {} 토큰)", model, (System.nanoTime() - started) / 1_000_000, node["prompt_eval_count"], node["eval_count"])
        if (node["done_reason"]?.asString() == "length") throw NotesRejectedException("TOO_LONG_OUTPUT")
        return node["message"]["content"].asString()
    }
}

/**
 * Ollama 로 회의록. 받아쓴 내용이 maxContext 보다 길면 앞에서부터 나눠 각각 요약하고,
 * 그 요약들을 한 번 더 합쳐서 마무리한다 (작은 모델·작은 메모리에서도 긴 회의를 다룰 수 있게).
 */
@Component
class OllamaMeetingNotesAi(private val ollama: OllamaClient, private val props: AiProperties, private val json: JsonMapper) {

    fun summarize(input: NotesInput): NotesResult {
        val max = props.ollama.maxContext
        // 프롬프트에 넣을 수 있는 글자 수 (한국어는 대략 1글자 ≈ 1토큰, 답·지시문 몫을 남긴다)
        val budgetChars = ((max - RESERVED_TOKENS) * CHARS_PER_TOKEN).toInt().coerceAtLeast(1000)
        val chunks = chunk(input.lines, budgetChars)
        if (chunks.size <= 1) return once(input, input.lines)

        // 나눠서 요약 → 요약들을 말한 것처럼 이어 붙여 한 번 더
        val parts = chunks.map { once(input, it) }
        val first = input.lines.first().at
        val merged = parts.flatMapIndexed { i, p ->
            val at = chunks[i].first().at
            p.summary.map { NotesLine(at, "(part ${i + 1} summary)", it) } +
                p.decisions.map { NotesLine(at, "(part ${i + 1} decision)", it) } +
                p.actionItems.map { NotesLine(at, "(part ${i + 1} action)", "${it.task} — owner: ${it.owner ?: "?"}, due: ${it.due ?: "?"}") }
        }.ifEmpty { listOf(NotesLine(first, "-", "")) }
        return once(input.copy(meetingTitle = input.meetingTitle + " (notes from ${parts.size} parts)"), merged)
    }

    private fun once(input: NotesInput, lines: List<NotesLine>): NotesResult {
        val user = NotesPrompt.render(input, lines)
        val numCtx = (user.length / CHARS_PER_TOKEN + RESERVED_TOKENS).toInt().coerceIn(4096, props.ollama.maxContext)
        // CPU 에서는 긴 회의 요약이 몇 분 걸린다
        val text = ollama.chat(props.ollama.model, NotesPrompt.SYSTEM, user, NotesPrompt.SCHEMA, numCtx, Duration.ofMinutes(20))
        return NotesPrompt.parse(json, text, "ollama/${props.ollama.model}", input.meetingDate.toLocalDate())
    }

    private fun chunk(lines: List<NotesLine>, budgetChars: Int): List<List<NotesLine>> {
        val out = mutableListOf<MutableList<NotesLine>>(mutableListOf())
        var size = 0
        lines.forEach { line ->
            val len = line.text.length + line.speaker.length + 14
            if (size + len > budgetChars && out.last().isNotEmpty()) {
                out += mutableListOf<NotesLine>()
                size = 0
            }
            out.last() += line
            size += len
        }
        return out
    }

    companion object {
        /** 지시문·회의 정보·답에 남겨 둘 토큰 */
        const val RESERVED_TOKENS = 2500
        const val CHARS_PER_TOKEN = 1.2
    }
}

/** Ollama 로 자막 번역 (짧게, 빨리) */
@Component
class OllamaCaptionTranslator(private val ollama: OllamaClient, private val props: AiProperties, private val json: JsonMapper) {

    fun translate(text: String, sourceLang: String, targetLangs: Collection<String>, context: List<String>): Map<String, String> {
        val targets = CaptionPrompt.targets(targetLangs)
        if (targets.isEmpty()) return emptyMap()
        val model = props.ollama.captionModel.ifBlank { props.ollama.model }
        val out: Map<String, String> = json.readValue(
            ollama.chat(model, CaptionPrompt.SYSTEM, CaptionPrompt.render(text, sourceLang, targets, context), CaptionPrompt.schema(targets), 2048, Duration.ofSeconds(60)),
        )
        return CaptionPrompt.clean(out, targets)
    }
}
