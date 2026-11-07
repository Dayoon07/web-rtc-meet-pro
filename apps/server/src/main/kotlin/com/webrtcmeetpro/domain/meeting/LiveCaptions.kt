package com.webrtcmeetpro.domain.meeting

/**
 * 실시간 자막 번역 (external/anthropic 에 구현). 말한 한 문장을 지금 회의에 있는 사람들의 언어로 옮긴다.
 * @param context 바로 앞 문장 몇 개 (대명사·생략을 제대로 옮기려고)
 * @return 언어 코드(ko, en, ja, zh ...) → 번역문. 실패하면 비어 있다
 */
interface CaptionTranslator {
    val enabled: Boolean

    fun translate(text: String, sourceLang: String, targetLangs: Collection<String>, context: List<String>): Map<String, String>
}
