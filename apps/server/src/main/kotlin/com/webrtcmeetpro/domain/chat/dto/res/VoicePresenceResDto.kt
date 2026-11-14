package com.webrtcmeetpro.domain.chat.dto.res

/**
 * /user/queue/chat 으로 가는 이벤트. ROOM = 방 목록 갱신 필요(새 방, 초대, 퇴장), MESSAGE = 새 메시지,
 * MESSAGE_UPDATED = 고치거나 지운 메시지 (같은 id 의 메시지를 바꿔 끼운다)
 */
/** 채널 음성방: 진행 중인 회의와 들어와 있는 사람 */
data class VoicePresenceResDto(val meetingId: Long, val participants: List<ChatUserResDto>)
