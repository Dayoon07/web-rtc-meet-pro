package com.webrtcmeetpro.domain.push.dto.res

/** 화면이 구독할 때 쓰는 값. enabled=false 면 서버에 VAPID 키가 없어 푸시를 못 보낸다 */
data class PushConfigResDto(val enabled: Boolean, val publicKey: String?)
