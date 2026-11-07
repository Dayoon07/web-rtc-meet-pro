package com.webrtcmeetpro.external.anthropic

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.domain.meeting.MeetingNotesAi
import com.webrtcmeetpro.domain.meeting.NotesActionItem
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesRejectedException
import com.webrtcmeetpro.domain.meeting.NotesResult
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.format.DateTimeFormatter

/** Claude API 클라이언트 하나를 회의록·자막이 함께 쓴다. 키가 없으면 null */
@Component
class ClaudeClients(private val props: AiProperties) {
    val client: AnthropicClient? by lazy {
        if (!props.enabled) null
        else AnthropicOkHttpClient.builder()
            .apiKey(props.apiKey)
            // 환경 변수(ANTHROPIC_BASE_URL)가 아니라 설정값만 쓴다
            .baseUrl(props.baseUrl)
            .timeout(Duration.ofMinutes(10))
            .maxRetries(2)
            .build()
    }

    val enabled: Boolean get() = props.enabled

    fun require(): AnthropicClient = client ?: throw IllegalStateException("ANTHROPIC_API_KEY 가 없습니다")

    /** 정책상 거절하면 Anthropic 이 추천하는 모델로 다시 (server-side fallback, beta) */
    fun MessageCreateParams.Builder.withFallbacks(): MessageCreateParams.Builder = apply {
        if (props.fallbacks) {
            putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
    }

    @PreDestroy
    fun close() {
        if (props.enabled) client?.close()
    }

    companion object {
        /** 응답의 글자 부분을 이어 붙인다. 거절·잘림은 예외 (다시 시도해도 같음) */
        fun text(message: Message): String {
            when (message.stopReason().orElse(null)) {
                StopReason.REFUSAL -> throw NotesRejectedException("REFUSED")
                StopReason.MAX_TOKENS -> throw NotesRejectedException("TOO_LONG_OUTPUT")
                StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED -> throw NotesRejectedException("TOO_LONG_INPUT")
                else -> Unit
            }
            return message.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")
        }
    }
}

/**
 * 받아쓴 회의 내용을 Claude 로 회의록(요약·결정 사항·할 일)으로 만든다.
 * 구조화 출력(output_config.format = JSON 스키마)으로 받아서 항상 같은 모양의 JSON 이 온다.
 */
@Component
class ClaudeMeetingNotesAi(
    private val claude: ClaudeClients,
    private val props: AiProperties,
    private val json: JsonMapper,
) : MeetingNotesAi {

    override val enabled: Boolean get() = claude.enabled

    override fun summarize(input: NotesInput): NotesResult {
        val params = with(claude) {
            MessageCreateParams.builder()
                .model(props.model)
                .maxTokens(16000L)
                .system(SYSTEM)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                        .format(JsonOutputFormat.builder().schema(SCHEMA).build())
                        .build(),
                )
                .addUserMessage(render(input))
                .withFallbacks()
                .build()
        }
        val message = claude.require().messages().create(params)
        val out: Output = json.readValue(ClaudeClients.text(message))
        return NotesResult(
            title = out.title?.trim()?.ifBlank { null },
            language = out.language?.trim()?.ifBlank { null },
            summary = out.summary.map(String::trim).filter(String::isNotEmpty),
            decisions = out.decisions.map(String::trim).filter(String::isNotEmpty),
            actionItems = out.actionItems.filter { it.task.isNotBlank() }.map {
                NotesActionItem(it.task.trim(), it.owner?.trim()?.ifBlank { null }, it.due?.trim()?.takeIf { d -> DATE.matches(d) })
            },
            model = message.model().asString(),
        )
    }

    private fun render(input: NotesInput): String = buildString {
        appendLine("<meeting>")
        appendLine("title: ${input.meetingTitle}")
        appendLine("date: ${input.meetingDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (EEE) HH:mm", java.util.Locale.ENGLISH))}")
        appendLine("participants: ${input.participants.joinToString(", ") { it.name }}")
        appendLine("</meeting>")
        appendLine()
        appendLine("<transcript>")
        input.lines.forEach { appendLine("[${it.at.format(TIME)}] ${it.speaker}: ${it.text}") }
        appendLine("</transcript>")
    }

    private data class Item(val task: String = "", val owner: String? = null, val due: String? = null)

    private data class Output(
        val title: String? = null,
        val language: String? = null,
        val summary: List<String> = emptyList(),
        val decisions: List<String> = emptyList(),
        val actionItems: List<Item> = emptyList(),
    )

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

        private val SYSTEM = """
            You write meeting notes for a team collaboration app. You receive the meeting's title, date, participant list,
            and a transcript. Each participant's own browser transcribed their microphone with speech recognition, so speaker
            names are reliable but the text can contain recognition mistakes, missing punctuation, and filler words.
            Read through those mistakes for the intended meaning; don't quote them.

            Write the notes in the language most of the meeting was spoken in, and report that language as a short code
            (ko, en, ja, zh, ...). Keep it faithful to the transcript: no invented facts, numbers, or commitments.

            - title: a short, specific title for what the meeting was actually about (the given title may be generic).
            - summary: 3 to 7 short bullet sentences covering the main topics and outcomes, most important first.
            - decisions: things the group agreed on or concluded. Empty if nothing was decided.
            - actionItems: concrete follow-up tasks someone took on or was asked to do. owner must be exactly one of the
              participant names when the transcript makes the owner clear, otherwise null. due is an ISO date (YYYY-MM-DD)
              resolved against the meeting date when a deadline was stated ("by Friday", "다음 주 월요일까지"), otherwise null.
            If the transcript is too short or empty of substance, say so briefly in the summary and leave the lists empty.
        """.trimIndent()

        private fun nullable(type: String) = mapOf("type" to listOf(type, "null"))

        private val SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "title" to mapOf("type" to "string"),
                        "language" to mapOf("type" to "string"),
                        "summary" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "decisions" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "actionItems" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "task" to mapOf("type" to "string"),
                                    "owner" to nullable("string"),
                                    "due" to nullable("string"),
                                ),
                                "required" to listOf("task", "owner", "due"),
                                "additionalProperties" to false,
                            ),
                        ),
                    ),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("title", "language", "summary", "decisions", "actionItems")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
