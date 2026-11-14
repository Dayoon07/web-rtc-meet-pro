package com.webrtcmeetpro.domain.meeting.dto.res

/** 기다리지 않게 원문을 먼저 보내고(pending), 번역이 끝나면 같은 id 로 다시 보내 화면이 바꿔 끼운다 */
data class CaptionEventResDto(
    val id: Long,
    val meetingId: Long,
    val speakerId: Long,
    val speaker: String,
    val text: String,
    val lang: String,
    val translations: Map<String, String>,
    val pending: Boolean,
) {
    val type: String = "CAPTION"
}
