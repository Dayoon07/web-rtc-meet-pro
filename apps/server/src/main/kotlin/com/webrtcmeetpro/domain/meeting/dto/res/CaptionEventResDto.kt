package com.webrtcmeetpro.domain.meeting.dto.res

/**
 * /user/queue/meeting: 자막 한 줄. 먼저 원문으로 보내고(translations 비어 있음, pending = true),
 * 번역이 끝나면 같은 id 로 translations 를 채워 다시 보낸다 (화면이 같은 줄을 바꿔 끼운다).
 */
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
