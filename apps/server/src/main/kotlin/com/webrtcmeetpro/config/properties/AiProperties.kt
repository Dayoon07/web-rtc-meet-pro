package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** AI 를 누가 하는지 */
enum class AiProvider { CLAUDE, OLLAMA, NONE }

/**
 * AI 회의록 요약·실시간 자막 번역.
 * provider: claude(Claude API, 유료) / ollama(내 서버의 Ollama, 무료) / auto(기본: API 키가 있으면 claude, 없고 Ollama 주소가 있으면 ollama, 둘 다 없으면 꺼짐) / none.
 * captionProvider 를 따로 주면 자막만 다른 쪽으로 (예: 회의록은 ollama, 자막은 claude). 비우면 provider 와 같다.
 * @param model 회의록 요약 Claude 모델
 * @param captionModel 자막 번역 Claude 모델
 * @param fallbacks Claude 가 정책상 거절하면 Anthropic 이 추천하는 다른 모델로 한 번 더 (server-side fallback, beta)
 */
@ConfigurationProperties("app.ai")
data class AiProperties(
    val provider: String = "auto",
    val captionProvider: String = "",
    val apiKey: String = "",
    val baseUrl: String = "https://api.anthropic.com",
    val model: String = "claude-opus-5-5",
    val captionModel: String = "claude-opus-5-5",
    val fallbacks: Boolean = true,
    val ollama: Ollama = Ollama(),
    /**
     * 실시간 자막·번역 (Business). 지금은 꺼 둔다 (기본 false): 실시간 번역은 CPU 의 Ollama 로는 너무 느리고 Claude API 는 비용이 커서.
     * 코드는 그대로 두었으니 LIVE_CAPTIONS_ENABLED=true + 번역 AI(Claude 키 등)를 주면 다시 켜진다.
     * 꺼져 있으면 자막 번역기가 꺼지고(회의실 CC 버튼이 숨는다) 요금제에도 자막 시간이 0 으로 보인다
     */
    val liveCaptions: Boolean = false,
) {
    /**
     * @param url Ollama 주소 (예: http://localhost:11434, 도커 안에서는 http://ollama:11434). 비우면 안 씀
     * @param model 회의록 요약 모델 (qwen2.5:7b: 한국어·일본어·중국어에 강함, 약 4.7GB)
     * @param captionModel 자막 번역 모델. 비우면 model 과 같다 (더 빠르게 하려면 qwen2.5:3b)
     * @param maxContext 한 번에 넣는 최대 토큰. 받아쓴 내용이 더 길면 나눠서 요약한 뒤 합친다 (클수록 메모리를 더 쓴다)
     * @param keepAlive 마지막으로 쓴 뒤 모델을 메모리에 남겨 둘 시간 (지나면 내려서 메모리를 돌려준다)
     */
    data class Ollama(
        val url: String = "",
        val model: String = "qwen2.5:7b",
        val captionModel: String = "",
        val maxContext: Int = 8192,
        val keepAlive: String = "10m",
    )

    val notesProvider: AiProvider get() = resolve(provider)
    val captionsProvider: AiProvider get() = if (!liveCaptions) AiProvider.NONE else resolve(captionProvider.ifBlank { provider })

    /** AI 회의록을 쓸 수 있는지 (회의실 버튼) */
    val enabled: Boolean get() = notesProvider != AiProvider.NONE

    private fun resolve(value: String): AiProvider = when (value.trim().lowercase()) {
        "claude" -> if (apiKey.isNotBlank()) AiProvider.CLAUDE else AiProvider.NONE
        "ollama" -> if (ollama.url.isNotBlank()) AiProvider.OLLAMA else AiProvider.NONE
        "none", "off" -> AiProvider.NONE
        else -> when {
            apiKey.isNotBlank() -> AiProvider.CLAUDE
            ollama.url.isNotBlank() -> AiProvider.OLLAMA
            else -> AiProvider.NONE
        }
    }
}
