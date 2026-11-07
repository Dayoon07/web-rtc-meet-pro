package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Claude API (AI 회의록 요약, 실시간 자막 번역). api-key 가 비어 있으면 AI 기능이 꺼진다 (버튼을 누르면 "준비 중").
 * @param model 회의록 요약 모델
 * @param captionModel 자막 번역 모델 (짧은 문장을 빨리)
 * @param fallbacks 모델이 정책상 거절하면 Anthropic 이 추천하는 다른 모델로 한 번 더 (server-side fallback, beta)
 */
@ConfigurationProperties("app.ai")
data class AiProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://api.anthropic.com",
    val model: String = "claude-opus-5-5",
    val captionModel: String = "claude-opus-5-5",
    val fallbacks: Boolean = true,
) {
    val enabled: Boolean get() = apiKey.isNotBlank()
}
