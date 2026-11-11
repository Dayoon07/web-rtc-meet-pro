package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 웹 푸시(VAPID) 키. 둘 다 있어야 브라우저를 닫아도 알림이 간다 (없으면 화면 안 알림만).
 * 키 만들기: python scripts/vapid-keys.py → VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY (base64url)
 */
@ConfigurationProperties("app.push")
data class PushProperties(
    val vapidPublicKey: String = "",
    val vapidPrivateKey: String = "",
    /** 푸시 서버가 문제가 있을 때 연락할 곳 (mailto: 또는 https://) */
    val subject: String = "mailto:admin@localhost",
) {
    val enabled: Boolean get() = vapidPublicKey.isNotBlank() && vapidPrivateKey.isNotBlank()
}
