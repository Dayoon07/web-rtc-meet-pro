package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

enum class AiProvider { CLAUDE, OLLAMA, NONE }

/**
 * provider: claude(유료) / ollama(무료, 내 서버) / auto(API 키가 있으면 claude, Ollama 주소가 있으면 ollama, 둘 다 없으면 꺼짐) / none.
 * captionProvider 를 비우면 provider 와 같다.
 * @param fallbacks Claude 가 정책상 거절하면 다른 모델로 한 번 더 시도한다
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
     * CPU 의 Ollama 로는 실시간 번역이 너무 느리고 Claude API 는 비용이 커서 꺼 둔다.
     * LIVE_CAPTIONS_ENABLED=true 와 번역 AI 를 주면 다시 켜진다.
     */
    val liveCaptions: Boolean = false,
) {
    /**
     * @param url 비우면 쓰지 않는다. 도커 안에서는 http://ollama:11434
     * @param model qwen2.5:7b 는 한국어·일본어·중국어에 강하다 (약 4.7GB)
     * @param maxContext 받아쓴 내용이 더 길면 나눠 요약한 뒤 합친다. 클수록 메모리를 더 쓴다
     * @param keepAlive 지나면 모델을 내려 메모리를 돌려준다
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
