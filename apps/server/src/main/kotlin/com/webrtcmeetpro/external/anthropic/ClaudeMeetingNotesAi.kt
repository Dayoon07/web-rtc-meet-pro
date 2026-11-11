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
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesRejectedException
import com.webrtcmeetpro.domain.meeting.NotesResult
import com.webrtcmeetpro.external.ai.NotesPrompt
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** Claude API 클라이언트 하나를 회의록·자막이 함께 쓴다. API 키가 없으면 null */
@Component
class ClaudeClients(private val props: AiProperties) {
    val enabled: Boolean get() = props.apiKey.isNotBlank()

    val client: AnthropicClient? by lazy {
        if (!enabled) null
        else AnthropicOkHttpClient.builder()
            .apiKey(props.apiKey)
            // 환경 변수(ANTHROPIC_BASE_URL)가 아니라 설정값만 쓴다
            .baseUrl(props.baseUrl)
            .timeout(Duration.ofMinutes(10))
            .maxRetries(2)
            .build()
    }

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
        if (enabled) client?.close()
    }

    companion object {
        fun schema(map: Map<String, Any>): JsonOutputFormat.Schema =
            JsonOutputFormat.Schema.builder().apply { map.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build()

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
) {
    fun summarize(input: NotesInput): NotesResult {
        val params = with(claude) {
            MessageCreateParams.builder()
                .model(props.model)
                .maxTokens(16000L)
                .system(NotesPrompt.SYSTEM)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                        .format(JsonOutputFormat.builder().schema(ClaudeClients.schema(NotesPrompt.SCHEMA)).build())
                        .build(),
                )
                .addUserMessage(NotesPrompt.render(input))
                .withFallbacks()
                .build()
        }
        val message = claude.require().messages().create(params)
        return NotesPrompt.parse(json, ClaudeClients.text(message), message.model().asString(), input.meetingDate.toLocalDate())
    }
}
