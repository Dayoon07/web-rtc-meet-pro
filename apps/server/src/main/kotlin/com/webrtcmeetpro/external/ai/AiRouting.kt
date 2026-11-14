package com.webrtcmeetpro.external.ai

import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.config.properties.AiProvider
import com.webrtcmeetpro.domain.meeting.CaptionTranslator
import com.webrtcmeetpro.domain.meeting.MeetingNotesAi
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesRejectedException
import com.webrtcmeetpro.domain.meeting.NotesResult
import com.webrtcmeetpro.external.anthropic.ClaudeCaptionTranslator
import com.webrtcmeetpro.external.anthropic.ClaudeMeetingNotesAi
import com.webrtcmeetpro.external.ollama.OllamaCaptionTranslator
import com.webrtcmeetpro.external.ollama.OllamaMeetingNotesAi
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RoutingMeetingNotesAi(
    private val props: AiProperties,
    private val claude: ClaudeMeetingNotesAi,
    private val ollama: OllamaMeetingNotesAi,
) : MeetingNotesAi {
    override val enabled: Boolean get() = props.notesProvider != AiProvider.NONE

    @PostConstruct
    fun announce() {
        LoggerFactory.getLogger(javaClass).info(
            "AI: 회의록 {} · 자막 {}{}",
            props.notesProvider, props.captionsProvider,
            if (AiProvider.OLLAMA in listOf(props.notesProvider, props.captionsProvider)) " (Ollama ${props.ollama.url}, ${props.ollama.model})" else "",
        )
    }

    override fun summarize(input: NotesInput): NotesResult = when (props.notesProvider) {
        AiProvider.CLAUDE -> claude.summarize(input)
        AiProvider.OLLAMA -> ollama.summarize(input)
        AiProvider.NONE -> throw NotesRejectedException("AI_NOT_CONFIGURED")
    }
}

@Component
class RoutingCaptionTranslator(
    private val props: AiProperties,
    private val claude: ClaudeCaptionTranslator,
    private val ollama: OllamaCaptionTranslator,
) : CaptionTranslator {
    override val enabled: Boolean get() = props.captionsProvider != AiProvider.NONE

    override fun translate(text: String, sourceLang: String, targetLangs: Collection<String>, context: List<String>): Map<String, String> =
        when (props.captionsProvider) {
            AiProvider.CLAUDE -> claude.translate(text, sourceLang, targetLangs, context)
            AiProvider.OLLAMA -> ollama.translate(text, sourceLang, targetLangs, context)
            AiProvider.NONE -> emptyMap()
        }
}
