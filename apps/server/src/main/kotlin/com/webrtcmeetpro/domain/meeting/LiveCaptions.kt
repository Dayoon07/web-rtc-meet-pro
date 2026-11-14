package com.webrtcmeetpro.domain.meeting

/**
 * @param context 대명사·생략을 제대로 옮기려고 바로 앞 문장 몇 개를 같이 준다
 * @return 언어 코드 → 번역문. 실패하면 비어 있다
 */
interface CaptionTranslator {
    val enabled: Boolean

    fun translate(text: String, sourceLang: String, targetLangs: Collection<String>, context: List<String>): Map<String, String>
}
