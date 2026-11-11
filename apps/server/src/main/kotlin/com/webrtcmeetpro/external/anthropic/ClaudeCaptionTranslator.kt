package com.webrtcmeetpro.external.anthropic

import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.external.ai.CaptionPrompt
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
) {
    fun translate(text: String, sourceLang: String, targetLangs: Collection<String>, context: List<String>): Map<String, String> {
        val targets = CaptionPrompt.targets(targetLangs)
        if (targets.isEmpty()) return emptyMap()
        val params = with(claude) {
            MessageCreateParams.builder()
                .model(props.captionModel)
                .maxTokens(2000L)
                .system(CaptionPrompt.SYSTEM)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(ClaudeClients.schema(CaptionPrompt.schema(targets))).build())
                        .build(),
                )
                .addUserMessage(CaptionPrompt.render(text, sourceLang, targets, context))
                .withFallbacks()
                .build()
        }
        val out: Map<String, String> = json.readValue(ClaudeClients.text(claude.require().messages().create(params)))
        return CaptionPrompt.clean(out, targets)
    }
}
