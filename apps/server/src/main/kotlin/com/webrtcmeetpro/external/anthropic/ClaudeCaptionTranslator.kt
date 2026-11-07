package com.webrtcmeetpro.external.anthropic

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.domain.meeting.CaptionTranslator
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/**
 * 실시간 자막: 한 문장을 여러 언어로 한 번에 옮긴다. 빨리 끝나도록 effort low, 짧은 출력.
 * 요청한 언어마다 칸이 있는 JSON 스키마(구조화 출력)로 받는다.
 */
@Component
class ClaudeCaptionTranslator(
    private val claude: ClaudeClients,
    private val props: AiProperties,
    private val json: JsonMapper,
) : CaptionTranslator {

    override val enabled: Boolean get() = claude.enabled

    override fun translate(text: String, sourceLang: String, targetLangs: Collection<String>, context: List<String>): Map<String, String> {
        val targets = targetLangs.filter { it.matches(LANG) }.distinct().sorted()
        if (targets.isEmpty()) return emptyMap()
        val params = with(claude) {
            MessageCreateParams.builder()
                .model(props.captionModel)
                .maxTokens(2000L)
                .system(SYSTEM)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(schema(targets)).build())
                        .build(),
                )
                .addUserMessage(
                    buildString {
                        if (context.isNotEmpty()) {
                            appendLine("<previous_lines>")
                            context.forEach { appendLine(it) }
                            appendLine("</previous_lines>")
                        }
                        appendLine("<line lang=\"$sourceLang\">$text</line>")
                        append("Translate the line into: ${targets.joinToString(", ")}")
                    },
                )
                .withFallbacks()
                .build()
        }
        val out: Map<String, String> = json.readValue(ClaudeClients.text(claude.require().messages().create(params)))
        return out.filterKeys { it in targets }.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
    }

    private fun schema(targets: List<String>): JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty("properties", JsonValue.from(targets.associateWith { mapOf("type" to "string") }))
        .putAdditionalProperty("required", JsonValue.from(targets))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()

    companion object {
        private val LANG = Regex("[a-z]{2}")

        private val SYSTEM = """
            You translate live captions in a video meeting. Each line is one sentence a participant just said, produced by
            speech recognition, so it may lack punctuation or contain small recognition errors; translate the intended meaning.
            Return a natural, concise spoken-style translation for each requested language code (ko = Korean, en = English,
            ja = Japanese, zh = Simplified Chinese). Keep names, product names and numbers as they are. The previous lines are
            context only; translate just the line. If the line is already in a target language, return it unchanged for that language.
        """.trimIndent()
    }
}
